package org.example.security.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.AuthProvider;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
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

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;

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
    OAuth2User oauth2User = super.loadUser(userRequest);

    // 2. 제공자 ID 추출 (예: "google")
    String registrationId = userRequest.getClientRegistration().getRegistrationId();

    // 3. 팩토리를 통해 제공자에 맞는 파서로 사용자 정보 추출 (Google: sub, email, name)
    //    새로운 제공자 추가 시 이 코드는 변경 불필요 (OCP 준수)
    OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
        registrationId, oauth2User.getAttributes());

    String providerId = userInfo.getProviderId();
    String email = userInfo.getEmail();

    log.info("OAuth2 로그인 시도 - provider: {}, providerId: {}, email: {}",
        registrationId, providerId, email);

    // 4. provider + providerId 기준으로 기존 가입 유저 조회
    //    (이메일이 아닌 providerId로 조회하는 이유: 이메일은 변경될 수 있지만 sub는 불변)
    User user = userRepository
        .findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
        .orElseGet(() -> registerNewOAuth2User(userInfo));

    log.info("OAuth2 사용자 처리 완료 - userId: {}, username: {}", user.getId(), user.getUsername());

    // 5. Google OAuth2User와 우리 서버 User를 함께 래핑하여 반환
    return new CustomOAuth2User(oauth2User, user);
  }

  /**
   * 최초 OAuth2 로그인 시 우리 서버에 자동으로 회원가입을 처리한다.
   *
   * <p>username 규칙: "GOOGLE_{providerId}" — LOCAL 유저의 username과 충돌 방지.
   * 비밀번호는 OAuth2 유저에게 불필요하므로 설정하지 않는다(null).
   *
   * @param userInfo 소셜 제공자로부터 파싱된 사용자 정보
   * @return 저장된 새 User 엔티티
   */
  private User registerNewOAuth2User(OAuth2UserInfo userInfo) {
    log.info("신규 OAuth2 유저 자동 회원가입 - email: {}", userInfo.getEmail());

    // OAuth2 유저 전용 username: "GOOGLE_{providerId}" 형식으로 LOCAL 유저와 충돌 방지
    String username = "GOOGLE_" + userInfo.getProviderId();

    // 신규 OAuth2 유저 엔티티 생성 (password는 null — OAuth2 유저는 비밀번호 불필요)
    User newUser = User.oauthBuilder()
        .username(username)
        .nickname(userInfo.getName())
        .email(userInfo.getEmail())
        .provider(AuthProvider.GOOGLE)
        .providerId(userInfo.getProviderId())
        .build();

    // 기본 권한 ROLE_USER 부여
    Role userRole = roleRepository.findByName("ROLE_USER")
        .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
    newUser.addRole(userRole);

    return userRepository.save(newUser);
  }
}
