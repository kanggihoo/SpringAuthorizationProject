package org.example.security;

import org.example.TestFixtures;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService — DB 사용자 조회 서비스")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("loadUserByUsername: 존재하는 username이면 CustomUserDetails를 반환한다")
    void loadUserByUsername_returnsCustomUserDetails_whenFound() {
        // given
        String username = "testuser";
        User mockUser = TestFixtures.buildUser(1L, username, "TestNick", "ROLE_USER");
        given(userRepository.findByUsername(username)).willReturn(Optional.of(mockUser));

        // when
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

        // then
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(username);
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(((CustomUserDetails) userDetails).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("loadUserByUsername: 존재하지 않는 username이면 UsernameNotFoundException을 던진다")
    void loadUserByUsername_throwsUsernameNotFoundException_whenNotFound() {
        // given
        String username = "unknownuser";
        given(userRepository.findByUsername(anyString())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername(username))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining(username);
    }

    @Test
    @DisplayName("loadUserByUsername: 계정 잠금 상태(accountNonLocked)가 CustomUserDetails에 올바르게 반영된다")
    void loadUserByUsername_reflectsAccountLockFlag() {
        // given
        String username = "lockeduser";
        User mockUser = TestFixtures.buildUser(2L, username, "LockedNick", "ROLE_USER");
        ReflectionTestUtils.setField(mockUser, "accountNonLocked", false); // 잠금 처리
        given(userRepository.findByUsername(username)).willReturn(Optional.of(mockUser));

        // when
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

        // then
        assertThat(userDetails.isAccountNonLocked()).isFalse();
    }
}
