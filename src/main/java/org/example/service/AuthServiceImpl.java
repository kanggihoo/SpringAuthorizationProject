package org.example.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.response.TokenResponseDto;
import org.example.security.authenticated.AuthenticatedUser;
import org.example.security.token.TokenLifecycleService;
import org.springframework.security.authentication.AuthenticationManager;
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

  @Override
  public TokenResponseDto login(LoginRequestDto requestDto) {
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            requestDto.getUsername(),
            requestDto.getPassword()));

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
