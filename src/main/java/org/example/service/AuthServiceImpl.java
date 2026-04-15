package org.example.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.User;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.response.TokenResponseDto;
import org.example.repository.TokenRedisRepository;
import org.example.repository.UserRepository;
import org.example.security.CustomUserDetails;
import org.example.security.jwt.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * JWT 인증 서비스 구현체.
 *
 * <p>로그인/로그아웃/토큰 갱신 비즈니스 로직을 담당한다.
 * Refresh Token은 PostgreSQL 대신 Redis에 저장하여 TTL 자동 관리를 활용하고,
 * 로그아웃 시 Access Token을 Blacklist에 등록하여 즉시 무효화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRedisRepository tokenRedisRepository;
    private final UserRepository userRepository;

    @Override
    public TokenResponseDto login(LoginRequestDto requestDto) {
        // 1. 인증 처리 (CustomUserDetailsService가 호출됨)
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(requestDto.getUsername(), requestDto.getPassword()));

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();

        // 2. 권한 목록 추출
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());

        // 3. 토큰 발급
        String accessToken = jwtTokenProvider.generateAccessToken(username, roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        // 4. Redis에 RT 저장 (TTL은 초 단위로 변환)
        long rtTtlSeconds = jwtTokenProvider.getRefreshTokenExpiration() / 1000;
        tokenRedisRepository.saveRefreshToken(username, refreshToken, rtTtlSeconds);

        return TokenResponseDto.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .build();
    }

    @Override
    public void logout(String username, String accessToken) {
        // Redis에서 RT 삭제
        tokenRedisRepository.deleteRefreshToken(username);

        // AT를 Blacklist에 등록 (남은 유효 시간만큼 TTL 설정)
        long remainingTtl = jwtTokenProvider.getRemainingExpiration(accessToken);
        tokenRedisRepository.addToBlacklist(accessToken, remainingTtl);
    }

    @Override
    public TokenResponseDto refresh(String oldRefreshToken) {
        // 1. RT에서 username 추출
        String username = jwtTokenProvider.parseClaims(oldRefreshToken).getSubject();

        // 2. Redis에서 저장된 RT 조회
        String storedToken = tokenRedisRepository.findRefreshToken(username)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 Refresh Token입니다. (만료 또는 미존재)"));

        // 3. 전달받은 RT와 저장된 RT 비교 (RTR 방어 — 불일치 시 토큰 탈취 의심)
        if (!storedToken.equals(oldRefreshToken)) {
            throw new IllegalArgumentException("Refresh Token이 일치하지 않습니다. (탈취 의심)");
        }

        // 4. 사용자 정보 조회 (권한 추출용)
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        List<String> roles = user.getRoles().stream()
            .map(role -> role.getName())
            .collect(Collectors.toList());

        // 5. 새 토큰 발급
        String newAccessToken = jwtTokenProvider.generateAccessToken(username, roles);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

        // 6. Redis에 새 RT로 갱신 (RTR 패턴)
        long rtTtlSeconds = jwtTokenProvider.getRefreshTokenExpiration() / 1000;
        tokenRedisRepository.saveRefreshToken(username, newRefreshToken, rtTtlSeconds);

        return TokenResponseDto.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .tokenType("Bearer")
            .build();
    }
}
