package org.example.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponseDto {
  private String accessToken;
  private String tokenType;

  @JsonIgnore
  private String refreshToken;
}
