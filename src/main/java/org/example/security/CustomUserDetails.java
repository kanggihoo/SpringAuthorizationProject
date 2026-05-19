package org.example.security;

import org.example.domain.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Spring Security의 UserDetails 구현체
 * 보안 컨텍스트에서 유저 정보를 유지하기 위해 사용됨
 */
public class CustomUserDetails implements UserDetails {

  private final Long id;
  private final String username;
  private final String password;
  private final String nickname;
  private final boolean enabled;
  private final boolean accountNonLocked;
  private final Collection<? extends GrantedAuthority> authorities;

  public CustomUserDetails(User user) {
    this.id = user.getId();
    this.username = user.getUsername();
    this.password = user.getPassword();
    this.nickname = user.getNickname();
    this.enabled = user.isEnabled();
    this.accountNonLocked = user.isAccountNonLocked();
    // Entity의 Role 정보를 Security의 GrantedAuthority로 변환
    this.authorities = user.getRoles().stream()
        .map(role -> new SimpleGrantedAuthority(role.getName()))
        .collect(Collectors.toList());
  }

  /** 사용자 고유 ID 반환 (JWT 토큰 처리 등에 활용) */
  public Long getId() {
    return id;
  }

  /** 닉네임 반환 */
  public String getNickname() {
    return nickname;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true; // 기본값 true 설정
  }

  @Override
  public boolean isAccountNonLocked() {
    return accountNonLocked;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true; // 기본값 true 설정
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
