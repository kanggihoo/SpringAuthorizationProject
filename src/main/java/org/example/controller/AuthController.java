package org.example.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.request.SignupRequest;
import org.example.service.UserService;
import org.example.dto.LoginRequestDto;
import org.example.dto.TokenResponseDto;
import org.example.security.CustomUserDetails;
import org.example.security.jwt.JwtTokenProvider;
import org.example.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final JwtTokenProvider jwtTokenProvider;

  private final UserService userService;

  /**
   * 회원가입 API
   * 
   * @param signupRequest 회원가입 요청 DTO (JSON 반입)
   */
  @PostMapping("/signup")
  public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest signupRequest) {
    userService.signup(signupRequest);
    return ResponseEntity.ok("회원가입이 완료되었습니다.");
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponseDto> login(
      @Valid @RequestBody LoginRequestDto requestDto,
      HttpServletResponse response) {

    TokenResponseDto tokenResponse = authService.login(requestDto);

    // RT를 쿠키에 담음
    setRefreshTokenCookie(response, tokenResponse.getRefreshToken());

    return ResponseEntity.ok(tokenResponse);
  }

  @PostMapping("/logout")
  public ResponseEntity<String> logout(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      HttpServletResponse response) {

    if (userDetails != null) {
      authService.logout(userDetails.getId());
    }

    // 기존 쿠키 삭제
    Cookie cookie = new Cookie("Refresh-Token", null);
    cookie.setMaxAge(0);
    cookie.setPath("/");
    response.addCookie(cookie);

    return ResponseEntity.ok("로그아웃 되었습니다.");
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponseDto> refresh(
      HttpServletRequest request,
      HttpServletResponse response) {

    String refreshToken = getRefreshTokenFromCookie(request)
        .orElseThrow(() -> new IllegalArgumentException("Refresh Token이 존재하지 않습니다."));

    TokenResponseDto tokenResponse = authService.refresh(refreshToken);

    // 재발급된 RT를 다시 쿠키에 담음 (RTR)
    setRefreshTokenCookie(response, tokenResponse.getRefreshToken());

    return ResponseEntity.ok(tokenResponse);
  }

  // Refresh Token 쿠키 설정 (HttpOnly, Secure)
  private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
    Cookie cookie = new Cookie("Refresh-Token", refreshToken);
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setPath("/");
    cookie.setMaxAge((int) (jwtTokenProvider.getRefreshTokenExpiration() / 1000));
    response.addCookie(cookie);
  }

  // 요청의 쿠키에서 Refresh Token 추출
  private Optional<String> getRefreshTokenFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      return Arrays.stream(cookies)
          .filter(cookie -> "Refresh-Token".equals(cookie.getName()))
          .map(Cookie::getValue)
          .findFirst();
    }
    return Optional.empty();
  }
}
