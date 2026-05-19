# Architectural Decision Records (ADRs)

프로젝트에서 합의된 중요한 설계 결정 사항들입니다. AI는 코드를 제안할 때 이 결정들을 임의로 번복해서는 안 됩니다.

## 1. 테스트 환경 (TestContainers)
- **결정**: 통합 테스트/DB 테스트 시 H2와 같은 인메모리 DB를 사용하지 않고, **TestContainers**를 사용하여 실제 배포 환경(PostgreSQL, Redis)과 100% 동일한 환경에서 테스트합니다.
- **근거**: 운영 환경과 테스트 환경의 파편화를 방지하고, 특정 DB 벤더에 종속적인 쿼리나 제약조건 로직도 정확하게 검증하기 위함입니다.

## 2. Refresh Token Rotation (RTR)
- **결정**: `/refresh` 엔드포인트를 통해 Access Token을 재발급받을 때, 기존 Refresh Token도 즉시 폐기하고 **새로운 Refresh Token으로 함께 재발급**하여 쿠키로 덮어씁니다.
- **근거**: RT가 탈취되더라도 공격자가 영구적으로 세션을 유지할 수 없도록 방어하는 핵심 보안 패턴입니다.

## 3. 예외 처리 위임 (Exception Delegation)
- **결정 1 (JWT 예외)**: JWT 관련 필터(`JwtAuthenticationFilter`) 등에서 발생하는 토큰 파싱 에러/만료 에러는 필터 안에서 직접 JSON 응답을 만들지 않습니다. 대신 Spring Boot의 전역 예외 처리기인 `GlobalExceptionHandler`(`@RestControllerAdvice`)에서 통합 처리할 수 있도록 `HandlerExceptionResolver` 등을 통해 예외 처리를 위임합니다.
- **결정 2 (기타 시큐리티 필터)**: 전역 핸들러로 위임하기 적절하지 않은 다른 인증/인가 에러(401, 403)의 경우, 시큐리티 설정에 등록된 `AuthenticationEntryPoint` 및 `AccessDeniedHandler` 커스텀 구현체에서 직접 표준화된 에러 응답을 반환합니다.

## 4. 토큰 전달 및 Redis 활용 전략
- **Access Token (AT)**: JSON 응답 Body를 통해 전달하며, 클라이언트는 Authorization 헤더로 전송.
- **Refresh Token (RT)**: `HttpOnly, Secure, SameSite=Lax` 속성의 Cookie를 통해 전달 및 회수.
- **저장소**: RT의 저장소는 PostgreSQL 대신 **Redis**를 사용합니다. 키 패턴: `RT:{username}`. O(1) 접근 속도와 자체 TTL 기능을 활용.
- **로그아웃 (Blacklist)**: AT는 남은 만료 시간만큼 Redis Blacklist(`BL:{accessToken}`)에 저장하여 `JwtAuthenticationFilter`에서 조회/차단합니다.

## 5. OAuth2 상태 관리 (Stateless OAuth2)
- **결정**: Spring Security의 기본 OAuth2 Session 방식을 사용하지 않고, `CookieOAuth2AuthorizationRequestRepository`를 커스텀 구현하여 인가 요청 상태(state 파라미터)를 HttpOnly 쿠키에 저장 및 검증합니다.
- **근거**: 서버의 완전한 무상태(Stateless) 시스템을 유지하기 위함입니다.

## 6. OAuth2 UserInfo 추상화 (OCP 준수)
- **결정**: Google, GitHub 등 Provider마다 다른 응답 속성값을 캡슐화하기 위해 `OAuth2UserInfo` 인터페이스와 `OAuth2UserInfoFactory`를 도입합니다.

## 7. 회원가입 Role 강제 (보안)
- **결정**: 클라이언트의 `SignupRequest` DTO에서 `role` 필드를 제거하고, 서버 로직이 무조건 `ROLE_USER`를 할당합니다.
- **근거**: 악의적인 클라이언트가 `ROLE_ADMIN`으로 가입하는 권한 상승 취약점을 원천 차단합니다.
