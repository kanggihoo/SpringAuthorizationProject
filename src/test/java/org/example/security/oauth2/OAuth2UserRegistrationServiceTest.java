package org.example.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import java.util.Optional;
import org.example.domain.entity.AuthProvider;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuth2UserRegistrationServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private RoleRepository roleRepository;

  @InjectMocks
  private OAuth2UserRegistrationService oAuth2UserRegistrationService;

  @Test
  @DisplayName("기존 OAuth2 유저가 있으면 새 row를 생성하지 않고 기존 유저를 반환한다")
  void findOrRegister_returnsExistingUser_whenProviderUserAlreadyExists() {
    User existingUser = googleUser("GOOGLE_123", "tester", "tester@example.com", "123");
    given(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "123"))
        .willReturn(Optional.of(existingUser));

    User result = oAuth2UserRegistrationService.findOrRegister("google", googleAttributes());

    assertThat(result).isSameAs(existingUser);
    verify(userRepository, never()).save(any(User.class));
    verifyNoInteractions(roleRepository);
  }

  @Test
  @DisplayName("신규 OAuth2 유저면 provider 정보와 ROLE_USER를 저장한다")
  void findOrRegister_registersNewUserWithProviderFieldsAndRoleUser() {
    Role roleUser = new Role("ROLE_USER");
    given(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "123"))
        .willReturn(Optional.empty());
    given(roleRepository.findByName("ROLE_USER"))
        .willReturn(Optional.of(roleUser));
    given(userRepository.save(any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    User result = oAuth2UserRegistrationService.findOrRegister("google", googleAttributes());

    assertThat(result.getUsername()).isEqualTo("GOOGLE_123");
    assertThat(result.getNickname()).isEqualTo("tester");
    assertThat(result.getEmail()).isEqualTo("tester@example.com");
    assertThat(result.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(result.getProviderId()).isEqualTo("123");
    assertThat(result.getPassword()).isNull();
    assertThat(result.getRoles())
        .extracting(Role::getName)
        .containsExactly("ROLE_USER");
    verify(userRepository).save(result);
  }

  @Test
  @DisplayName("ROLE_USER가 없으면 새 권한을 생성한 뒤 신규 OAuth2 유저에 부여한다")
  void findOrRegister_createsRoleUser_whenRoleUserDoesNotExist() {
    Role savedRole = new Role("ROLE_USER");
    given(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "123"))
        .willReturn(Optional.empty());
    given(roleRepository.findByName("ROLE_USER"))
        .willReturn(Optional.empty());
    given(roleRepository.save(any(Role.class)))
        .willReturn(savedRole);
    given(userRepository.save(any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    User result = oAuth2UserRegistrationService.findOrRegister("google", googleAttributes());

    assertThat(result.getRoles())
        .extracting(Role::getName)
        .containsExactly("ROLE_USER");
    verify(roleRepository).save(any(Role.class));
  }

  private Map<String, Object> googleAttributes() {
    return Map.of(
        "sub", "123",
        "email", "tester@example.com",
        "name", "tester"
    );
  }

  private User googleUser(String username, String nickname, String email, String providerId) {
    return User.oauthBuilder()
        .username(username)
        .nickname(nickname)
        .email(email)
        .provider(AuthProvider.GOOGLE)
        .providerId(providerId)
        .build();
  }
}
