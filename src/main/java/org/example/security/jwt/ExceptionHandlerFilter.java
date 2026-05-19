package org.example.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Slf4j
@Component
public class ExceptionHandlerFilter extends OncePerRequestFilter {

  private final HandlerExceptionResolver resolver;

  // 복합 리졸버인 CompositeHandlerExceptionResolver를 주입??
  public ExceptionHandlerFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      filterChain.doFilter(request, response);
    } catch (Exception e) {
      log.error("필터 체인 예외 발생. URI: {}, 예외: {}", request.getRequestURI(), e.getMessage());
      // SpringBoot 전역핸들러인 GlobalExceptionHandler 에서 처리할 수 있도록 위임
      resolver.resolveException(request, response, null, e);
    }
  }
}
