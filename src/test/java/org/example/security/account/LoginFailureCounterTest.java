package org.example.security.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.example.repository.LoginFailureRedisRepository;
import org.example.security.token.RedisFailurePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginFailureCounterTest {

  @Mock
  private LoginFailureRedisRepository repository;

  private LoginFailureCounter counter;

  @BeforeEach
  void setUp() {
    counter = new LoginFailureCounter(repository, new RedisFailurePolicy());
  }

  @Test
  @DisplayName("recordFailure returns false before threshold")
  void recordFailure_returnsFalse_beforeThreshold() {
    given(repository.incrementFailure("testuser", Duration.ofMinutes(30)))
        .willReturn(4L);

    boolean lockRequired = counter.recordFailure("testuser");

    assertThat(lockRequired).isFalse();
  }

  @Test
  @DisplayName("recordFailure returns true at threshold")
  void recordFailure_returnsTrue_atThreshold() {
    given(repository.incrementFailure("testuser", Duration.ofMinutes(30)))
        .willReturn(5L);

    boolean lockRequired = counter.recordFailure("testuser");

    assertThat(lockRequired).isTrue();
  }

  @Test
  @DisplayName("clear delegates to repository")
  void clear_delegatesToRepository() {
    counter.clear("testuser");

    verify(repository).clearFailure("testuser");
  }
}
