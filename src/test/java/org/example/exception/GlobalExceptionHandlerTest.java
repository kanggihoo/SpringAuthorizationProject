package org.example.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("BadCredentialsException 은 401 응답을 반환한다")
    void handleBadCredentialsException_returnsUnauthorized() {
        ResponseEntity<Map<String, String>> response =
            globalExceptionHandler.handleBadCredentialsException(new BadCredentialsException("bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Unauthorized");
        assertThat(response.getBody()).containsEntry("message", "아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("LockedException 은 423 응답을 반환한다")
    void handleLockedException_returnsLocked() {
        ResponseEntity<Map<String, String>> response =
            globalExceptionHandler.handleLockedException(new LockedException("locked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(response.getBody()).containsEntry("error", "Locked");
        assertThat(response.getBody()).containsEntry("message", "계정이 잠겼습니다. 관리자에게 문의하세요.");
    }

    @Test
    @DisplayName("DisabledException 은 403 응답을 반환한다")
    void handleDisabledException_returnsForbidden() {
        ResponseEntity<Map<String, String>> response =
            globalExceptionHandler.handleDisabledException(new DisabledException("disabled"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "Forbidden");
        assertThat(response.getBody()).containsEntry("message", "비활성화된 계정입니다.");
    }

    @Test
    @DisplayName("기존 RuntimeException 은 400 응답을 유지한다")
    void handleRuntimeException_returnsBadRequest() {
        ResponseEntity<String> response =
            globalExceptionHandler.handleRuntimeException(new RuntimeException("runtime"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("runtime");
    }
}
