package org.example.service;

import org.example.dto.request.LoginRequestDto;
import org.example.dto.response.TokenResponseDto;

public interface AuthService {
  TokenResponseDto login(LoginRequestDto requestDto);

  void logout(Long userId);

  TokenResponseDto refresh(String refreshToken);
}
