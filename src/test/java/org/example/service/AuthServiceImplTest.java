package org.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.response.TokenResponseDto;
import org.example.repository.UserRepository;
import org.example.security.CustomUserDetails;
import org.example.security.account.LoginFailureCounter;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.example.security.token.TokenLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  @Mock
  private AuthenticationManager authenticationManager;

  @Mock
  private TokenLifecycleService tokenLifecycleService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private LoginFailureCounter loginFailureCounter;

  @InjectMocks
  private AuthServiceImpl authServiceImpl;

  @Test
  @DisplayName("login delegates token issuing to TokenLifecycleService")
  void login_delegatesTokenIssuingToTokenLifecycleService() {
    given(userRepository.findByUsername("testuser"))
        .willReturn(Optional.of(user("testuser", true)));
    given(authenticationManager.authenticate(any()))
        .willReturn(authentication("principal-subject"));
    given(tokenLifecycleService.issue(eq("principal-subject"), eq(List.of("ROLE_USER"))))
        .willReturn(tokenResponse("access-token", "refresh-token"));

    TokenResponseDto result = authServiceImpl.login(createLoginRequest("testuser", "password123"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    verify(loginFailureCounter).clear("testuser");
    verify(tokenLifecycleService).issue("principal-subject", List.of("ROLE_USER"));
  }

  @Test
  @DisplayName("login locks User after fifth bad credential failure")
  void login_locksUser_afterFiveFailures() {
    User user = user("testuser", true);
    BadCredentialsException badCredentials = new BadCredentialsException("Bad credentials");
    given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
    given(authenticationManager.authenticate(any())).willThrow(badCredentials);
    given(loginFailureCounter.recordFailure("testuser")).willReturn(true);

    assertThatThrownBy(() ->
        authServiceImpl.login(createLoginRequest("testuser", "wrong-password")))
        .isInstanceOfSatisfying(AuthFailureException.class, failure -> {
          assertThat(failure.getCode()).isEqualTo(AuthFailureCode.ACCOUNT_LOCKED);
          assertThat(failure.getCause()).isSameAs(badCredentials);
        });

    assertThat(user.isAccountNonLocked()).isFalse();
    verify(userRepository).save(user);
    verify(tokenLifecycleService, never()).issue(any(), any());
  }

  @Test
  @DisplayName("login rejects bad credentials without locking before threshold")
  void login_throwsBadCredentials_whenFailureThresholdNotReached() {
    User user = user("testuser", true);
    BadCredentialsException badCredentials = new BadCredentialsException("Bad credentials");
    given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
    given(authenticationManager.authenticate(any())).willThrow(badCredentials);
    given(loginFailureCounter.recordFailure("testuser")).willReturn(false);

    assertThatThrownBy(() ->
        authServiceImpl.login(createLoginRequest("testuser", "wrong-password")))
        .isInstanceOfSatisfying(AuthFailureException.class, failure -> {
          assertThat(failure.getCode()).isEqualTo(AuthFailureCode.BAD_CREDENTIALS);
          assertThat(failure.getCause()).isSameAs(badCredentials);
        });

    assertThat(user.isAccountNonLocked()).isTrue();
    verify(userRepository, never()).save(any());
    verify(tokenLifecycleService, never()).issue(any(), any());
  }

  @Test
  @DisplayName("login rejects locked User before authentication")
  void login_throwsLockedException_whenUserLocked() {
    given(userRepository.findByUsername("testuser"))
        .willReturn(Optional.of(user("testuser", false)));

    assertThatThrownBy(() -> authServiceImpl.login(createLoginRequest("testuser", "password123")))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.ACCOUNT_LOCKED));

    verify(authenticationManager, never()).authenticate(any());
    verify(loginFailureCounter, never()).recordFailure(any());
    verify(loginFailureCounter, never()).clear(any());
    verify(tokenLifecycleService, never()).issue(any(), any());
  }

  @Test
  @DisplayName("login clears failure counter after successful authentication")
  void login_clearsFailureCounter_whenAuthenticationSucceeds() {
    given(userRepository.findByUsername("testuser"))
        .willReturn(Optional.of(user("testuser", true)));
    given(authenticationManager.authenticate(any())).willReturn(authentication());
    given(tokenLifecycleService.issue(eq("testuser"), any()))
        .willReturn(tokenResponse("access-token", "refresh-token"));

    TokenResponseDto result = authServiceImpl.login(createLoginRequest("testuser", "password123"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    verify(loginFailureCounter).clear("testuser");
    verify(tokenLifecycleService).issue(eq("testuser"), any());
  }

  @Test
  @DisplayName("login rejects unknown User as bad credentials")
  void login_throwsBadCredentials_whenUserIsUnknown() {
    given(userRepository.findByUsername("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authServiceImpl.login(createLoginRequest("missing", "password123")))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.BAD_CREDENTIALS));

    verify(loginFailureCounter, never()).recordFailure(any());
    verify(authenticationManager, never()).authenticate(any());
    verify(tokenLifecycleService, never()).issue(any(), any());
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
    return authentication("testuser");
  }

  private Authentication authentication(String principalSubject) {
    User user = user(principalSubject, true);
    CustomUserDetails userDetails = new CustomUserDetails(user);
    return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
  }

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

  private TokenResponseDto tokenResponse(String accessToken, String refreshToken) {
    return TokenResponseDto.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .build();
  }
}
