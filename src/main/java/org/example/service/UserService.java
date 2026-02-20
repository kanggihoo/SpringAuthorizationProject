package org.example.service;

import org.example.dto.request.SignupRequest;

public interface UserService {
  /**
   * 회원가입 처리
   */
  void signup(SignupRequest signupRequest);
}
