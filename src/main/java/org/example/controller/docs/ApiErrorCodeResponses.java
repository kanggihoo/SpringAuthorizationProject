package org.example.controller.docs;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 공통 예외 응답(400, 401, 403, 500 등)을 Swagger에 전역적으로 적용하기 위한 메타 어노테이션
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (Bad Request)",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\n  \"status\": 400,\n  \"error\": \"Bad Request\",\n  \"message\": \"잘못된 입력값입니다.\",\n  \"path\": \"/api/some-path\"\n}"))),
    @ApiResponse(responseCode = "401", description = "인증 실패 (Unauthorized)",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\n  \"status\": 401,\n  \"error\": \"Unauthorized\",\n  \"message\": \"유효하지 않은 토큰입니다.\",\n  \"path\": \"/api/some-path\"\n}"))),
    @ApiResponse(responseCode = "403", description = "권한 없음 (Forbidden)",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\n  \"status\": 403,\n  \"error\": \"Forbidden\",\n  \"message\": \"접근 권한이 없습니다.\",\n  \"path\": \"/api/some-path\"\n}")))
})
public @interface ApiErrorCodeResponses {
}
