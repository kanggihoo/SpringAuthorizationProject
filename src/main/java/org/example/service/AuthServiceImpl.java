package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.RefreshToken;
import org.example.domain.entity.User;
import org.example.dto.LoginRequestDto;
import org.example.dto.TokenResponseDto;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.security.CustomUserDetails;
import org.example.security.jwt.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final AuthenticationManager authenticationManager;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional
  public TokenResponseDto login(LoginRequestDto requestDto) {
    // 1. 인증 처리 (여기서 CustomUserDetailsService가 호출됨)
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(requestDto.getUsername(), requestDto.getPassword()));

    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
    Long userId = userDetails.getId();

    // 2. 권한 목록 추출
    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    // 3. 토큰 발급
    String accessToken = jwtTokenProvider.generateAccessToken(userDetails.getUsername(), roles);
    String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails.getUsername());

    // 4. DB에 RT 저장 (RTR 패턴)
    LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpiration() / 1000);

    Optional<RefreshToken> existingToken = refreshTokenRepository.findByUserId(userId);
    if (existingToken.isPresent()) {
      existingToken.get().updateToken(refreshToken, expiryDate);
    } else {
      refreshTokenRepository.save(new RefreshToken(userId, refreshToken, expiryDate));
    }

    return TokenResponseDto.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .build();
  }

  @Override
  @Transactional
  public void logout(Long userId) {
    refreshTokenRepository.deleteByUserId(userId);
  }

  @Override
  @Transactional
  public TokenResponseDto refresh(String oldRefreshToken) {
    // 1. 전달받은 토큰의 유효성 검증
    jwtTokenProvider.validateToken(oldRefreshToken);

    // 2. DB 토큰 조회 (RTR 방어 로직)
    RefreshToken storedToken = refreshTokenRepository.findByRefreshToken(oldRefreshToken)
        .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 Refresh Token 입니다. (탈취 의심 또는 만료)"));

    // 3. 사용자 정보 조회
    Long userId = storedToken.getUserId();
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

    // 4. 권한 추출
    List<String> roles = user.getRoles().stream()
        .map(role -> role.getName())
        .collect(Collectors.toList());

    // 5. 새 토큰 발급
    String newAccessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), roles);
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

    // 6. DB 토큰 업데이트
    LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpiration() / 1000);
    storedToken.updateToken(newRefreshToken, expiryDate);

    return TokenResponseDto.builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .tokenType("Bearer")
        .build();
  }
}
