package org.example.security.oauth2;

import java.util.Map;

/**
 * Google OAuth2 사용자 정보 파싱 구현체.
 *
 * <p>Google UserInfo API는 다음과 같은 attribute 키를 사용한다:
 * <ul>
 *   <li>{@code sub}: 사용자 고유 식별자 (Subject)</li>
 *   <li>{@code email}: 이메일 주소</li>
 *   <li>{@code name}: 표시 이름 (성 + 이름)</li>
 *   <li>{@code picture}: 프로필 사진 URL (현재 미사용)</li>
 * </ul>
 *
 * <p>이 클래스는 Google 전용 attribute 파싱 로직을 캡슐화하여,
 * {@link CustomOAuth2UserService}가 Google의 응답 구조를 직접 알 필요가 없게 한다.
 */
public class GoogleOAuth2UserInfo implements OAuth2UserInfo {

  /** Google UserInfo API로부터 수신한 사용자 attribute 맵 */
  private final Map<String, Object> attributes;

  /**
   * Google OAuth2 사용자 정보 객체를 생성한다.
   *
   * @param attributes Google UserInfo 엔드포인트에서 받은 attribute 맵
   */
  public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
    this.attributes = attributes;
  }

  /**
   * Google이 부여한 사용자 고유 식별자를 반환한다.
   * Google은 "sub" (Subject) 클레임에 사용자 ID를 담아 반환한다.
   *
   * @return Google 사용자 고유 ID (sub 클레임)
   */
  @Override
  public String getProviderId() {
    return (String) attributes.get("sub");
  }

  /**
   * Google 계정에 등록된 이메일 주소를 반환한다.
   *
   * @return 사용자 이메일
   */
  @Override
  public String getEmail() {
    return (String) attributes.get("email");
  }

  /**
   * Google 프로필에 설정된 표시 이름을 반환한다.
   * 자동 회원가입 시 닉네임으로 사용된다.
   *
   * @return 표시 이름 (성 + 이름)
   */
  @Override
  public String getName() {
    return (String) attributes.get("name");
  }
}
