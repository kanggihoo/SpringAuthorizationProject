package org.example.service;

import io.jsonwebtoken.ExpiredJwtException;
import org.example.TestFixtures;
import org.example.domain.entity.RefreshToken;
import org.example.domain.entity.User;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.response.TokenResponseDto;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.security.CustomUserDetails;
import org.example.security.jwt.JwtTokenProvider;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl — 인증 및 토큰 관리 서비스")
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AuthServiceImpl authService;

    @Test
    @DisplayName("login: 기존 RT가 없으면 새로 발급하고 DB에 저장(save)한다")
    void login_returnsTokens_andCreatesNewRefreshToken() {
        // given
        LoginRequestDto request = new LoginRequestDto();
        ReflectionTestUtils.setField(request, "username", "testuser");
        ReflectionTestUtils.setField(request, "password", "password");
        
        CustomUserDetails userDetails = TestFixtures.buildUserDetails(1L, "testuser", "ROLE_USER");
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        
        given(authenticationManager.authenticate(any())).willReturn(auth);
        given(jwtTokenProvider.generateAccessToken(anyString(), any())).willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken(anyString())).willReturn("new-refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604800000L);
        given(refreshTokenRepository.findByUserId(1L)).willReturn(Optional.empty());

        // when
        TokenResponseDto response = authService.login(request);

        // then
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        then(refreshTokenRepository).should().save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("login: 기존 RT가 존재하면 기존 엔티티를 갱신(updateToken)하고 save는 호출하지 않는다")
    void login_updatesExistingRefreshToken_onReLogin() {
        // given
        LoginRequestDto request = new LoginRequestDto();
        ReflectionTestUtils.setField(request, "username", "testuser");
        ReflectionTestUtils.setField(request, "password", "password");

        CustomUserDetails userDetails = TestFixtures.buildUserDetails(1L, "testuser", "ROLE_USER");
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        
        RefreshToken existingToken = TestFixtures.buildRefreshToken(10L, 1L, "old-refresh-token");
        
        given(authenticationManager.authenticate(any())).willReturn(auth);
        given(jwtTokenProvider.generateAccessToken(anyString(), any())).willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken(anyString())).willReturn("new-refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604800000L);
        given(refreshTokenRepository.findByUserId(1L)).willReturn(Optional.of(existingToken));

        // when
        TokenResponseDto response = authService.login(request);

        // then
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(existingToken.getRefreshToken()).isEqualTo("new-refresh-token");
        then(refreshTokenRepository).shouldHaveNoMoreInteractions(); // save 호출 없음 검증
    }

    @Test
    @DisplayName("login: 잘못된 자격증명이면 BadCredentialsException을 전파한다")
    void login_propagatesException_onBadCredentials() {
        // given
        LoginRequestDto request = new LoginRequestDto();
        ReflectionTestUtils.setField(request, "username", "wronguser");
        ReflectionTestUtils.setField(request, "password", "wrongpass");

        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("Bad credentials"));

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("logout: userId로 RefreshToken을 DB에서 삭제한다")
    void logout_deletesRefreshToken_byUserId() {
        // given
        Long userId = 1L;
        willDoNothing().given(refreshTokenRepository).deleteByUserId(userId);

        // when
        authService.logout(userId);

        // then
        then(refreshTokenRepository).should().deleteByUserId(userId); // refreshTokenRepository.deleteByUserId 메서드 호출여부확인
    }

    @Test
    @DisplayName("refresh: 정상 RTR 순환 시 새 토큰을 발급하고 DB를 업데이트한다")
    void refresh_returnsNewTokens_andUpdatesDb() {
        // given
        String oldTokenStr = "old-refresh-token";
        RefreshToken storedTokenStr = TestFixtures.buildRefreshToken(10L, 1L, oldTokenStr);
        User user = TestFixtures.buildUser(1L, "testuser", "Nick", "ROLE_USER");

        given(jwtTokenProvider.validateToken(oldTokenStr)).willReturn(true);
        given(refreshTokenRepository.findByRefreshToken(oldTokenStr)).willReturn(Optional.of(storedTokenStr));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.generateAccessToken(anyString(), any())).willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken(anyString())).willReturn("new-refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604800000L);

        // when
        TokenResponseDto response = authService.refresh(oldTokenStr);

        // then
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(storedTokenStr.getRefreshToken()).isEqualTo("new-refresh-token"); // RTR 업데이트 확인
    }

    @Test
    @DisplayName("refresh: DB에 없는 RT로 재발급 시도 시 탈취로 간주하고 예외를 던진다")
    void refresh_throwsException_whenTokenNotInDb() {
        // given
        String tokenStr = "stolen-or-outdated-token";
        given(jwtTokenProvider.validateToken(tokenStr)).willReturn(true);
        given(refreshTokenRepository.findByRefreshToken(tokenStr)).willReturn(Optional.empty()); // DB 없음

        // when & then
        assertThatThrownBy(() -> authService.refresh(tokenStr))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 Refresh Token");
    }

    @Test
    @DisplayName("refresh: 만료된 RT가 주어지면 DB미조회로 ExpiredJwtException을 전파한다")
    void refresh_propagatesExpiredException() {
        // given
        String expiredToken = "expired-token";
        willThrow(new ExpiredJwtException(null, null, "Expired")).given(jwtTokenProvider).validateToken(expiredToken);

        // when & then
        assertThatThrownBy(() -> authService.refresh(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
        then(refreshTokenRepository).shouldHaveNoInteractions(); // DB조회 안함 검증
    }

    @Test
    @DisplayName("refresh: 유저가 삭제되어 DB에서 찾을 수 없으면 UsernameNotFoundException 던진다")
    void refresh_throwsUsernameNotFoundException_whenUserDeleted() {
        // given
        String validToken = "valid-token";
        RefreshToken storedToken = TestFixtures.buildRefreshToken(10L, 999L, validToken); // 삭제된 유저의 ID

        given(jwtTokenProvider.validateToken(validToken)).willReturn(true);
        given(refreshTokenRepository.findByRefreshToken(validToken)).willReturn(Optional.of(storedToken));
        given(userRepository.findById(999L)).willReturn(Optional.empty()); // 유저 없음

        // when & then
        assertThatThrownBy(() -> authService.refresh(validToken))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }
}
