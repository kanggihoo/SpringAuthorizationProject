package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.controller.docs.AuthApi;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.request.SignupRequest;
import org.example.dto.response.TokenResponseDto;
import org.example.security.authenticated.AuthenticatedUser;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.example.security.token.delivery.TokenDeliveryService;
import org.example.service.AuthService;
import org.example.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication HTTP adapter.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

  private final AuthService authService;
  private final UserService userService;
  private final TokenDeliveryService tokenDeliveryService;

  /**
   * Creates a Local User.
   */
  @PostMapping("/signup")
  public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest signupRequest) {
    userService.signup(signupRequest);
    return ResponseEntity.ok("회원가입이 완료되었습니다.");
  }

  /**
   * Authenticates a Local User and delivers the issued tokens.
   */
  @PostMapping("/login")
  public ResponseEntity<TokenResponseDto> login(
      @Valid @RequestBody LoginRequestDto requestDto,
      HttpServletResponse response
  ) {
    TokenResponseDto tokenResponse = authService.login(requestDto);
    tokenDeliveryService.addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
    return ResponseEntity.ok(tokenResponse);
  }

  /**
   * Revokes current tokens and expires the Refresh Token cookie.
   */
  @PostMapping("/logout")
  public ResponseEntity<String> logout(
      @AuthenticationPrincipal AuthenticatedUser userDetails,
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    try {
      if (userDetails != null) {
        String accessToken = tokenDeliveryService.resolveBearerAccessToken(request).orElse(null);
        authService.logout(userDetails.getJwtSubject(), accessToken);
      }

      return ResponseEntity.ok("로그아웃 되었습니다.");
    } finally {
      tokenDeliveryService.expireRefreshTokenCookie(response);
    }
  }

  /**
   * Rotates the Refresh Token and returns a new Access Token.
   */
  @PostMapping("/refresh")
  public ResponseEntity<TokenResponseDto> refresh(
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    String refreshToken = tokenDeliveryService.readRefreshToken(request)
        .orElseThrow(() -> new AuthFailureException(
            AuthFailureCode.REFRESH_TOKEN_MISSING,
            "Refresh Token이 존재하지 않습니다."));

    TokenResponseDto tokenResponse = authService.refresh(refreshToken);
    tokenDeliveryService.addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
    return ResponseEntity.ok(tokenResponse);
  }
}
