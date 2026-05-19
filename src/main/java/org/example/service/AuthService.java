package org.example.service;

import org.example.dto.request.LoginRequestDto;
import org.example.dto.response.TokenResponseDto;

public interface AuthService {
  TokenResponseDto login(LoginRequestDto requestDto);

  /**
   * 로그아웃 처리: Redis에서 RT 삭제 + AT Blacklist 등록.
   *
   * @param username    사용자 고유 식별자 (JWT subject)
   * @param accessToken 현재 요청의 AT (Blacklist 등록용)
   */
  void logout(String username, String accessToken);

  TokenResponseDto refresh(String refreshToken);
}
