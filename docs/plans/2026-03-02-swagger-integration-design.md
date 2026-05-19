# Swagger 연동 설계 (Swagger Integration Design)

## 1. 개요 (Overview)

`springdoc-openapi-starter-webmvc-ui`를 Spring Boot 프로젝트에 통합하여 API 문서를 자동 생성하고 대화형 테스트 환경을 제공합니다. 이 설계는 비즈니스 로직의 명확성과 가독성을 유지하기 위해, 실제 Controller 구현부에서 Java 인터페이스(Interface)를 활용해 Swagger 어노테이션들을 분리하는 것을 핵심으로 합니다.

## 2. 설정 (Configuration) (`SwaggerConfig`)

- `OpenAPI` 빈(Bean)을 구성하기 위해 `@Configuration` 클래스를 정의합니다.
- 다음 2가지 `SecurityScheme`을 생성합니다:
  1. **Bearer Authentication** (`bearerAuth`): `Authorization` 헤더를 통해 전달되는 Access Token용.
  2. **Cookie Authentication** (`cookieAuth`): 쿠키(`Refresh-Token`)로 전달되는 Refresh Token용.
- 기본적으로 모든 엔드포인트에 자물쇠 아이콘이 표시되도록 전역(global) Security Requirement로 `bearerAuth`를 적용합니다.

## 3. Spring Security 통합 (Spring Security Integration)

- `SecurityConfig.java`를 수정하여 Swagger 관련 엔드포인트 접근을 명시적으로 허용(`permitAll`)합니다:
  - `/v3/api-docs/**`
  - `/swagger-ui/**`
  - `/swagger-ui.html`
- 불필요한 JWT 파싱이나 401(Unauthorized) 예외 발생을 막기 위해, `JwtAuthenticationFilter`의 예외 경로(`shouldNotFilter()`)에 위 경로들을 추가합니다.

## 4. 어노테이션 분리 (인터페이스 기반 Controller)

- Swagger 인터페이스들만 모아둘 분리된 패키지 `controller.docs`를 생성합니다.
- **`AuthApi` 인터페이스 (`controller/docs/AuthApi.java`)**:
  - `@Tag(name = "Auth API")` 어노테이션을 적용합니다.
  - `signup`, `login`, `logout`, `refresh` 엔드포인트들에 대한 `@Operation`, `@ApiResponses`, `@Parameter`를 정의합니다.
  - Refresh 토큰 쿠키 테스트가 가능하도록, `/refresh`와 `/logout` API에는 `bearerAuth`와 더불어 명시적으로 `@SecurityRequirement(name = "cookieAuth")`를 추가합니다.
- 기존 `AuthController`가 `AuthApi`를 `implements` 하도록 업데이트하고 코드를 복잡하게 만드는 인라인 문서화 어노테이션은 구현부에서 모두 제거합니다.

## 5. 예외 처리 & 응답 구조 통합 (Exception Handling)

- 기존의 커스텀 에러 응답 구조를 Swagger의 `@ApiResponses`에 직접 매핑합니다.
- `AuthApi` 적용 예시:
  - **401 Unauthorized**: `CustomAuthenticationEntryPoint` 또는 `GlobalExceptionHandler`(`JwtException`)에서 처리. `{"status": 401, "error": "Unauthorized", "message": "...", "path": "..."}` 스키마 형태로 `@ApiResponse`에 반영.
  - **403 Forbidden**: `CustomAccessDeniedHandler`에서 처리. `{"status": 403, "error": "Forbidden", "message": "...", "path": "..."}` 스키마 형태로 `@ApiResponse`에 반영.
  - **400 Bad Request / 422 Unprocessable Content**: `GlobalExceptionHandler` 처리 내용 매핑.

## 6. DTO 스키마 정의 (DTO Schema Definitions)

- Request(요청) 및 Response(응답) DTO에 `@Schema` 어노테이션을 추가합니다.
- `SignupRequest`, `LoginRequestDto`, `TokenResponseDto` 모델에 대해 읽기 쉬운 설명(description)과 예제 데이터(example)를 제공하여 Swagger UI에서 확인할 수 있도록 설정합니다.

## 7. 구현으로의 전환 (Migration to Implementation)

- 이 설계를 검증한 이후, `writing-plans` 스킬을 호출하여 단계별 세부 코드 구현 계획을 수립합니다.
