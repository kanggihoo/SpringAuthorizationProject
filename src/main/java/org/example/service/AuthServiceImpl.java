package org.example.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.User;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.response.TokenResponseDto;
import org.example.repository.UserRepository;
import org.example.security.account.LoginFailureCounter;
import org.example.security.authenticated.AuthenticatedUser;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.example.security.token.TokenLifecycleService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Handles local authentication and delegates token lifecycle policy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final AuthenticationManager authenticationManager;
  private final TokenLifecycleService tokenLifecycleService;
  private final UserRepository userRepository;
  private final LoginFailureCounter loginFailureCounter;

  @Override
  public TokenResponseDto login(LoginRequestDto requestDto) {
    String username = requestDto.getUsername();
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new AuthFailureException(
            AuthFailureCode.BAD_CREDENTIALS,
            "Invalid username or password."));

    if (!user.isAccountNonLocked()) {
      throw new AuthFailureException(
          AuthFailureCode.ACCOUNT_LOCKED,
          "User account is locked.");
    }

    Authentication authentication;
    try {
      authentication = authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(
              username,
              requestDto.getPassword()));
    } catch (BadCredentialsException e) {
      if (loginFailureCounter.recordFailure(username)) {
        user.lock();
        userRepository.save(user);
        throw new AuthFailureException(
            AuthFailureCode.ACCOUNT_LOCKED,
            "User account is locked after too many login failures.",
            e);
      }
      throw new AuthFailureException(
          AuthFailureCode.BAD_CREDENTIALS,
          "Invalid username or password.",
          e);
    }

    loginFailureCounter.clear(username);

    AuthenticatedUser userDetails = (AuthenticatedUser) authentication.getPrincipal();
    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    return tokenLifecycleService.issue(userDetails.getJwtSubject(), roles);
  }

  @Override
  public void logout(String username, String accessToken) {
    tokenLifecycleService.logout(username, accessToken);
  }

  @Override
  public TokenResponseDto refresh(String refreshToken) {
    return tokenLifecycleService.rotate(refreshToken);
  }
}
