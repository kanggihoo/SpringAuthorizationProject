# 01. Domain Methods And Login Failure Counter

## Task 1: Add User Lock Domain Methods

**Files:**

- Modify: `src/main/java/org/example/domain/entity/User.java`
- Test through the service tests in `02-auth-service-lock-flow.md`.

- [ ] **Step 1: Add domain methods**

Add these methods near `addRole(...)`:

```java
  public void lock() {
    this.accountNonLocked = false;
  }

  public void unlock() {
    this.accountNonLocked = true;
  }
```

- [ ] **Step 2: Run compile check**

Run:

```bash
./gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/example/domain/entity/User.java
git commit -m "feat: add user account lock methods"
```

## Task 2: Add Redis Repository For Login Failures

**Files:**

- Create: `src/main/java/org/example/repository/LoginFailureRedisRepository.java`
- Create: `src/test/java/org/example/repository/LoginFailureRedisRepositoryTest.java`

- [ ] **Step 1: Write failing Redis repository test**

Create `src/test/java/org/example/repository/LoginFailureRedisRepositoryTest.java`:

```java
package org.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class LoginFailureRedisRepositoryTest {

  @Container
  static RedisContainer redisContainer = new RedisContainer(
      RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG));

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redisContainer::getHost);
    registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
  }

  @Autowired
  private LoginFailureRedisRepository repository;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @Test
  @DisplayName("incrementFailure increments username failure count and sets TTL")
  void incrementFailure_incrementsCountAndSetsTtl() {
    long count = repository.incrementFailure("testuser", Duration.ofMinutes(30));

    assertThat(count).isOne();
    assertThat(redisTemplate.opsForValue().get("auth:login:fail:user:testuser"))
        .isEqualTo("1");
    assertThat(redisTemplate.getExpire("auth:login:fail:user:testuser"))
        .isPositive();
  }

  @Test
  @DisplayName("incrementFailure keeps increasing the same username counter")
  void incrementFailure_incrementsExistingCounter() {
    repository.incrementFailure("testuser", Duration.ofMinutes(30));

    long count = repository.incrementFailure("testuser", Duration.ofMinutes(30));

    assertThat(count).isEqualTo(2L);
  }

  @Test
  @DisplayName("clearFailure deletes username failure counter")
  void clearFailure_deletesCounter() {
    repository.incrementFailure("testuser", Duration.ofMinutes(30));

    repository.clearFailure("testuser");

    assertThat(redisTemplate.hasKey("auth:login:fail:user:testuser")).isFalse();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew.bat test --tests org.example.repository.LoginFailureRedisRepositoryTest
```

Expected: FAIL because `LoginFailureRedisRepository` does not exist.

- [ ] **Step 3: Implement Redis repository**

Create `src/main/java/org/example/repository/LoginFailureRedisRepository.java`:

```java
package org.example.repository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LoginFailureRedisRepository {

  private static final String ACCOUNT_FAILURE_PREFIX = "auth:login:fail:user:";

  private final StringRedisTemplate redisTemplate;

  public long incrementFailure(String username, Duration ttl) {
    String key = accountFailureKey(username);
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, ttl.toSeconds(), TimeUnit.SECONDS);
    }
    return count == null ? 0L : count;
  }

  public void clearFailure(String username) {
    redisTemplate.delete(accountFailureKey(username));
  }

  private String accountFailureKey(String username) {
    return ACCOUNT_FAILURE_PREFIX + username;
  }
}
```

- [ ] **Step 4: Run repository tests**

Run:

```bash
./gradlew.bat test --tests org.example.repository.LoginFailureRedisRepositoryTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/repository/LoginFailureRedisRepository.java src/test/java/org/example/repository/LoginFailureRedisRepositoryTest.java
git commit -m "feat: add login failure redis repository"
```

## Task 3: Add LoginFailureCounter Policy

**Files:**

- Create: `src/main/java/org/example/security/account/LoginFailureCounter.java`
- Create: `src/test/java/org/example/security/account/LoginFailureCounterTest.java`

- [ ] **Step 1: Write failing policy tests**

Create `src/test/java/org/example/security/account/LoginFailureCounterTest.java`:

```java
package org.example.security.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.example.repository.LoginFailureRedisRepository;
import org.example.security.token.RedisFailurePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginFailureCounterTest {

  @Mock
  private LoginFailureRedisRepository repository;

  private LoginFailureCounter counter;

  @BeforeEach
  void setUp() {
    counter = new LoginFailureCounter(repository, new RedisFailurePolicy());
  }

  @Test
  @DisplayName("recordFailure returns false before threshold")
  void recordFailure_returnsFalse_beforeThreshold() {
    given(repository.incrementFailure("testuser", Duration.ofMinutes(30)))
        .willReturn(4L);

    boolean lockRequired = counter.recordFailure("testuser");

    assertThat(lockRequired).isFalse();
  }

  @Test
  @DisplayName("recordFailure returns true at threshold")
  void recordFailure_returnsTrue_atThreshold() {
    given(repository.incrementFailure("testuser", Duration.ofMinutes(30)))
        .willReturn(5L);

    boolean lockRequired = counter.recordFailure("testuser");

    assertThat(lockRequired).isTrue();
  }

  @Test
  @DisplayName("clear delegates to repository")
  void clear_delegatesToRepository() {
    counter.clear("testuser");

    verify(repository).clearFailure("testuser");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew.bat test --tests org.example.security.account.LoginFailureCounterTest
```

Expected: FAIL because `LoginFailureCounter` does not exist.

- [ ] **Step 3: Implement counter policy**

Create `src/main/java/org/example/security/account/LoginFailureCounter.java`:

```java
package org.example.security.account;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.example.repository.LoginFailureRedisRepository;
import org.example.security.token.RedisFailurePolicy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginFailureCounter {

  private static final int LOCK_THRESHOLD = 5;
  private static final Duration LOCK_WINDOW = Duration.ofMinutes(30);

  private final LoginFailureRedisRepository repository;
  private final RedisFailurePolicy redisFailurePolicy;

  public boolean recordFailure(String username) {
    return redisFailurePolicy.requireAvailable(
        () -> repository.incrementFailure(username, LOCK_WINDOW)) >= LOCK_THRESHOLD;
  }

  public void clear(String username) {
    redisFailurePolicy.requireAvailable(() -> repository.clearFailure(username));
  }
}
```

- [ ] **Step 4: Run counter tests**

Run:

```bash
./gradlew.bat test --tests org.example.security.account.LoginFailureCounterTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run all Task 1 tests**

Run:

```bash
./gradlew.bat test --tests org.example.repository.LoginFailureRedisRepositoryTest --tests org.example.security.account.LoginFailureCounterTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/example/security/account/LoginFailureCounter.java src/test/java/org/example/security/account/LoginFailureCounterTest.java
git commit -m "feat: add login failure counter policy"
```
