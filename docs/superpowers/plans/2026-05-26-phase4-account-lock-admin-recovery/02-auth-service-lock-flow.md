# 02. AuthService Account Lock Flow

## Task 1: Add Account Lock Tests To AuthServiceImplTest

**Files:**

- Modify: `src/test/java/org/example/service/AuthServiceImplTest.java`
- Modify: `src/main/java/org/example/service/AuthServiceImpl.java`

- [ ] **Step 1: Update test fields**

Add these imports:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;

import java.util.Optional;
import org.example.repository.UserRepository;
import org.example.security.account.LoginFailureCounter;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.springframework.security.authentication.BadCredentialsException;
```

Add mocks:

```java
  @Mock
  private UserRepository userRepository;

  @Mock
  private LoginFailureCounter loginFailureCounter;
```

- [ ] **Step 2: Add failing test for 5 failures**

Add:

```java
  @Test
  @DisplayName("login locks User after five failed attempts within the counter window")
  void login_locksUser_afterFiveFailures() {
    User user = user("testuser", true);
    given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
    given(authenticationManager.authenticate(any()))
        .willThrow(new BadCredentialsException("Bad credentials"));
    given(loginFailureCounter.recordFailure("testuser")).willReturn(true);

    assertThatThrownBy(() -> authServiceImpl.login(createLoginRequest("testuser", "wrong-password")))
        .isInstanceOf(AuthFailureException.class)
        .satisfies(exception -> assertThat(((AuthFailureException) exception).getCode())
            .isEqualTo(AuthFailureCode.ACCOUNT_LOCKED));

    assertThat(user.isAccountNonLocked()).isFalse();
    verify(tokenLifecycleService, never()).issue(any(), any());
  }
```

- [ ] **Step 3: Add failing test for locked login**

Add:

```java
  @Test
  @DisplayName("login rejects locked User before token issuing")
  void login_throwsLockedException_whenUserLocked() {
    User user = user("testuser", false);
    given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));

    assertThatThrownBy(() -> authServiceImpl.login(createLoginRequest("testuser", "password123")))
        .isInstanceOf(AuthFailureException.class)
        .satisfies(exception -> assertThat(((AuthFailureException) exception).getCode())
            .isEqualTo(AuthFailureCode.ACCOUNT_LOCKED));

    verify(authenticationManager, never()).authenticate(any());
    verify(tokenLifecycleService, never()).issue(any(), any());
  }
```

- [ ] **Step 4: Add failing test for clearing counter on success**

Add:

```java
  @Test
  @DisplayName("login clears failure counter when authentication succeeds")
  void login_clearsFailureCounter_whenAuthenticationSucceeds() {
    given(userRepository.findByUsername("testuser"))
        .willReturn(Optional.of(user("testuser", true)));
    given(authenticationManager.authenticate(any())).willReturn(authentication());
    given(tokenLifecycleService.issue(eq("testuser"), any()))
        .willReturn(tokenResponse("access-token", "refresh-token"));

    authServiceImpl.login(createLoginRequest("testuser", "password123"));

    verify(loginFailureCounter).clear("testuser");
  }
```

- [ ] **Step 5: Add failing test for unknown username**

Add:

```java
  @Test
  @DisplayName("login returns credential failure without counter when User is unknown")
  void login_throwsBadCredentials_whenUserIsUnknown() {
    given(userRepository.findByUsername("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authServiceImpl.login(createLoginRequest("missing", "password123")))
        .isInstanceOf(AuthFailureException.class)
        .satisfies(exception -> assertThat(((AuthFailureException) exception).getCode())
            .isEqualTo(AuthFailureCode.BAD_CREDENTIALS));

    verify(loginFailureCounter, never()).recordFailure(any());
    verify(authenticationManager, never()).authenticate(any());
  }
```

- [ ] **Step 6: Add helper method**

Add this helper below `authentication()` or replace local user creation helpers with it:

```java
  private User user(String username, boolean accountNonLocked) {
    User user = User.builder()
        .username(username)
        .password("encoded")
        .nickname("tester")
        .build();
    user.addRole(new Role("ROLE_USER"));
    if (!accountNonLocked) {
      user.lock();
    }
    return user;
  }
```

- [ ] **Step 7: Run tests to verify failure**

Run:

```bash
./gradlew.bat test --tests org.example.service.AuthServiceImplTest
```

Expected: FAIL because `AuthServiceImpl` does not yet inject `UserRepository` or `LoginFailureCounter`.

## Task 2: Implement Account Lock In AuthServiceImpl

**Files:**

- Modify: `src/main/java/org/example/service/AuthServiceImpl.java`

- [ ] **Step 1: Add dependencies and imports**

Add imports:

```java
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.account.LoginFailureCounter;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.springframework.security.authentication.BadCredentialsException;
```

Add fields:

```java
  private final UserRepository userRepository;
  private final LoginFailureCounter loginFailureCounter;
```

- [ ] **Step 2: Replace login method**

Replace `login(...)` with:

```java
  @Override
  public TokenResponseDto login(LoginRequestDto requestDto) {
    String username = requestDto.getUsername();
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new AuthFailureException(
            AuthFailureCode.BAD_CREDENTIALS,
            "아이디 또는 비밀번호가 올바르지 않습니다."));

    if (!user.isAccountNonLocked()) {
      throw new AuthFailureException(
          AuthFailureCode.ACCOUNT_LOCKED,
          "계정이 잠겼습니다.");
    }

    Authentication authentication;
    try {
      authentication = authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(
              username,
              requestDto.getPassword()));
    } catch (BadCredentialsException e) {
      if (loginFailureCounter.recordFailure(username)) {
        user.lock();
        throw new AuthFailureException(
            AuthFailureCode.ACCOUNT_LOCKED,
            "계정이 잠겼습니다.",
            e);
      }
      throw new AuthFailureException(
          AuthFailureCode.BAD_CREDENTIALS,
          "아이디 또는 비밀번호가 올바르지 않습니다.",
          e);
    }

    loginFailureCounter.clear(username);

    AuthenticatedUser userDetails = (AuthenticatedUser) authentication.getPrincipal();
    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    return tokenLifecycleService.issue(userDetails.getJwtSubject(), roles);
  }
```

- [ ] **Step 3: Run AuthServiceImpl tests**

Run:

```bash
./gradlew.bat test --tests org.example.service.AuthServiceImplTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run controller login failure tests**

Run:

```bash
./gradlew.bat test --tests org.example.controller.AuthControllerTest
```

Expected: `BUILD SUCCESSFUL`. `AuthControllerTest` may continue stubbing Spring Security exceptions because `GlobalExceptionHandler` maps both Spring `AuthenticationException` and `AuthFailureException`.

- [ ] **Step 5: Run Phase 4 login target tests**

Run:

```bash
./gradlew.bat test --tests org.example.service.AuthServiceImplTest --tests org.example.controller.AuthControllerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/example/service/AuthServiceImpl.java src/test/java/org/example/service/AuthServiceImplTest.java src/test/java/org/example/controller/AuthControllerTest.java
git commit -m "feat: lock users after repeated login failures"
```
