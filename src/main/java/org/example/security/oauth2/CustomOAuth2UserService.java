package org.example.security.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth2 로그인 시 사용자 정보를 처리하는 서비스.
 *
 * <p>Spring Security의 {@link DefaultOAuth2UserService}를 확장하여,
 * Google 등 소셜 제공자로부터 받은 사용자 정보를 우리 서버의 User 엔티티와 연결한다.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>부모 클래스(DefaultOAuth2UserService)를 통해 OAuth2 제공자로부터 사용자 정보 수신</li>
 *   <li>{@link OAuth2UserInfoFactory}를 통해 제공자 종류에 맞는 파서로 사용자 정보 추출</li>
 *   <li>DB에서 기존 가입 여부 조회 (provider + providerId 기준)</li>
 *   <li>최초 로그인 시 자동 회원가입 처리</li>
 *   <li>{@link CustomOAuth2User}로 래핑하여 반환 (SuccessHandler에서 User 엔티티 접근 가능)</li>
 * </ol>
 *
 * <p>Provider별 분기 로직이 없어 새로운 제공자 추가 시 이 클래스를 수정할 필요가 없다. (OCP 준수)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final OAuth2UserRegistrationService oAuth2UserRegistrationService;

  /**
   * OAuth2 인증 성공 후 사용자 정보를 로드하고 우리 서버의 User 엔티티와 연결한다.
   *
   * @param userRequest OAuth2 인증 요청 정보 (registrationId, access token 포함)
   * @return 우리 서버의 User 엔티티가 포함된 {@link CustomOAuth2User}
   * @throws OAuth2AuthenticationException 사용자 정보 로딩 또는 회원가입 처리 실패 시
   */
  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    // 1. 부모 클래스를 통해 소셜 제공자로부터 사용자 정보 수신
    // 이미 "인가 코드(Authorization Code)"를 "액세스 토큰(Access Token)"으로 교환한 직후
    OAuth2User oauth2User = super.loadUser(userRequest);

    // 2. 제공자 ID 추출 (예: "google")
    String registrationId = userRequest.getClientRegistration().getRegistrationId();

    // 3. provider attributes를 애플리케이션 User 엔티티와 연결한다.
    User user = oAuth2UserRegistrationService.findOrRegister(registrationId, oauth2User.getAttributes());

    log.info("OAuth2 사용자 처리 완료 - userId: {}, username: {}", user.getId(), user.getUsername());

    // 4. 소셜 OAuth2User와 우리 서버 User를 함께 래핑하여 반환
    return new CustomOAuth2User(oauth2User, user);
  }
}
