package org.example.service;

import org.example.TestFixtures;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.dto.request.SignupRequest;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl — 회원 관리 서비스")
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserServiceImpl userService;

    private SignupRequest createSignupRequest(String username, String password, String nickname, String role) {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "username", username);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "nickname", nickname);
        ReflectionTestUtils.setField(request, "role", role);
        return request;
    }

    @Test
    @DisplayName("signup: 정상 가입 시 비밀번호를 암호화하고 기존 Role을 찾아 매핑 후 저장한다")
    void signup_savesUser_withEncodedPassword_andExistingRole() {
        // given
        SignupRequest request = createSignupRequest("newuser", "rawPassword", "NewBuddy", "ROLE_USER");
        
        given(userRepository.findByUsername("newuser")).willReturn(Optional.empty());
        given(passwordEncoder.encode("rawPassword")).willReturn("encodedPassword");
        
        Role existingRole = new Role("ROLE_USER");
        given(roleRepository.findByName("ROLE_USER")).willReturn(Optional.of(existingRole));

        // when
        userService.signup(request);

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("newuser");
        assertThat(savedUser.getPassword()).isEqualTo("encodedPassword");
        assertThat(savedUser.getNickname()).isEqualTo("NewBuddy");
        assertThat(savedUser.getRoles()).containsExactly(existingRole);
    }

    @Test
    @DisplayName("signup: DB에 Role이 없으면 새 Role을 생성하여 매핑한 뒤 저장한다")
    void signup_createsNewRole_whenRoleNotFound() {
        // given
        SignupRequest request = createSignupRequest("newuser", "rawPassword", "NewBuddy", "ROLE_NEW");
        
        given(userRepository.findByUsername("newuser")).willReturn(Optional.empty());
        given(passwordEncoder.encode("rawPassword")).willReturn("encodedPassword");
        
        given(roleRepository.findByName("ROLE_NEW")).willReturn(Optional.empty()); // Role 없음
        
        Role newRole = new Role("ROLE_NEW");
        given(roleRepository.save(any(Role.class))).willReturn(newRole);

        // when
        userService.signup(request);

        // then
        then(roleRepository).should().save(any(Role.class));
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(userCaptor.capture());
        
        assertThat(userCaptor.getValue().getRoles()).containsExactly(newRole);
    }

    @Test
    @DisplayName("signup: 중복된 username이면 RuntimeException을 던진다")
    void signup_throwsRuntimeException_onDuplicateUsername() {
        // given
        SignupRequest request = createSignupRequest("dupUser", "password", "Nick", "ROLE_USER");
        User existingUser = TestFixtures.buildUser(1L, "dupUser", "Nick", "ROLE_USER");
        
        given(userRepository.findByUsername("dupUser")).willReturn(Optional.of(existingUser));

        // when & then
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("이미 존재하는 아이디");
    }

    @Test
    @DisplayName("signup: 중복 아이디 감지 시 save 로직(비밀번호 암호화 및 유저 저장)은 절대 호출되지 않는다")
    void signup_neverCallsSave_whenDuplicateDetected() {
        // given
        SignupRequest request = createSignupRequest("dupUser", "password", "Nick", "ROLE_USER");
        User existingUser = TestFixtures.buildUser(1L, "dupUser", "Nick", "ROLE_USER");
        
        given(userRepository.findByUsername("dupUser")).willReturn(Optional.of(existingUser));

        // when
        try {
            userService.signup(request);
        } catch (RuntimeException ignored) {
            // 예상된 예외 발생
        }

        // then
        then(passwordEncoder).should(never()).encode(anyString());
        then(roleRepository).shouldHaveNoInteractions();
        then(userRepository).should(never()).save(any(User.class));
    }
}
