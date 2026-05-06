package org.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.domain.entity.AuthProvider;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("findByUsername은 username으로 사용자를 조회한다")
    void findByUsername_returnsUser() {
        // given
        Role roleUser = roleRepository.save(new Role("ROLE_USER"));
        User user = User.builder()
                .username("testuser")
                .password("encoded-password")
                .nickname("테스터")
                .build();
        user.addRole(roleUser);
        userRepository.save(user);

        // when
        var result = userRepository.findByUsername("testuser");

        // then
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getUsername()).isEqualTo("testuser");
        assertThat(result.orElseThrow().getRoles())
                .extracting(Role::getName)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("findByProviderAndProviderId는 OAuth2 사용자 정보를 조회한다")
    void findByProviderAndProviderId_returnsOauthUser() {
        // given
        User oauthUser = new User(
                "GOOGLE_123456",
                "구글유저",
                "google@example.com",
                AuthProvider.GOOGLE,
                "123456");
        userRepository.saveAndFlush(oauthUser);

        // when
        var result = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "123456");

        // then
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getEmail()).isEqualTo("google@example.com");
    }

    @Test
    @DisplayName("findByEmail은 이메일로 사용자를 조회한다")
    void findByEmail_returnsUser() {
        // given
        User oauthUser = new User(
                "GOOGLE_email-user",
                "이메일유저",
                "email@example.com",
                AuthProvider.GOOGLE,
                "email-user");
        userRepository.saveAndFlush(oauthUser);

        // when
        var result = userRepository.findByEmail("email@example.com");

        // then
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getUsername()).isEqualTo("GOOGLE_email-user");
    }

    @Test
    @DisplayName("존재하지 않는 username으로 조회하면 Optional.empty()를 반환한다")
    void findByUsername_returnsEmpty_whenUserDoesNotExist() {
        // when
        var result = userRepository.findByUsername("missing-user");

        // then
        assertThat(result).isEmpty();
    }
}
