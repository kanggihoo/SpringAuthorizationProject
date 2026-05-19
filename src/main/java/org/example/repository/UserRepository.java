package org.example.repository;

import java.util.Optional;
import org.example.domain.entity.AuthProvider;
import org.example.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 데이터 접근 레포지토리.
 *
 * <p>일반 로그인(LOCAL) 및 OAuth2 소셜 로그인(GOOGLE 등) 유저 조회를 모두 지원한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * 로그인 ID(username)로 사용자를 조회한다.
   * 일반 로그인 인증 및 JWT 필터의 UserDetails 로딩에 사용된다.
   *
   * @param username 로그인 ID
   * @return 해당 username을 가진 User (없으면 Optional.empty())
   */
  Optional<User> findByUsername(String username);

  /**
   * OAuth2 제공자와 제공자 고유 ID로 사용자를 조회한다.
   * OAuth2 로그인 시 기존 가입 유저 여부를 판단하는 데 사용된다.
   *
   * @param provider   인증 제공자 (GOOGLE 등)
   * @param providerId 소셜 제공자가 부여한 고유 사용자 ID
   * @return 해당 소셜 계정으로 가입된 User (없으면 Optional.empty())
   */
  Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

  /**
   * 이메일로 사용자를 조회한다.
   * 향후 LOCAL 계정과 OAuth2 계정의 계정 연동 기능 구현 시 활용 예정.
   * (현재는 OAuth2 유저 등록 시 이메일을 저장만 하고 조회에는 사용하지 않음)
   *
   * @param email 사용자 이메일 주소
   * @return 해당 이메일을 가진 User (없으면 Optional.empty())
   */
  Optional<User> findByEmail(String email);
}
