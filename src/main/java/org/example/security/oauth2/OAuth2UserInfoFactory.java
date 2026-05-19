package org.example.security.oauth2;

import java.util.Map;

/**
 * OAuth2 제공자 종류에 따라 적절한 {@link OAuth2UserInfo} 구현체를 반환하는 팩토리 클래스.
 *
 * <p>이 팩토리를 통해 {@link CustomOAuth2UserService}는 제공자 별 파싱 로직을 직접 알 필요 없이
 * 통일된 {@link OAuth2UserInfo} 인터페이스를 사용할 수 있다.
 *
 * <p>새로운 OAuth2 제공자(GitHub, Kakao 등)를 추가할 때는:
 * <ol>
 *   <li>새 구현체 클래스를 생성한다. (예: {@code GithubOAuth2UserInfo})</li>
 *   <li>이 팩토리의 switch에 case를 추가한다.</li>
 *   <li>{@link CustomOAuth2UserService} 등 다른 코드는 수정하지 않아도 된다. (OCP 준수)</li>
 * </ol>
 */
public class OAuth2UserInfoFactory {

  /**
   * 정적 유틸리티 클래스이므로 인스턴스화를 방지한다.
   */
  private OAuth2UserInfoFactory() {
  }

  /**
   * OAuth2 제공자 ID와 사용자 attribute를 기반으로 적합한 {@link OAuth2UserInfo} 구현체를 반환한다.
   *
   * @param registrationId Spring Security OAuth2의 제공자 등록 ID (예: "google", "github")
   * @param attributes     OAuth2 UserInfo 엔드포인트로부터 받은 사용자 attribute 맵
   * @return 해당 제공자에 맞는 {@link OAuth2UserInfo} 구현체
   * @throws IllegalArgumentException 지원하지 않는 제공자 ID일 경우
   */
  public static OAuth2UserInfo getOAuth2UserInfo(
      String registrationId, Map<String, Object> attributes) {

    return switch (registrationId.toLowerCase()) {
      case "google" -> new GoogleOAuth2UserInfo(attributes);
      // 향후 추가 예시:
      // case "github" -> new GithubOAuth2UserInfo(attributes);
      // case "kakao"  -> new KakaoOAuth2UserInfo(attributes);
      // case "naver"  -> new NaverOAuth2UserInfo(attributes);
      default -> throw new IllegalArgumentException(
          "지원하지 않는 OAuth2 제공자입니다: " + registrationId);
    };
  }
}
