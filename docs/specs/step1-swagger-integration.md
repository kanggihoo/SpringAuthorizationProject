# Swagger 기반 API 문서화 및 테스트 환경 구축 명세서

## 1. 목적 및 목표 (Goals)

- `springdoc-openapi-starter-webmvc-ui`를 활용하여 API 스펙 자동 문서화 및 대화형 테스트 환경(Swagger UI)을 구축합니다.
- 비즈니스 로직(Controller)과 문서화 설정(Swagger Annotation) 코드를 철저히 분리하여, 프로덕션 코드의 가독성과 클린 코드 원칙을 유지합니다.
- JWT 기반의 Authorization 스키마(Header & Cookie)를 명확하게 지정하여, Swagger UI 내에서 인증/인가를 완벽하게 테스트할 수 있도록 지원합니다.

## 2. 핵심 정책 및 비즈니스 로직 (Core Policies)

- **보안 및 접근 제어**: 현재 로컬 및 개발 편의성에 초점을 맞추므로, 환경별 차단 정책 없이 모든 `v3/api-docs` 및 `swagger-ui` 관련 경로를 `Spring Security`에서 `permitAll()` 처리하고 JWT 필터를 우회(`shouldNotFilter()`) 시킵니다.
- **인증 스키마 (Security Scheme)**:
  1. `bearerAuth`: Access Token 검증을 위한 HTTP Bearer 헤더 인증
  2. `cookieAuth`: Refresh Token 검증을 위한 쿠키(API Key) 인증
- **전역 보안 적용**: 기본적으로 모든 엔드포인트에서 자물쇠 아이콘이 표시되도록 `bearerAuth`를 Global Security Requirement로 설정합니다.

## 3. 데이터 및 모델 구조 (Data Models)

- 기존 Request/Response DTO 파일(`SignupRequest`, `LoginRequestDto`, `TokenResponseDto` 등)에 Swagger `@Schema` 어노테이션을 적용합니다.
- 예시(Example) 데이터와 풍부한 설명(Description)을 기입하여, API 사용자가 DTO 명세를 보고 즉시 요청/응답 형태를 파악할 수 있게 합니다.

## 4. 인터페이스 명세 (Interfaces)

- **Controller Interface 분리**: `org.example.controller.docs` 등 별도의 인터페이스 전용 패키지를 구성합니다.
- `AuthApi.java` (인터페이스) 예시:
  - 클래스 상단에 `@Tag(name = "Auth API", description = "인증 관련 API")` 명시.
  - 각 기능별 `@Operation`, 반환 형식, 및 파라터를 정의.
  - Refresh 및 Logout 엔드포인트(`POST /auth/refresh`, `POST /auth/logout`)는 예외적으로 `@SecurityRequirement(name = "cookieAuth")`를 추가해 Refresh Token 쿠키를 통한 테스트를 지원합니다.
- 실제 환경의 `AuthController`는 `AuthApi`를 `implements`하며, 문서화 어노테이션이 아예 없는 순수 핵심 로직만을 유지합니다.

## 5. 핵심 컴포넌트 설계 (Components Flow)

1. **`SwaggerConfig`**: `OpenAPI` Bean 정의, 서버 정보 입력, 위에 설명한 2가지 Security Scheme(Bearer, Cookie) 세팅.
2. **`SecurityConfig` & `JwtAuthenticationFilter`**: Swagger Resource 경로 패턴(`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`) 예외 처리 추가.
3. **커스텀 예외 어노테이션 (`@ApiErrorCodeResponses`)**: 반복되는 400, 401, 403, 500 응답 등의 규격을 하나의 어노테이션에 만들어 중복 코드를 제거하고 전역적으로 재사용할 수 있도록 정의.
4. **`AuthApi` 적용**: 생성한 커스텀 오류 어노테이션(`@ApiErrorCodeResponses`) 및 정상 응답에 관한 내용을 각 메서드에 작성.

## 6. 예외 처리 정책 (Error Handling)

- 서버에서 반환되는 커스텀 예외 JSON 포맷(예: `{"status": 401, "error": "Unauthorized" ...}`)을 Swagger의 응답 스키마(`@ApiResponse`)에 정확히 일치시켜 노출합니다.
- 이를 개별로 적지 않고, 5번 항목에서 정의한 공통 커스텀 어노테이션(`@ApiErrorCodeResponses` 등)을 통해 일관되고 간결하게 예외 응답 문서화를 처리합니다.
