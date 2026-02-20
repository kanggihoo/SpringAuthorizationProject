package org.example.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * 사용자 계정 정보를 관리하는 엔티티
 */
@Entity
@Table(name = "users") // DB 내의 users 테이블
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY) // DB 차원에서 고유성 보장
  private Long id;

  @Column(unique = true, nullable = false)
  private String username; // 로그인 ID

  @Column(nullable = false)
  private String password; // 암호화된 비밀번호

  @Column(nullable = false)
  private String nickname; // 별명

  private boolean enabled = true; // 계정 활성화 여부

  private boolean accountNonLocked = true; // 계정 잠금 여부

  // 유저와 권한의 N:M 관계를 해소하는 교차 테이블 설정
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<Role> roles = new HashSet<>();

  @Builder
  public User(String username, String password, String nickname) {
    this.username = username;
    this.password = password;
    this.nickname = nickname;
    this.enabled = true;
    this.accountNonLocked = true;
  }

  /**
   * 권한 추가 비즈니스 로직
   */
  public void addRole(Role role) {
    this.roles.add(role);
  }
}
