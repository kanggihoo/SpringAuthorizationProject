package org.example.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
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
  @DisplayName("incrementFailure does not refresh TTL for existing username counter")
  void incrementFailure_doesNotRefreshTtlForExistingCounter() throws InterruptedException {
    repository.incrementFailure("testuser", Duration.ofSeconds(5));
    Long firstTtl = redisTemplate.getExpire("auth:login:fail:user:testuser");
    assertThat(firstTtl).isPositive();

    Thread.sleep(2100L);
    repository.incrementFailure("testuser", Duration.ofSeconds(5));

    Long secondTtl = redisTemplate.getExpire("auth:login:fail:user:testuser");
    assertThat(secondTtl).isPositive().isLessThan(firstTtl);
  }

  @Test
  @DisplayName("incrementFailure treats null Redis script result as unavailable")
  void incrementFailure_throwsDataAccessException_whenRedisReturnsNull() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    when(redisTemplate.execute(
        ArgumentMatchers.<RedisScript<Long>>any(),
        ArgumentMatchers.<List<String>>any(),
        eq("1800")))
        .thenReturn(null);
    LoginFailureRedisRepository repository = new LoginFailureRedisRepository(redisTemplate);

    assertThatThrownBy(() -> repository.incrementFailure("testuser", Duration.ofMinutes(30)))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("clearFailure deletes username failure counter")
  void clearFailure_deletesCounter() {
    repository.incrementFailure("testuser", Duration.ofMinutes(30));

    repository.clearFailure("testuser");

    assertThat(redisTemplate.hasKey("auth:login:fail:user:testuser")).isFalse();
  }
}
