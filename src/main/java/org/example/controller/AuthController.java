package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.request.SignupRequest;
import org.example.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 API 컨트롤러
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

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
}
