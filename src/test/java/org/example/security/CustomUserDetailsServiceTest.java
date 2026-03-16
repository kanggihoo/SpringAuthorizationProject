package org.example.security;

import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * ============================================================
 * Phase 2-2: CustomUserDetailsService 단위 테스트
 * ============================================================
 *
 * [테스트 대상]
 *   CustomUserDetailsService.loadUserByUsername()
 *   - Spring Security가 폼 로그인 시 내부적으로 호출하는 메서드
 *   - DaoAuthenticationProvider → CustomUserDetailsService.loadUserByUsername() 순서로 호출됨
 *   - DB에서 User를 조회하여 CustomUserDetails(UserDetails 구현체)로 변환하여 반환
 *
 * [이 테스트의 목적]
 *   1. 성공 케이스: DB에 유저가 있을 때 올바른 UserDetails가 반환되는지
 *   2. 실패 케이스: DB에 유저가 없을 때 UsernameNotFoundException이 발생하는지
 *   - step1-internal-flow.md의 "2.1 인증 성공"과 "2.2 인증 실패" 시나리오에 대응
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("존재하는 username으로 조회 시 올바른 UserDetails 반환")
    void loadUserByUsername_Success() {
        // ============================================================
        // [Given] DB에 유저가 존재하는 상황 시뮬레이션
        // ============================================================

        // Role과 User 엔티티를 직접 구성
        Role role = new Role("ROLE_USER");
        User user = User.builder()
                .username("testuser")
                .password("$2a$10$encodedPassword")
                .nickname("Tester")
                .build();
        user.addRole(role);

        // userRepository.findByUsername("testuser") 호출 시 → 위에서 만든 User 반환
        given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));

        // ============================================================
        // [When] loadUserByUsername() 실행
        // - Spring Security의 DaoAuthenticationProvider가 내부적으로 호출하는 메서드
        // ============================================================
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("testuser");

        // ============================================================
        // [Then] 반환된 UserDetails의 각 필드가 원본 User 데이터와 일치하는지 검증
        // ============================================================

        // 기본 인증 정보 검증
        assertThat(userDetails.getUsername()).isEqualTo("testuser");
        assertThat(userDetails.getPassword()).isEqualTo("$2a$10$encodedPassword");

        // CustomUserDetails로 캐스팅하여 커스텀 필드(nickname) 검증
        // - @AuthenticationPrincipal로 컨트롤러에서 꺼내 쓸 때 이 값이 사용됨
        assertThat(userDetails).isInstanceOf(CustomUserDetails.class);
        CustomUserDetails customDetails = (CustomUserDetails) userDetails;
        assertThat(customDetails.getNickname()).isEqualTo("Tester");

        // 계정 상태 검증 (기본값 true)
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();

        // 권한(GrantedAuthority) 검증
        // - Role 엔티티의 name("ROLE_USER")이 SimpleGrantedAuthority로 변환되었는지 확인
        // - SecurityConfig의 hasRole("USER")은 내부적으로 "ROLE_USER"를 기대함
        assertThat(userDetails.getAuthorities())
                .hasSize(1)
                .extracting("authority")  // GrantedAuthority에서 문자열 추출
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("존재하지 않는 username으로 조회 시 UsernameNotFoundException 발생")
    void loadUserByUsername_NotFound_ThrowsException() {
        // ============================================================
        // [Given] DB에 해당 유저가 없는 상황
        // - step1-internal-flow.md "2.2 인증 실패 시나리오" 중
        //   "DB에 찾는 회원이 없는 경우" 에 해당
        // ============================================================
        given(userRepository.findByUsername("nonexistent")).willReturn(Optional.empty());

        // ============================================================
        // [When & Then] 예외 발생 검증
        // - UsernameNotFoundException은 AuthenticationException의 하위 클래스
        // - 이 예외가 발생하면 AuthenticationFailureHandler가 실패 처리를 담당함
        // ============================================================
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("nonexistent"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("nonexistent");  // 예외 메시지에 username이 포함되는지
    }
}
