package org.example.security.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.security.CustomUserDetails;
import org.example.security.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserServiceImplTest {

  @Mock
  private CustomUserDetailsService customUserDetailsService;

  private AuthenticatedUserServiceImpl authenticatedUserService;

  @BeforeEach
  void setUp() {
    authenticatedUserService = new AuthenticatedUserServiceImpl(customUserDetailsService);
  }

  @Test
  @DisplayName("Returns an active Authenticated User for an enabled and unlocked JWT Subject")
  void findActiveUserByJwtSubject_returnsUser_whenUserIsActive() {
    CustomUserDetails userDetails = userDetails("testuser", true, true);
    given(customUserDetailsService.loadUserByUsername("testuser")).willReturn(userDetails);

    assertThat(authenticatedUserService.findActiveUserByJwtSubject("testuser"))
        .hasValue(userDetails);
    verify(customUserDetailsService).loadUserByUsername("testuser");
  }

  @Test
  @DisplayName("Returns empty when the JWT Subject belongs to a locked User")
  void findActiveUserByJwtSubject_returnsEmpty_whenUserIsLocked() {
    CustomUserDetails userDetails = userDetails("testuser", true, false);
    given(customUserDetailsService.loadUserByUsername("testuser")).willReturn(userDetails);

    assertThat(authenticatedUserService.findActiveUserByJwtSubject("testuser")).isEmpty();
  }

  @Test
  @DisplayName("Returns empty when the JWT Subject belongs to a disabled User")
  void findActiveUserByJwtSubject_returnsEmpty_whenUserIsDisabled() {
    CustomUserDetails userDetails = userDetails("testuser", false, true);
    given(customUserDetailsService.loadUserByUsername("testuser")).willReturn(userDetails);

    assertThat(authenticatedUserService.findActiveUserByJwtSubject("testuser")).isEmpty();
  }

  private CustomUserDetails userDetails(
      String username, boolean enabled, boolean accountNonLocked) {
    User user = User.builder()
        .username(username)
        .password("encoded-password")
        .nickname("tester")
        .build();
    ReflectionTestUtils.setField(user, "enabled", enabled);
    ReflectionTestUtils.setField(user, "accountNonLocked", accountNonLocked);
    user.addRole(new Role("ROLE_USER"));
    return new CustomUserDetails(user);
  }
}
