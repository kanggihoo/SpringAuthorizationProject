# 04. Admin Controller Security Evidence

## Task 1: Add AdminControllerSecurityTest

**Files:**

- Create: `src/test/java/org/example/controller/AdminControllerSecurityTest.java`
- May modify: `src/test/java/org/example/controller/TestSecurityConfig.java`

- [ ] **Step 1: Write failing controller security test**

Create `src/test/java/org/example/controller/AdminControllerSecurityTest.java`:

```java
package org.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.example.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AdminController.class)
@Import(TestSecurityConfig.class)
class AdminControllerSecurityTest {

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private AdminService adminService;

  @Test
  @DisplayName("ROLE_USER cannot unlock account")
  void userCannotUnlockAccount() {
    assertThat(mvc.post().uri("/admin/users/testuser/unlock")
        .with(user("testuser").roles("USER")))
        .hasStatus(HttpStatus.FORBIDDEN);

    verify(adminService, never()).unlockUser("testuser");
  }

  @Test
  @DisplayName("ROLE_ADMIN can call unlock endpoint")
  void adminCanUnlockAccount() {
    assertThat(mvc.post().uri("/admin/users/testuser/unlock")
        .with(user("admin").roles("ADMIN")))
        .hasStatusOk();

    verify(adminService).unlockUser("testuser");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew.bat test --tests org.example.controller.AdminControllerSecurityTest
```

Expected: FAIL if `TestSecurityConfig` does not restrict `/admin/**` to ADMIN.

- [ ] **Step 3: Update TestSecurityConfig for admin authorization**

Modify `src/test/java/org/example/controller/TestSecurityConfig.java` authorization block:

```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/refresh", "/signup").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated());
```

This mirrors production `SecurityConfig` enough for admin endpoint slice evidence.

- [ ] **Step 4: Run controller security tests**

Run:

```bash
./gradlew.bat test --tests org.example.controller.AdminControllerSecurityTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run existing controller tests**

Run:

```bash
./gradlew.bat test --tests org.example.controller.AuthControllerTest --tests org.example.controller.AdminControllerSecurityTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/org/example/controller/AdminControllerSecurityTest.java src/test/java/org/example/controller/TestSecurityConfig.java
git commit -m "test: prove user cannot unlock accounts"
```
