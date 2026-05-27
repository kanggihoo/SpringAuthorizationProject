package org.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.example.security.authenticated.AuthenticatedUserService;
import org.example.security.jwt.JwtTokenProvider;
import org.example.security.token.TokenLifecycleService;
import org.example.security.token.delivery.TokenDeliveryServiceImpl;
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
@Import({TestSecurityConfig.class, TokenDeliveryServiceImpl.class})
class AdminControllerSecurityTest {

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private AdminService adminService;

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private AuthenticatedUserService authenticatedUserService;

  @MockitoBean
  private TokenLifecycleService tokenLifecycleService;

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
