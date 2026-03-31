package org.example.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExceptionHandlerFilter — 필터망 예외 처리")
class ExceptionHandlerFilterTest {

    @Mock
    private HandlerExceptionResolver resolver;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ExceptionHandlerFilter exceptionHandlerFilter;

    @Test
    @DisplayName("doFilterInternal: 예외가 발생하지 않으면 resolver는 호출되지 않는다")
    void doFilterInternal_callsNextFilter_whenNoException() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        exceptionHandlerFilter.doFilterInternal(request, response, filterChain);

        // then
        then(filterChain).should().doFilter(request, response);
        then(resolver).should(never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("doFilterInternal: RuntimeException 발생 시 resolver로 예외 처리를 위임한다")
    void doFilterInternal_delegatesToResolver_whenFilterChainThrows() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeException exception = new RuntimeException("Test Exception");

        willThrow(exception).given(filterChain).doFilter(request, response);

        // when
        exceptionHandlerFilter.doFilterInternal(request, response, filterChain);

        // then
        then(filterChain).should().doFilter(request, response);
        then(resolver).should().resolveException(eq(request), eq(response), isNull(), eq(exception));
    }

    @Test
    @DisplayName("doFilterInternal: ExpiredJwtException 발생 시 resolver로 예외 처리를 위임한다")
    void doFilterInternal_delegatesToResolver_forJwtException() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExpiredJwtException exception = new ExpiredJwtException(null, null, "JWT Expired");

        willThrow(exception).given(filterChain).doFilter(request, response);

        // when
        exceptionHandlerFilter.doFilterInternal(request, response, filterChain);

        // then
        then(filterChain).should().doFilter(request, response);
        then(resolver).should().resolveException(eq(request), eq(response), isNull(), eq(exception));
    }
}
