package org.example.security.oauth2;

import java.util.Collection;
import java.util.Map;
import org.example.domain.entity.User;
import org.example.security.authenticated.AuthenticatedUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Spring Security {@link OAuth2User}와 우리 서버의 {@link User} 엔티티를 함께 보관하는 래퍼 클래스.
 *
 * <p>Spring Security OAuth2 인증 흐름에서 {@link OAuth2User}는 Google로부터 받은 정보만을 담고 있어,
 * 인증 성공 핸들러({@link OAuth2AuthenticationSuccessHandler})에서 우리 DB의 User 엔티티에
 * 접근하기 어렵다. 이 클래스는 두 정보를 함께 래핑하여 핸들러에서 User 엔티티를 바로 참조할 수 있게 한다.
 *
 * <p>권한(Authorities)은 우리 DB에 저장된 User의 Role 기반으로 제공된다.
 */
public class CustomOAuth2User implements OAuth2User, AuthenticatedUser {

  /** Google OAuth2로부터 받은 원본 사용자 정보 */
  private final OAuth2User oauth2User;

  /** DB에 저장된 (혹은 이번 요청으로 생성된) 우리 서버의 User 엔티티 */
  private final User user;

  /**
   * CustomOAuth2User 인스턴스를 생성한다.
   *
   * @param oauth2User Spring Security가 Google로부터 받아 파싱한 OAuth2 사용자 정보
   * @param user       DB에 저장된 우리 서버의 User 엔티티
   */
  public CustomOAuth2User(OAuth2User oauth2User, User user) {
    this.oauth2User = oauth2User;
    this.user = user;
  }

  /**
   * DB의 User 엔티티에 할당된 Role 기반 권한 목록을 반환한다.
   * Google 응답의 권한이 아닌, 우리 서버에서 관리하는 권한 체계를 사용한다.
   *
   * @return {@link org.example.security.CustomUserDetails}와 동일한 방식의 권한 컬렉션
   */
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    // User 엔티티의 Role Set을 Spring Security GrantedAuthority로 변환
    return user.getRoles().stream()
        .map(role -> (GrantedAuthority) role::getName)
        .toList();
  }

  @Override
  public Long getId() {
    return user.getId();
  }

  @Override
  public String getJwtSubject() {
    return user.getUsername();
  }

  @Override
  public String getNickname() {
    return user.getNickname();
  }

  @Override
  public boolean isEnabled() {
    return user.isEnabled();
  }

  @Override
  public boolean isAccountNonLocked() {
    return user.isAccountNonLocked();
  }

  /**
   * Google UserInfo API로부터 받은 사용자 attribute 맵을 반환한다.
   *
   * @return attribute 맵 (sub, email, name, picture 등 포함)
   */
  @Override
  public Map<String, Object> getAttributes() {
    return oauth2User.getAttributes();
  }

  /**
   * 사용자를 식별하는 이름을 반환한다.
   * Google은 기본적으로 "sub" 값을 name으로 사용하므로 그대로 위임한다.
   *
   * @return Google OAuth2 사용자 식별자
   */
  @Override
  public String getName() {
    return oauth2User.getName();
  }

  /**
   * DB에 저장된 우리 서버의 User 엔티티를 반환한다.
   * SuccessHandler에서 username, id 등 서버 측 정보에 접근할 때 사용한다.
   *
   * @return 우리 서버의 User 엔티티
   */
  public User getUser() {
    return user;
  }
}
