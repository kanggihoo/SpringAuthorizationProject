package org.example.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.request.SignupRequest;
import org.example.dto.response.TokenResponseDto;
import org.example.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Auth API", description = "인증 관 API")
@ApiErrorCodeResponses
public interface AuthApi {

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements() // 회원가입은 인증 불필요
    ResponseEntity<String> signup(@Valid @RequestBody SignupRequest signupRequest);

    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인하여 Access/Refresh 토큰을 발급받습니다.")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements() // 로그인은 인증 불필요
    ResponseEntity<TokenResponseDto> login(
            @Valid @RequestBody LoginRequestDto requestDto,
            HttpServletResponse response);

    @Operation(summary = "로그아웃", description = "로그아웃을 수행하고 Refresh 토큰 쿠키를 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "cookieAuth")
    ResponseEntity<String> logout(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails,
            HttpServletResponse response);

    @Operation(summary = "토큰 갱신", description = "쿠키의 Refresh 토큰을 사용하여 새로운 Access/Refresh 토큰을 발급받습니다.")
    @SecurityRequirement(name = "cookieAuth")
    ResponseEntity<TokenResponseDto> refresh(
            HttpServletRequest request,
            HttpServletResponse response);
}
