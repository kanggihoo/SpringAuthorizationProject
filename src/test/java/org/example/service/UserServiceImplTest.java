package org.example.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.dto.request.SignupRequest;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * UserServiceImpl 순수 단위 테스트.
 *
 * <p>Spring Context 없이 Mockito만 사용하여 비즈니스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userServiceImpl;

    @Test
    @DisplayName("회원가입 시 role 필드에 ROLE_ADMIN을 넣어도 ROLE_USER로 강제된다")
    void signup_roleIsAlwaysRoleUser_whenAdminRequested() {
        // given
        SignupRequest request = new SignupRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setNickname("테스트유저");
        // role 필드가 제거되었으므로 클라이언트가 role을 지정할 수 없음

        Role roleUser = new Role("ROLE_USER");
        given(userRepository.findByUsername("testuser")).willReturn(Optional.empty());
        given(passwordEncoder.encode("password123")).willReturn("encodedPassword");
        given(roleRepository.findByName("ROLE_USER")).willReturn(Optional.of(roleUser));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        userServiceImpl.signup(request);

        // then: ROLE_USER로 조회했는지 검증 (ROLE_ADMIN이 아님)
        verify(roleRepository).findByName(eq("ROLE_USER"));
        verify(roleRepository, never()).findByName(eq("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("회원가입 시 roleRepository.findByName(ROLE_USER)가 반드시 호출된다")
    void signup_callsFindByNameWithRoleUser() {
        // given
        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setNickname("새유저");
        // role을 명시하지 않아도 (기본값 포함) ROLE_USER여야 함

        Role roleUser = new Role("ROLE_USER");
        given(userRepository.findByUsername("newuser")).willReturn(Optional.empty());
        given(passwordEncoder.encode(any())).willReturn("encodedPassword");
        given(roleRepository.findByName("ROLE_USER")).willReturn(Optional.of(roleUser));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        userServiceImpl.signup(request);

        // then
        verify(roleRepository).findByName("ROLE_USER");
    }

    @Test
    @DisplayName("이미 존재하는 username으로 회원가입 시 RuntimeException이 발생한다")
    void signup_throwsException_whenUsernameAlreadyExists() {
        // given
        SignupRequest request = new SignupRequest();
        request.setUsername("existinguser");
        request.setPassword("password123");
        request.setNickname("기존유저");

        User existingUser = User.builder()
            .username("existinguser")
            .password("encoded")
            .nickname("기존유저")
            .build();
        given(userRepository.findByUsername("existinguser"))
            .willReturn(Optional.of(existingUser));

        // when & then
        assertThatThrownBy(() -> userServiceImpl.signup(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("이미 존재하는 아이디");
    }
}
