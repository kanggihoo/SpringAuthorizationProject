package org.example.service;

import org.example.dto.LoginRequestDto;
import org.example.dto.TokenResponseDto;

public interface AuthService {
  TokenResponseDto login(LoginRequestDto requestDto);

  void logout(Long userId);

  TokenResponseDto refresh(String refreshToken);
}
