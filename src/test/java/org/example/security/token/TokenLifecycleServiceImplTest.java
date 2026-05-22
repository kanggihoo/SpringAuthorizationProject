package org.example.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.List;
import java.util.Optional;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.dto.response.TokenResponseDto;
import org.example.repository.TokenRedisRepository;
import org.example.repository.UserRepository;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.example.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class TokenLifecycleServiceImplTest {

  @Mock
  private JwtTokenProvider jwtTokenProvider;

  @Mock
  private TokenRedisRepository tokenRedisRepository;

  @Mock
  private UserRepository userRepository;

  @Spy
  private RedisFailurePolicy redisFailurePolicy = new RedisFailurePolicy();

  @InjectMocks
  private TokenLifecycleServiceImpl tokenLifecycleService;

  @Test
  @DisplayName("issue stores the Refresh Token in the Token Store for the JWT Subject")
  void issue_storesRefreshTokenForJwtSubject() {
    given(jwtTokenProvider.generateAccessToken(eq("testuser"), any()))
        .willReturn("access-token");
    given(jwtTokenProvider.generateRefreshToken("testuser"))
        .willReturn("refresh-token");
    given(jwtTokenProvider.getRefreshTokenExpiration())
        .willReturn(604_800_000L);

    TokenResponseDto result = tokenLifecycleService.issue("testuser", List.of("ROLE_USER"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    assertThat(result.getTokenType()).isEqualTo("Bearer");
    verify(tokenRedisRepository).saveRefreshToken("testuser", "refresh-token", 604_800L);
  }

  @Test
  @DisplayName("issue fails closed when the Token Store is unavailable")
  void issue_throwsTokenStoreUnavailable_whenRedisSaveFails() {
    given(jwtTokenProvider.generateAccessToken(eq("testuser"), any()))
        .willReturn("access-token");
    given(jwtTokenProvider.generateRefreshToken("testuser"))
        .willReturn("refresh-token");
    given(jwtTokenProvider.getRefreshTokenExpiration())
        .willReturn(604_800_000L);
    doThrow(new DataAccessResourceFailureException("redis down"))
        .when(tokenRedisRepository)
        .saveRefreshToken("testuser", "refresh-token", 604_800L);

    assertThatThrownBy(() -> tokenLifecycleService.issue("testuser", List.of("ROLE_USER")))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.TOKEN_STORE_UNAVAILABLE));
  }

  @Test
  @DisplayName("rotate rejects a reused Refresh Token that differs from the Token Store value")
  void rotate_rejectsReusedRefreshToken() {
    given(jwtTokenProvider.parseClaims("old-refresh-token"))
        .willReturn(claims("testuser"));
    given(tokenRedisRepository.findRefreshToken("testuser"))
        .willReturn(Optional.of("current-refresh-token"));

    assertThatThrownBy(() -> tokenLifecycleService.rotate("old-refresh-token"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.REFRESH_TOKEN_REUSED));
  }

  @Test
  @DisplayName("rotate rejects a Refresh Token that is missing from the Token Store")
  void rotate_rejectsMissingRefreshToken() {
    given(jwtTokenProvider.parseClaims("missing-refresh-token"))
        .willReturn(claims("testuser"));
    given(tokenRedisRepository.findRefreshToken("testuser"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> tokenLifecycleService.rotate("missing-refresh-token"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.REFRESH_TOKEN_INVALID));
  }

  @Test
  @DisplayName("rotate fails closed when the Token Store cannot read the active Refresh Token")
  void rotate_throwsTokenStoreUnavailable_whenRedisReadFails() {
    given(jwtTokenProvider.parseClaims("refresh-token"))
        .willReturn(claims("testuser"));
    given(tokenRedisRepository.findRefreshToken("testuser"))
        .willThrow(new DataAccessResourceFailureException("redis down"));

    assertThatThrownBy(() -> tokenLifecycleService.rotate("refresh-token"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.TOKEN_STORE_UNAVAILABLE));
  }

  @Test
  @DisplayName("rotate replaces the active Refresh Token for the same JWT Subject")
  void rotate_replacesActiveRefreshToken() {
    User user = User.builder()
        .username("testuser")
        .password("encoded")
        .nickname("tester")
        .build();
    user.addRole(new Role("ROLE_USER"));

    given(jwtTokenProvider.parseClaims("old-refresh-token"))
        .willReturn(claims("testuser"));
    given(tokenRedisRepository.findRefreshToken("testuser"))
        .willReturn(Optional.of("old-refresh-token"));
    given(userRepository.findByUsername("testuser"))
        .willReturn(Optional.of(user));
    given(jwtTokenProvider.generateAccessToken(anyString(), any()))
        .willReturn("new-access-token");
    given(jwtTokenProvider.generateRefreshToken("testuser"))
        .willReturn("new-refresh-token");
    given(jwtTokenProvider.getRefreshTokenExpiration())
        .willReturn(604_800_000L);

    TokenResponseDto result = tokenLifecycleService.rotate("old-refresh-token");

    assertThat(result.getAccessToken()).isEqualTo("new-access-token");
    assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
    verify(tokenRedisRepository).saveRefreshToken("testuser", "new-refresh-token", 604_800L);
  }

  @Test
  @DisplayName("logout removes the active Refresh Token and blacklists the Access Token")
  void logout_removesRefreshTokenAndBlacklistsAccessToken() {
    given(jwtTokenProvider.getRemainingExpiration("access-token"))
        .willReturn(3_600_000L);

    tokenLifecycleService.logout("testuser", "access-token");

    verify(tokenRedisRepository).deleteRefreshToken("testuser");
    verify(tokenRedisRepository).addToBlacklist("access-token", 3_600_000L);
  }

  @Test
  @DisplayName("logout fails closed when the Token Store cannot revoke tokens")
  void logout_throwsTokenStoreUnavailable_whenRedisDeleteFails() {
    doThrow(new DataAccessResourceFailureException("redis down"))
        .when(tokenRedisRepository)
        .deleteRefreshToken("testuser");

    assertThatThrownBy(() -> tokenLifecycleService.logout("testuser", "access-token"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.TOKEN_STORE_UNAVAILABLE));
  }

  @Test
  @DisplayName("allows a non-blacklisted Access Token")
  void isAccessTokenAllowed_returnsTrueForNonBlacklistedToken() {
    given(tokenRedisRepository.isBlacklisted("access-token")).willReturn(false);

    assertThat(tokenLifecycleService.isAccessTokenAllowed("access-token")).isTrue();
  }

  @Test
  @DisplayName("rejects a Blacklisted Access Token")
  void isAccessTokenAllowed_returnsFalseForBlacklistedToken() {
    given(tokenRedisRepository.isBlacklisted("access-token")).willReturn(true);

    assertThat(tokenLifecycleService.isAccessTokenAllowed("access-token")).isFalse();
  }

  @Test
  @DisplayName("Access Token validation fails closed when the Logout Blacklist cannot be checked")
  void isAccessTokenAllowed_throwsTokenStoreUnavailable_whenRedisReadFails() {
    given(tokenRedisRepository.isBlacklisted("access-token"))
        .willThrow(new DataAccessResourceFailureException("redis down"));

    assertThatThrownBy(() -> tokenLifecycleService.isAccessTokenAllowed("access-token"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.TOKEN_STORE_UNAVAILABLE));
  }

  private Claims claims(String subject) {
    return Jwts.claims().subject(subject).build();
  }
}
