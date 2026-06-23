package org.example.security.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.example.security.audit.SecurityAuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountLockServiceImplTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private SecurityAuditService securityAuditService;

  @InjectMocks
  private AccountLockServiceImpl accountLockService;

  @Test
  @DisplayName("lockForLoginFailure locks User and records ACCOUNT_LOCKED audit")
  void lockForLoginFailure_locksUserAndRecordsAudit() {
    User user = user("testuser");
    given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));

    accountLockService.lockForLoginFailure("testuser");

    assertThat(user.isAccountNonLocked()).isFalse();
    verify(securityAuditService).recordAccountLocked("testuser");
  }

  @Test
  @DisplayName("lockForLoginFailure throws USER_NOT_FOUND when User is missing")
  void lockForLoginFailure_throwsUserNotFound_whenUserIsMissing() {
    given(userRepository.findByUsername("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> accountLockService.lockForLoginFailure("missing"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.USER_NOT_FOUND));
  }

  private User user(String username) {
    return User.builder()
        .username(username)
        .password("encoded")
        .nickname("tester")
        .build();
  }
}

