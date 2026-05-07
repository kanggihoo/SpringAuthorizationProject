package org.example.security.oauth2;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.AuthProvider;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth2 provider attribute를 애플리케이션 User 엔티티로 연결한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserRegistrationService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;

  /**
   * provider 고유 식별자로 기존 유저를 찾고, 없으면 OAuth2 유저를 자동 가입시킨다.
   *
   * @param registrationId Spring Security OAuth2 provider 등록 ID
   * @param attributes provider user-info attributes
   * @return 기존 또는 신규 User 엔티티
   */
  @Transactional
  public User findOrRegister(String registrationId, Map<String, Object> attributes) {
    OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, attributes);
    AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

    log.info("OAuth2 로그인 시도 - provider: {}, providerId: {}, email: {}",
        registrationId, userInfo.getProviderId(), userInfo.getEmail());

    return userRepository
        .findByProviderAndProviderId(provider, userInfo.getProviderId())
        .orElseGet(() -> registerNewOAuth2User(provider, userInfo));
  }

  private User registerNewOAuth2User(AuthProvider provider, OAuth2UserInfo userInfo) {
    log.info("신규 OAuth2 유저 자동 회원가입 - provider: {}, email: {}", provider, userInfo.getEmail());

    String username = provider.name() + "_" + userInfo.getProviderId();
    User newUser = User.oauthBuilder()
        .username(username)
        .nickname(userInfo.getName())
        .email(userInfo.getEmail())
        .provider(provider)
        .providerId(userInfo.getProviderId())
        .build();

    Role userRole = roleRepository.findByName("ROLE_USER")
        .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
    newUser.addRole(userRole);

    return userRepository.save(newUser);
  }
}
