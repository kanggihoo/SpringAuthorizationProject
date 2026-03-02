package org.example.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.Getter;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증 토큰 응답 데이터")
@Getter
@Builder
public class TokenResponseDto {
  @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
  private String accessToken;
  
  @Schema(description = "토큰 타입", example = "Bearer")
  private String tokenType;
  

  @JsonIgnore
  private String refreshToken;
}
