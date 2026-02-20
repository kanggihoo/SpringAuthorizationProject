package org.example.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원가입 요청 데이터 구조
 */
@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {

  @NotBlank(message = "아이디는 필수 입력 값입니다.")
  @Size(min = 4, max = 20, message = "아이디는 4자 이상 20자 이하로 입력해주세요.")
  private String username;

  @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
  @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
  private String password;

  @NotBlank(message = "닉네임은 필수 입력 값입니다.")
  private String nickname;

  // 기본값은 USER로 설정
  private String role = "ROLE_USER";
}
