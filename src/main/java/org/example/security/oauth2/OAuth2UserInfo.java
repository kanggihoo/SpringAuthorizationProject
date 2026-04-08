package org.example.security.oauth2;

/**
 * OAuth2 제공자별 사용자 정보 파싱을 추상화한 인터페이스.
 *
 * <p>Google, GitHub, Kakao 등 각 OAuth2 제공자는 사용자 정보를 서로 다른 attribute 키로 반환한다.
 * (Google: {@code sub}, GitHub: {@code id}, Naver: {@code response.id} 등)
 * 이 인터페이스는 그 차이를 캡슐화하여 {@link CustomOAuth2UserService}가
 * 제공자 종류에 상관없이 동일한 방식으로 사용자 정보를 다룰 수 있도록 한다.
 *
 * <p>새로운 OAuth2 제공자를 추가할 때는:
 * <ol>
 *   <li>이 인터페이스를 구현하는 새 클래스를 생성한다. (예: {@code GithubOAuth2UserInfo})</li>
 *   <li>{@link OAuth2UserInfoFactory}에 해당 제공자의 case를 추가한다.</li>
 *   <li>기존 코드는 수정하지 않아도 된다. (OCP 준수)</li>
 * </ol>
 */
public interface OAuth2UserInfo {

  /**
   * OAuth2 제공자가 부여한 사용자의 고유 식별자를 반환한다.
   * Google: {@code sub} 클레임, GitHub: {@code id} 필드
   *
   * @return 제공자 고유 사용자 ID
   */
  String getProviderId();

  /**
   * 사용자의 이메일 주소를 반환한다.
   *
   * @return 이메일 주소
   */
  String getEmail();

  /**
   * 사용자의 표시 이름(Display Name)을 반환한다.
   * 소셜 로그인 자동 회원가입 시 닉네임으로 사용된다.
   *
   * @return 표시 이름
   */
  String getName();
}
