package org.example.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.example.config.SecurityConfig;
import org.example.security.authenticated.AuthenticatedUserService;
import org.example.security.exception.CustomAccessDeniedHandler;
import org.example.security.exception.CustomAuthenticationEntryPoint;
import org.example.security.jwt.ExceptionHandlerFilter;
import org.example.security.jwt.JwtAuthenticationFilter;
import org.example.security.jwt.JwtTokenProvider;
import org.example.security.oauth2.CookieOAuth2AuthorizationRequestRepository;
import org.example.security.oauth2.CustomOAuth2UserService;
import org.example.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.example.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.example.security.token.TokenLifecycleService;
import org.example.security.token.delivery.TokenDeliveryServiceImpl;
import org.example.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = AdminControllerSecurityIntegrationTest.TestApplication.class,
    properties = "spring.security.oauth2.client.registration.google.client-id=test-client")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerSecurityIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminService adminService;

  @Test
  @DisplayName("production SecurityConfig returns 403 when USER unlocks account")
  void userCannotUnlockAccount_withProductionSecurityConfig() throws Exception {
    mockMvc.perform(post("/admin/users/testuser/unlock")
            .with(user("testuser").roles("USER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Forbidden"));

    verifyNoInteractions(adminService);
  }

  @Test
  @DisplayName("production SecurityConfig allows ADMIN to unlock account")
  void adminCanUnlockAccount_withProductionSecurityConfig() throws Exception {
    mockMvc.perform(post("/admin/users/testuser/unlock")
            .with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk());

    verify(adminService).unlockUser("testuser");
  }

  @Test
  @DisplayName("production SecurityConfig returns 401 when anonymous user unlocks account")
  void anonymousCannotUnlockAccount_withProductionSecurityConfig() throws Exception {
    mockMvc.perform(post("/admin/users/testuser/unlock"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Unauthorized"));

    verifyNoInteractions(adminService);
  }

  @Configuration
  @EnableAutoConfiguration(exclude = {
      DataJpaRepositoriesAutoConfiguration.class,
      DataRedisAutoConfiguration.class,
      DataSourceAutoConfiguration.class,
      DataSourceTransactionManagerAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class
  })
  @Import({
      SecurityConfig.class,
      AdminController.class,
      JwtAuthenticationFilter.class,
      ExceptionHandlerFilter.class,
      TokenDeliveryServiceImpl.class,
      CustomAuthenticationEntryPoint.class,
      CustomAccessDeniedHandler.class
  })
  static class TestApplication {

    private static final String TEST_JWT_SECRET =
        "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

    @Bean
    JwtTokenProvider jwtTokenProvider() {
      return new JwtTokenProvider(TEST_JWT_SECRET, 3_600_000L, 604_800_000L);
    }

    @Bean
    AuthenticatedUserService authenticatedUserService() {
      return jwtSubject -> Optional.empty();
    }

    @Bean
    TokenLifecycleService tokenLifecycleService() {
      return new TokenLifecycleService() {
        @Override
        public org.example.dto.response.TokenResponseDto issue(String jwtSubject,
            List<String> roles) {
          throw new UnsupportedOperationException("Not used in admin security tests");
        }

        @Override
        public org.example.dto.response.TokenResponseDto rotate(String refreshToken) {
          throw new UnsupportedOperationException("Not used in admin security tests");
        }

        @Override
        public void logout(String jwtSubject, String accessToken) {
          throw new UnsupportedOperationException("Not used in admin security tests");
        }

        @Override
        public boolean isAccessTokenAllowed(String accessToken) {
          return true;
        }

        @Override
        public long getRefreshTokenTtlSeconds() {
          return 604_800L;
        }
      };
    }

    @Bean
    CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository() {
      return new CookieOAuth2AuthorizationRequestRepository();
    }

    @Bean
    OAuth2AuthenticationSuccessHandler oauthSuccessHandler(
        TokenLifecycleService tokenLifecycleService,
        TokenDeliveryServiceImpl tokenDeliveryService,
        CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository) {
      return new OAuth2AuthenticationSuccessHandler(
          tokenLifecycleService,
          tokenDeliveryService,
          cookieAuthorizationRequestRepository);
    }

    @Bean
    OAuth2AuthenticationFailureHandler oauthFailureHandler(
        CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository) {
      return new OAuth2AuthenticationFailureHandler(cookieAuthorizationRequestRepository);
    }

    @Bean
    CustomOAuth2UserService customOauthUserService() {
      return org.mockito.Mockito.mock(CustomOAuth2UserService.class);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return org.mockito.Mockito.mock(ClientRegistrationRepository.class);
    }
  }
}
