package org.example.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // 토큰 검증이 필요 없는 경로 설정
    return path.startsWith("/login") || path.startsWith("/signup") || path.startsWith("/refresh");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = resolveToken(request);

    // 토큰이 존재하고 유효한 경우에만 인증 처리
    if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
      Claims claims = jwtTokenProvider.parseClaims(token);
      String username = claims.getSubject();

      // 토큰에 담긴 권한 정보 추출
      @SuppressWarnings("unchecked")
      List<String> roles = claims.get("roles", List.class);
      List<SimpleGrantedAuthority> authorities = roles.stream()
          .map(SimpleGrantedAuthority::new)
          .collect(Collectors.toList());

      // DB 조회 없이 SecurityContext에 인증 객체 저장 => 이미 jwt로 자격증명이 끝났으므로 Authentication 객체
      // 생성 시 credentials는 null
      UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username, null,
          authorities);

      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
  }

  // Authorization 헤더에서 Bearer 토큰 추출
  private String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }
}
