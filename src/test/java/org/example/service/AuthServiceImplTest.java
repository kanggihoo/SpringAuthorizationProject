package org.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.response.TokenResponseDto;
import org.example.security.CustomUserDetails;
import org.example.security.token.TokenLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  @Mock
  private AuthenticationManager authenticationManager;

  @Mock
  private TokenLifecycleService tokenLifecycleService;

  @InjectMocks
  private AuthServiceImpl authServiceImpl;

  @Test
  @DisplayName("login delegates token issuing to TokenLifecycleService")
  void login_delegatesTokenIssuingToTokenLifecycleService() {
    given(authenticationManager.authenticate(any())).willReturn(authentication());
    given(tokenLifecycleService.issue(eq("testuser"), any()))
        .willReturn(tokenResponse("access-token", "refresh-token"));

    TokenResponseDto result = authServiceImpl.login(createLoginRequest("testuser", "password123"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    verify(tokenLifecycleService).issue(eq("testuser"), any());
  }

  @Test
  @DisplayName("logout delegates token revocation to TokenLifecycleService")
  void logout_delegatesTokenRevocationToTokenLifecycleService() {
    authServiceImpl.logout("testuser", "access-token");

    verify(tokenLifecycleService).logout("testuser", "access-token");
  }

  @Test
  @DisplayName("refresh delegates Refresh Token Rotation to TokenLifecycleService")
  void refresh_delegatesRefreshTokenRotationToTokenLifecycleService() {
    given(tokenLifecycleService.rotate("old-refresh-token"))
        .willReturn(tokenResponse("new-access-token", "new-refresh-token"));

    TokenResponseDto result = authServiceImpl.refresh("old-refresh-token");

    assertThat(result.getAccessToken()).isEqualTo("new-access-token");
    assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
    verify(tokenLifecycleService).rotate("old-refresh-token");
  }

  private LoginRequestDto createLoginRequest(String username, String password) {
    return LoginRequestDto.builder()
        .username(username)
        .password(password)
        .build();
  }

  private Authentication authentication() {
    User user = User.builder()
        .username("testuser")
        .password("encoded")
        .nickname("tester")
        .build();
    user.addRole(new Role("ROLE_USER"));
    CustomUserDetails userDetails = new CustomUserDetails(user);
    return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
  }

  private TokenResponseDto tokenResponse(String accessToken, String refreshToken) {
    return TokenResponseDto.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .build();
  }
}
