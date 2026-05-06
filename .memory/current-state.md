# Current State

## 프로젝트 위치

Spring Boot 4 기반 인증/인가 학습 프로젝트다.

진행 단계:
1. Step 1: 세션 기반 Form Login
2. Step 2: JWT 무상태 인증, AT/RT 분리, RTR
3. Step 3: Google OAuth2 로그인, 자체 JWT 발급, Docker Compose(PostgreSQL/Redis)
4. Step 4-A: Refresh Token Redis 전환, Access Token blacklist, 회원가입 role 취약점 수정

현재 기준으로 Step 4-A 구현은 완료됐다.

## 현재 도달 상태

- Refresh Token 저장소는 PostgreSQL JPA에서 Redis로 전환됐다.
- Redis key 전략은 `RT:{username}`, `BL:{accessToken}` 이다.
- 일반 로그인과 OAuth2 로그인 모두 Redis에 Refresh Token을 저장한다.
- 로그아웃 시 Redis RT 삭제와 AT blacklist 등록을 함께 수행한다.
- `JwtAuthenticationFilter` 는 유효한 JWT라도 blacklist 등록 토큰이면 인증을 주입하지 않는다.
- `GlobalExceptionHandler` 는 인증 실패 예외를 `401/423/403` 으로 세분화한다.
- JPA 기반 `RefreshToken` 엔티티와 `RefreshTokenRepository` 는 제거됐다.

## 바로 볼 문서

다음 작업 시작 전 우선 확인:
1. `docs/plans/step4-7-roadmap.md`
2. `docs/review/testCodeReview.md`
3. `docs/review/retrospective-test.md`
4. 필요 시 `docs/plans/step4-A-troubleshooting.md`

Step 4-A 구현 결과 확인용:
1. `docs/plans/step4-A-handover.md`
2. `docs/plans/step4-A-implementation.md`

## 현재 작업 시 주의

- 작업 시작 전 `git status --short` 로 사용자 변경사항을 먼저 확인한다.
- `.memory/` 는 최신 handoff 용도이며, 오래된 `docs/memory/project-status.md` 보다 우선한다.
- controller slice 테스트는 JWT 필터 동작 자체를 검증하지 않는다. JWT 필터 검증은 별도 security 테스트에서 다룬다.

## 다음 진입점

`tasks.md` 의 `Now` 부터 시작한다.
