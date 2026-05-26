# 03. Admin Recovery Service And Endpoint

## Task 1: Add AdminService Unlock Evidence

**Files:**

- Create: `src/main/java/org/example/service/AdminService.java`
- Create: `src/main/java/org/example/service/AdminServiceImpl.java`
- Create: `src/test/java/org/example/service/AdminServiceImplTest.java`

- [ ] **Step 1: Write failing service test**

Create `src/test/java/org/example/service/AdminServiceImplTest.java`:

```java
package org.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.account.LoginFailureCounter;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private LoginFailureCounter loginFailureCounter;

  @InjectMocks
  private AdminServiceImpl adminService;

  @Test
  @DisplayName("unlockUser unlocks locked User and clears failure counter")
  void unlockUser_unlocksLockedUser() {
    User user = lockedUser("testuser");
    given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));

    adminService.unlockUser("testuser");

    assertThat(user.isAccountNonLocked()).isTrue();
    verify(loginFailureCounter).clear("testuser");
  }

  @Test
  @DisplayName("unlockUser throws USER_NOT_FOUND when User is missing")
  void unlockUser_throwsUserNotFound_whenUserIsMissing() {
    given(userRepository.findByUsername("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminService.unlockUser("missing"))
        .isInstanceOf(AuthFailureException.class)
        .satisfies(exception -> assertThat(((AuthFailureException) exception).getCode())
            .isEqualTo(AuthFailureCode.USER_NOT_FOUND));
  }

  private User lockedUser(String username) {
    User user = User.builder()
        .username(username)
        .password("encoded")
        .nickname("tester")
        .build();
    user.lock();
    return user;
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew.bat test --tests org.example.service.AdminServiceImplTest
```

Expected: FAIL because `AdminServiceImpl` does not exist.

- [ ] **Step 3: Add AdminService interface**

Create `src/main/java/org/example/service/AdminService.java`:

```java
package org.example.service;

public interface AdminService {

  void unlockUser(String username);
}
```

- [ ] **Step 4: Add AdminServiceImpl**

Create `src/main/java/org/example/service/AdminServiceImpl.java`:

```java
package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.account.LoginFailureCounter;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

  private final UserRepository userRepository;
  private final LoginFailureCounter loginFailureCounter;

  @Override
  @Transactional
  public void unlockUser(String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new AuthFailureException(
            AuthFailureCode.USER_NOT_FOUND,
            "User를 찾을 수 없습니다."));

    user.unlock();
    loginFailureCounter.clear(username);
  }
}
```

- [ ] **Step 5: Run AdminService tests**

Run:

```bash
./gradlew.bat test --tests org.example.service.AdminServiceImplTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/example/service/AdminService.java src/main/java/org/example/service/AdminServiceImpl.java src/test/java/org/example/service/AdminServiceImplTest.java
git commit -m "feat: add admin account unlock service"
```

## Task 2: Add AdminController

**Files:**

- Create: `src/main/java/org/example/controller/AdminController.java`

- [ ] **Step 1: Create controller**

Create `src/main/java/org/example/controller/AdminController.java`:

```java
package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AdminService adminService;

  @PostMapping("/users/{username}/unlock")
  public ResponseEntity<String> unlockUser(@PathVariable String username) {
    adminService.unlockUser(username);
    return ResponseEntity.ok("계정 잠금이 해제되었습니다.");
  }
}
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/example/controller/AdminController.java
git commit -m "feat: add admin unlock endpoint"
```
