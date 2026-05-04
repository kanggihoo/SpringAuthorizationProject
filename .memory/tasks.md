# Tasks

## Now

- Step 4-A 사이클 6: `JwtAuthenticationFilter` Blacklist 체크 검증/구현.
  - 목적: 로그아웃된 Access Token이 만료 전까지 계속 사용되는 보안 문제 차단.
  - 테스트: `src/test/java/org/example/security/jwt/JwtAuthenticationFilterTest.java`
  - 구현: `src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java`
  - 핵심 조건: 유효한 AT라도 `BL:{accessToken}`에 있으면 `SecurityContext`에 인증을 넣지 않는다.
  - 참고: `docs/plans/step4-A-handover.md`

- 현재 uncommitted 테스트 변경 상태 확인.
  - 목적: 이미 진행 중인 사용자 변경과 충돌하지 않기 위함.
  - 먼저 실행: `git status --short`
  - 주의: 수정/삭제/신규 파일을 임의로 되돌리지 않는다.

## Next

- Step 4-A 사이클 7: OAuth2 성공 핸들러 Refresh Token 저장소 Redis 전환.
  - 대상: `OAuth2AuthenticationSuccessHandler`
  - 변경 방향: `RefreshTokenRepository` 사용 제거, `TokenRedisRepository.saveRefreshToken(username, rt, ttlSeconds)` 사용.
  - 이유: 일반 로그인과 OAuth2 로그인의 RT 저장 전략을 Redis로 통일.

- Step 4-A 사이클 8: 인증 예외 응답 세분화.
  - 대상: `GlobalExceptionHandler`
  - 추가 대상: `BadCredentialsException` → 401, `LockedException` → 423, `DisabledException` → 403.
  - 이유: RuntimeException 일괄 400 처리에서 인증 실패 의미를 분리.

- Step 4-A 마무리: JPA RefreshToken 제거 여부 확인.
  - 삭제 후보: `domain/entity/RefreshToken.java`, `repository/RefreshTokenRepository.java`
  - 조건: OAuth2 SuccessHandler까지 Redis 전환 완료 후.

## Test Refactor Queue

- `AuthControllerTest` 개선.
  - 채택 방향: 슬라이스 테스트에서는 인증 상태만 주입하고 JWT 필터 검증은 별도 통합 테스트로 분리.
  - 커스텀 JWT 필터까지 돌리면 사실상 통합 테스트가 되므로 `@SpringBootTest` 쪽으로 분리한다.
  - 참고: `docs/review/testCodeReview.md`, `docs/review/retrospective-test.md`

- `MockMvcTester` + AssertJ 스타일로 전환 가능한 부분 정리.
  - `TokenResponseDto.refreshToken`은 `@JsonIgnore`라 JSON 본문이 아니라 `Set-Cookie`로 검증한다.

- `LoginRequestDto` 테스트 생성성 개선.
  - 채택 방향: `@Builder` 추가 선호, `@NoArgsConstructor`는 Jackson 역직렬화를 위해 유지.

- `TokenRedisRepositoryTest`는 당장 큰 리팩터링보다 안정성 우선.
  - 가능하면 `@SpringBootTest(classes = { TokenRedisRepository.class, RedisConfig.class })`로 좁힌다.
  - 실패하면 기존 방식을 유지하고 P0/P1 작업을 우선한다.

## Later

- Step 4-B: 로그인 실패 5회 계정 잠금, 잠긴 계정 AT 즉시 차단, 관리자 unlock API.
- Step 5: Method Security, 사용자 프로필/비밀번호/탈퇴 API, SecurityContext 즉시 갱신.
- Step 6: 보안 이벤트와 비동기 알림.
- Step 7: IP 기반 Brute Force 방어.

## Done Summary

- Step 2: JWT 무상태 인증, RTR, JSON 401/403, ExceptionHandlerFilter 설계 완료.
- Step 3: Google OAuth2, OAuth2 state cookie repository, Success/FailureHandler, Docker Compose 도입 완료.
- Step 4-A 사이클 1~5: role 지정 취약점 수정, RedisConfig/TokenRedisRepository, `getRemainingExpiration`, AuthService Redis 전환, AuthController 테스트 완료 문서 존재.
