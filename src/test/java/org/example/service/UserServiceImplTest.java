package org.example.service;

import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.dto.request.SignupRequest;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ============================================================
 * Phase 2-1: 서비스 계층 단위 테스트 (UserServiceImpl)
 * ============================================================
 *
 * [@ExtendWith(MockitoExtension.class)]
 *   - JUnit 5에서 Mockito를 사용하기 위한 확장 기능 등록
 *   - 이 어노테이션이 있어야 @Mock, @InjectMocks 등의 어노테이션이 동작함
 *   - Spring 컨텍스트를 전혀 로드하지 않으므로 매우 빠르게 실행됨
 *
 * [@Mock]
 *   - 가짜(Mock) 객체를 생성하는 어노테이션
 *   - 실제 DB나 외부 의존성 없이 "이 메서드가 호출되면 이 값을 반환해라"라고 행동을 지정할 수 있음
 *   - 예: userRepository.findByUsername("test") 호출 시 → Optional.empty() 반환하도록 설정
 *
 * [@InjectMocks]
 *   - 테스트 대상 클래스의 인스턴스를 생성하면서, 위에서 @Mock으로 만든 가짜 객체들을
 *     자동으로 생성자에 주입해줌
 *   - 즉, UserServiceImpl의 생성자 파라미터(userRepository, roleRepository, passwordEncoder)에
 *     각각의 Mock 객체가 자동 주입됨
 *
 * [@Captor (ArgumentCaptor)]
 *   - Mock 객체의 메서드가 호출될 때 전달된 "실제 인자값"을 캡처(낚아채기)하는 도구
 *   - verify()와 함께 사용하여, save()에 실제로 어떤 User 객체가 전달되었는지 검증 가능
 *   - 단순히 "호출되었나?"를 넘어 "어떤 데이터로 호출되었나?"까지 검증할 수 있음
 *
 * [BDDMockito - given().willReturn() 패턴]
 *   - Mockito의 when().thenReturn() 대신 BDD 스타일의 given().willReturn()을 사용
 *   - Given(준비) → When(실행) → Then(검증)의 구조와 자연스럽게 어울림
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // 테스트 대상: Mock 객체들이 자동 주입된 실제 서비스 인스턴스
    @InjectMocks
    private UserServiceImpl userService;

    // save()에 전달되는 User 객체를 캡처하기 위한 ArgumentCaptor
    @Captor
    private ArgumentCaptor<User> userCaptor;

    /**
     * 회원가입 요청 DTO를 생성하는 헬퍼 메서드
     * 여러 테스트에서 반복 사용되는 DTO 생성 로직을 분리
     */
    private SignupRequest createSignupRequest(String username, String password, String nickname) {
        SignupRequest request = new SignupRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setNickname(nickname);
        return request;
    }

    @Test
    @DisplayName("정상 회원가입: 비밀번호 인코딩, 닉네임, Role 매핑까지 ArgumentCaptor로 검증")
    void signup_Success_WithArgumentCaptor() {
        // ============================================================
        // [Given] Mock 객체들의 행동을 사전에 정의
        // ============================================================
        SignupRequest request = createSignupRequest("testuser", "password123", "Tester");

        // "testuser"로 조회하면 → 아무도 없음 (중복 아님)
        given(userRepository.findByUsername("testuser")).willReturn(Optional.empty());

        // 비밀번호 인코딩 시 → 고정된 해시값 반환 (실제 BCrypt 대신 예측 가능한 값 사용)
        given(passwordEncoder.encode("password123")).willReturn("$2a$10$encodedPassword");

        // "ROLE_USER"로 Role 조회 시 → 이미 존재하는 Role 반환
        Role existingRole = new Role("ROLE_USER");
        given(roleRepository.findByName("ROLE_USER")).willReturn(Optional.of(existingRole));

        // userRepository.save() 호출 시 → 전달받은 객체를 그대로 반환
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // ============================================================
        // [When] 실제 테스트 대상 메서드 실행
        // ============================================================
        userService.signup(request);

        // ============================================================
        // [Then] ArgumentCaptor로 save()에 전달된 User 객체를 캡처하여 상세 검증
        // ============================================================

        // verify(): userRepository.save()가 정확히 1번 호출되었는지 확인
        // capture(): 그때 전달된 User 인자를 캡처
        verify(userRepository).save(userCaptor.capture());

        // 캡처된 User 객체 꺼내기
        User capturedUser = userCaptor.getValue();

        // ★ 핵심 검증 1: 비밀번호가 원문("password123")이 아닌 인코딩된 문자열인지
        assertThat(capturedUser.getPassword()).isEqualTo("$2a$10$encodedPassword");
        assertThat(capturedUser.getPassword()).isNotEqualTo("password123"); // 원문이 저장되면 안 됨!

        // ★ 핵심 검증 2: 닉네임이 정확히 전달되었는지
        assertThat(capturedUser.getNickname()).isEqualTo("Tester");

        // ★ 핵심 검증 3: username이 정확히 세팅되었는지
        assertThat(capturedUser.getUsername()).isEqualTo("testuser");

        // ★ 핵심 검증 4: Role이 정상적으로 매핑되었는지
        assertThat(capturedUser.getRoles()).hasSize(1);
        assertThat(capturedUser.getRoles())
                .extracting("name")
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("중복 아이디 회원가입 시 RuntimeException 발생")
    void signup_DuplicateUsername_ThrowsException() {
        // ============================================================
        // [Given] 이미 동일한 username이 DB에 존재하는 상황을 시뮬레이션
        // ============================================================
        SignupRequest request = createSignupRequest("existingUser", "password123", "Dup");

        // "existingUser"로 조회하면 → 이미 존재하는 유저가 반환됨
        User existingUser = User.builder()
                .username("existingUser")
                .password("encoded")
                .nickname("Existing")
                .build();
        given(userRepository.findByUsername("existingUser")).willReturn(Optional.of(existingUser));

        // ============================================================
        // [When & Then] 예외 발생 검증
        // ============================================================

        // assertThatThrownBy: 람다 안의 코드 실행 시 예외가 발생하는지 검증
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(RuntimeException.class)           // 예외 타입 확인
                .hasMessageContaining("이미 존재하는 아이디");    // 예외 메시지 확인

        // 중복이면 save()가 절대 호출되면 안 됨
        // never(): 해당 메서드가 한 번도 호출되지 않았음을 검증
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("기존 Role이 없을 시 새로 생성하여 저장하는 분기 검증")
    void signup_RoleNotExist_CreatesNewRole() {
        // ============================================================
        // [Given] Role 테이블에 "ROLE_USER"가 아직 없는 상황
        // ============================================================
        SignupRequest request = createSignupRequest("newuser", "password123", "NewUser");

        given(userRepository.findByUsername("newuser")).willReturn(Optional.empty());
        given(passwordEncoder.encode("password123")).willReturn("$2a$10$encoded");

        // "ROLE_USER"로 조회 시 → 없음 (Optional.empty())
        given(roleRepository.findByName("ROLE_USER")).willReturn(Optional.empty());

        // roleRepository.save()가 호출되면 → 전달받은 Role 객체를 그대로 반환
        // ★ 이 부분이 핵심: findByName()이 empty일 때 orElseGet()에 의해 save()가 호출됨
        given(roleRepository.save(any(Role.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // ============================================================
        // [When] 회원가입 실행
        // ============================================================
        userService.signup(request);

        // ============================================================
        // [Then] roleRepository.save()가 호출되었는지 검증
        // - 기존 Role이 없으므로 새로 생성하여 저장하는 로직이 실행되어야 함
        // ============================================================
        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());

        Role capturedRole = roleCaptor.getValue();
        assertThat(capturedRole.getName()).isEqualTo("ROLE_USER");
    }
}
