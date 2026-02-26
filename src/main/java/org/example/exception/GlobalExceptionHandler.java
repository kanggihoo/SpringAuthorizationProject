package org.example.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import io.jsonwebtoken.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기 => 이후에 동일한 형식으로 반환하면 좋을듯?
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 비즈니스 로직 예외 처리(400에러)
   */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
  }

  /**
   * 입력값 검증(@Valid) 예외 처리(422)
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(errors);
  }

  /**
   * JWT 관련 인증(토큰) 예외 처리(401) = > 403 에러가 없네??
   */
  @ExceptionHandler(JwtException.class)
  public ResponseEntity<Map<String, String>> handleJwtExceptions(io.jsonwebtoken.JwtException ex) {
    Map<String, String> errors = new HashMap<>();
    errors.put("error", "Invalid Token");
    errors.put("message", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errors);
  }

  /**
   * 일반적인 예외 처리
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleException(Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 내부 오류가 발생했습니다.");
  }
}
