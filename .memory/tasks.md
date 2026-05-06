# Tasks

## Now

- Step 4-B 범위 정리 시작.
  - 로그인 실패 5회 계정 잠금
  - 잠긴 계정 Access Token 즉시 차단
  - 관리자 unlock API

## Next

- Step 4-B 구현 계획 문서 작성 또는 갱신.
- 계정 잠금 도메인 모델을 기존 인증 흐름과 어디서 연결할지 결정.
- 잠금/해제/실패횟수 테스트 전략 정리.

## Test Refactor Queue

- `AuthControllerTest`
  - 현재는 새 `JwtAuthenticationFilter` 의존성 때문에 `TokenRedisRepository` mock bean 이 추가된 상태다.
  - 필요하면 controller slice 와 security integration 테스트 역할을 더 분리한다.

- `TokenRedisRepositoryTest`
  - 현재 Testcontainers Redis 기반 통합 테스트다.
  - 컨텍스트 범위를 더 줄일 수 있는지 추후 검토한다.

- `MockMvcTester` 적용 범위
  - 일부 테스트는 이미 `MockMvcTester` 를 사용하지만, 프로젝트 전체적으로는 아직 혼재 상태다.

## Later

- Step 5: Method Security, 사용자 프로필/비밀번호/토큰 관련 API, SecurityContext 즉시 갱신
- Step 6: 보안 이벤트 비동기 알림
- Step 7: IP 기반 Brute Force 방어

## Done Summary

- Step 2: JWT 무상태 인증, RTR, JSON 401/403, ExceptionHandlerFilter 구조 완료
- Step 3: Google OAuth2, OAuth2 state cookie repository, Success/FailureHandler, Docker Compose 완료
- Step 4-A:
  - 회원가입 role 지정 취약점 수정
  - RedisConfig / TokenRedisRepository 도입
  - `JwtTokenProvider.getRemainingExpiration()` 추가
  - AuthService Redis 전환
  - AuthController 테스트 정리
  - `JwtAuthenticationFilter` blacklist 체크 추가
  - `OAuth2AuthenticationSuccessHandler` Redis 전환
  - `GlobalExceptionHandler` 인증 예외 세분화
  - JPA `RefreshToken` 엔티티/리포지토리 제거
  - 전체 테스트 통과 (`./gradlew test -x checkstyleMain -x checkstyleTest`)
