package org.example.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원가입 요청 데이터 구조
 */
@Schema(description = "회원가입 요청 데이터")
@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {

  @Schema(description = "사용자 로그인용 아이디", example = "testuser")
  @NotBlank(message = "아이디는 필수 입력 값입니다.")
  @Size(min = 4, max = 20, message = "아이디는 4자 이상 20자 이하로 입력해주세요.")
  private String username;

  @Schema(description = "사용자 비밀번호", example = "SecurePass123!")
  @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
  @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
  private String password;

  @Schema(description = "사용자 닉네임", example = "테스트유저")
  @NotBlank(message = "닉네임은 필수 입력 값입니다.")
  private String nickname;

  // 기본값은 USER로 설정
  @Schema(description = "사용자 권한 명", example = "ROLE_USER", defaultValue = "ROLE_USER")
  private String role = "ROLE_USER";
}
