package org.example.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 계정 정보를 관리하는 엔티티.
 *
 * <p>일반 회원가입(LOCAL) 유저와 OAuth2 소셜 로그인(GOOGLE 등) 유저를 모두 포괄한다.
 * 인증 제공자(provider)와 제공자 고유 ID(providerId)를 통해 두 방식을 구분하며,
 * OAuth2 유저는 비밀번호를 갖지 않으므로 password 필드는 nullable이다.
 *
 * <p>향후 계정 연동(LOCAL ↔ GOOGLE)은 별도 API 및 user_oauth_accounts 테이블 분리로 구현 예정.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 로그인 ID. LOCAL 유저는 입력한 username, OAuth2 유저는 "GOOGLE_{providerId}" 형식 */
  @Column(unique = true, nullable = false)
  private String username;

  /**
   * 암호화된 비밀번호.
   * OAuth2 유저는 비밀번호가 없으므로 nullable 허용.
   */
  @Column(nullable = true)
  private String password;

  /** 사용자 닉네임 (화면에 표시되는 이름) */
  @Column(nullable = false)
  private String nickname;

  /**
   * 사용자 이메일 주소.
   * OAuth2 로그인 시 제공자로부터 수신하여 저장. 향후 계정 연동에 활용 예정.
   * LOCAL 유저는 회원가입 시 별도로 받지 않으므로 nullable.
   */
  @Column(nullable = true)
  private String email;

  /**
   * 인증 제공자 종류 (LOCAL, GOOGLE 등).
   * DB에는 문자열(STRING)로 저장되어 가독성을 높임.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthProvider provider = AuthProvider.LOCAL;

  /**
   * OAuth2 제공자가 부여한 고유 식별자.
   * Google의 경우 "sub" 클레임 값. LOCAL 유저는 null.
   */
  @Column(nullable = true)
  private String providerId;

  /** 계정 활성화 여부 (false 시 로그인 불가) */
  private boolean enabled = true;

  /** 계정 잠금 여부 (false 시 로그인 불가) */
  private boolean accountNonLocked = true;

  /** 유저와 권한의 N:M 관계를 해소하는 교차 테이블 설정 */
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id")
  )
  private Set<Role> roles = new HashSet<>();

  /**
   * 일반 회원가입(LOCAL) 유저 생성용 빌더.
   *
   * @param username 로그인 ID
   * @param password BCrypt 암호화된 비밀번호
   * @param nickname 닉네임
   */
  @Builder
  public User(String username, String password, String nickname) {
    this.username = username;
    this.password = password;
    this.nickname = nickname;
    this.provider = AuthProvider.LOCAL;
    this.enabled = true;
    this.accountNonLocked = true;
  }

  /**
   * OAuth2 소셜 로그인 유저 자동 회원가입용 빌더.
   *
   * @param username "GOOGLE_{providerId}" 형식의 고유 식별자
   * @param nickname 소셜 제공자로부터 받은 표시 이름
   * @param email 소셜 제공자로부터 받은 이메일
   * @param provider 인증 제공자 종류 (GOOGLE 등)
   * @param providerId 소셜 제공자의 고유 사용자 ID
   */
  @Builder(builderMethodName = "oauthBuilder")
  public User(String username, String nickname, String email,
      AuthProvider provider, String providerId) {
    this.username = username;
    this.nickname = nickname;
    this.email = email;
    this.provider = provider;
    this.providerId = providerId;
    this.enabled = true;
    this.accountNonLocked = true;
  }

  /**
   * 권한 추가 비즈니스 로직.
   *
   * @param role 추가할 권한 엔티티
   */
  public void addRole(Role role) {
    this.roles.add(role);
  }

  /**
   * Locks this User for authentication.
   */
  public void lock() {
    this.accountNonLocked = false;
  }

  /**
   * Unlocks this User for authentication.
   */
  public void unlock() {
    this.accountNonLocked = true;
  }
}
