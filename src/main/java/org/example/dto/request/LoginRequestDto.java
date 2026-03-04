package org.example.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 요청 데이터")
@Getter
@NoArgsConstructor
public class LoginRequestDto {
  @Schema(description = "사용자 로그인 아이디", example = "testuser")
  @NotBlank(message = "아이디를 입력해주세요.")
  private String username;

  @Schema(description = "사용자 비밀번호", example = "SecurePass123!")
  @NotBlank(message = "비밀번호를 입력해주세요.")
  private String password;
}
