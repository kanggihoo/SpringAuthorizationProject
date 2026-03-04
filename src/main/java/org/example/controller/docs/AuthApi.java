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
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@Tag(name = "Auth API", description = "인증 관리 API")
@ApiErrorCodeResponses
public interface AuthApi {

        @Operation(summary = "회원가입", description = "새로운 사용자를 등록하는 API입니다.<br><br>" +
                        "**[입력]**<br>" +
                        "- `username`: 회원가입에 사용할 아이디<br>" +
                        "- `password`: 사용할 비밀번호<br>" +
                        "- `nickname`: 사용자 닉네임<br>" +
                        "- `role`: 권한 (기본값 ROLE_USER)<br><br>" +
                        "**[반환]**<br>" +
                        "- 성공 시 '회원가입이 완료되었습니다.' 메시지 (String)")
        @SecurityRequirements() // 회원가입은 인증 불필요
        ResponseEntity<String> signup(@Valid @RequestBody SignupRequest signupRequest);

        @Operation(summary = "로그인", description = "아이디와 비밀번호를 검증하여 로그인하고 토큰을 발급받는 API입니다.<br><br>" +
                        "**[입력]**<br>" +
                        "- `username`: 로그인 아이디<br>" +
                        "- `password`: 로그인 비밀번호<br><br>" +
                        "**[반환]**<br>" +
                        "- JSON 응답: 발급된 **Access Token** (`accessToken`)<br>" +
                        "- HTTP 쿠키: 발급된 **Refresh Token** (`Refresh-Token`)<br><br>" +
                        "💡 **[참고사항]**<br>" +
                        "발급받은 `accessToken` 값은 Swagger 문서 우측 상단의 **[Authorize]** 버튼을 클릭하여 입력해 주어야 인증이 필요한 다른 API를 테스트할 수 있습니다.")
        @SecurityRequirements() // 로그인은 인증 불필요
        ResponseEntity<TokenResponseDto> login(
                        @Valid @RequestBody LoginRequestDto requestDto,
                        HttpServletResponse response);

        @Operation(summary = "로그아웃", description = "현재 사용자의 세션을 종료하고 토큰 정보를 삭제하는 API입니다.<br><br>" +
                        "**[입력]**<br>" +
                        "- 등록된 Access Token 및 브라우저 쿠키의 Refresh Token<br><br>" +
                        "**[반환]**<br>" +
                        "- 성공 시 '로그아웃 되었습니다.' 메시지 반환<br>" +
                        "- 복구 불가하도록 기존 쿠키에 있던 `Refresh-Token` 삭제<br><br>" +
                        "💡 **[참고사항]**<br>" +
                        "이 API를 호출하려면 Swagger 문서 우측 상단의 **[Authorize]**에 Access Token이 등록되어 있어야 합니다.")
        @SecurityRequirement(name = "bearerAuth")
        @SecurityRequirement(name = "cookieAuth")
        ResponseEntity<String> logout(
                        @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails,
                        HttpServletResponse response);

        @Operation(summary = "토큰 갱신", description = "Access Token이 만료되었을 때, 기존의 Refresh Token을 통해 새로운 Access Token과 Refresh Token을 재발급받는 API입니다.<br><br>"
                        +
                        "**[입력]**<br>" +
                        "- HTTP 쿠키에 저장된 `Refresh-Token`<br><br>" +
                        "**[반환]**<br>" +
                        "- JSON 응답: 새로 발급된 **Access Token** (`accessToken`)<br>" +
                        "- HTTP 쿠키: 새로 발급된 **Refresh Token** (`Refresh-Token` RTR 방식 적용)")
        @SecurityRequirement(name = "cookieAuth")
        ResponseEntity<TokenResponseDto> refresh(
                        HttpServletRequest request,
                        HttpServletResponse response);
}
