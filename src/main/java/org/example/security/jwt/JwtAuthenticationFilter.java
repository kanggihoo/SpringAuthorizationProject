package org.example.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.example.security.authenticated.AuthenticatedUser;
import org.example.security.authenticated.AuthenticatedUserService;
import org.example.security.token.TokenLifecycleService;
import org.example.security.token.delivery.TokenDeliveryService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates Bearer Access Tokens for Protected API requests.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;
  private final AuthenticatedUserService authenticatedUserService;
  private final TokenLifecycleService tokenLifecycleService;
  private final TokenDeliveryService tokenDeliveryService;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/login") || path.startsWith("/signup") || path.startsWith("/refresh")
        || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
        || path.startsWith("/oauth2/authorization")
        || path.startsWith("/login/oauth2");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String token = tokenDeliveryService.resolveBearerAccessToken(request).orElse(null);

    if (token != null && jwtTokenProvider.validateToken(token)) {
      if (!tokenLifecycleService.isAccessTokenAllowed(token)) {
        filterChain.doFilter(request, response);
        return;
      }

      Claims claims = jwtTokenProvider.parseClaims(token);
      String username = claims.getSubject();

      AuthenticatedUser authenticatedUser = authenticatedUserService
          .findActiveUserByJwtSubject(username)
          .orElse(null);
      if (authenticatedUser == null) {
        filterChain.doFilter(request, response);
        return;
      }

      UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
          authenticatedUser, null, authenticatedUser.getAuthorities());

      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
  }
}
