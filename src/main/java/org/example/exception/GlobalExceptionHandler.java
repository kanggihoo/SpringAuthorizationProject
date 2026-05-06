package org.example.exception;

import io.jsonwebtoken.JwtException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<Map<String, String>> handleBadCredentialsException(BadCredentialsException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(errorResponse("Unauthorized", "아이디 또는 비밀번호가 올바르지 않습니다."));
  }

  @ExceptionHandler(LockedException.class)
  public ResponseEntity<Map<String, String>> handleLockedException(LockedException ex) {
    return ResponseEntity.status(HttpStatus.LOCKED)
        .body(errorResponse("Locked", "계정이 잠겼습니다. 관리자에게 문의하세요."));
  }

  @ExceptionHandler(DisabledException.class)
  public ResponseEntity<Map<String, String>> handleDisabledException(DisabledException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(errorResponse("Forbidden", "비활성화된 계정입니다."));
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(errors);
  }

  @ExceptionHandler(JwtException.class)
  public ResponseEntity<Map<String, String>> handleJwtExceptions(JwtException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(errorResponse("Invalid Token", ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleException(Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 내부 오류가 발생했습니다.");
  }

  private Map<String, String> errorResponse(String error, String message) {
    Map<String, String> errors = new HashMap<>();
    errors.put("error", error);
    errors.put("message", message);
    return errors;
  }
}
