package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.dto.request.SignupRequest;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 관련 비즈니스 로직 구현체
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public void signup(SignupRequest signupRequest) {
    // 이미 존재하는 유저인지 확인
    if (userRepository.findByUsername(signupRequest.getUsername()).isPresent()) {
      throw new RuntimeException("이미 존재하는 아이디입니다.");
    }

    // 비밀번호 암호화
    String encodedPassword = passwordEncoder.encode(signupRequest.getPassword());

    // 유저 객체 생성
    User user = User.builder()
        .username(signupRequest.getUsername())
        .password(encodedPassword)
        .nickname(signupRequest.getNickname())
        .build();

    // 권한 할당 (DB에 해당 권한이 없으면 생성)
    Role role = roleRepository.findByName(signupRequest.getRole())
        .orElseGet(() -> roleRepository.save(new Role(signupRequest.getRole())));

    user.addRole(role);

    // 유저 저장
    userRepository.save(user);
  }
}
