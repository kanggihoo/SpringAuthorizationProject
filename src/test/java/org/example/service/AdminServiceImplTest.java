package org.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.account.LoginFailureCounter;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private LoginFailureCounter loginFailureCounter;

  @InjectMocks
  private AdminServiceImpl adminService;

  @Test
  @DisplayName("unlockUser unlocks a locked User and clears login failures")
  void unlockUser_unlocksLockedUser() {
    User user = user("testuser");
    user.lock();
    given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));

    adminService.unlockUser("testuser");

    assertThat(user.isAccountNonLocked()).isTrue();
    verify(loginFailureCounter).clear("testuser");
  }

  @Test
  @DisplayName("unlockUser throws USER_NOT_FOUND when User is missing")
  void unlockUser_throwsUserNotFound_whenUserIsMissing() {
    given(userRepository.findByUsername("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminService.unlockUser("missing"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.USER_NOT_FOUND));

    verify(loginFailureCounter, never()).clear("missing");
  }

  private User user(String username) {
    return User.builder()
        .username(username)
        .password("encoded")
        .nickname("tester")
        .build();
  }
}
