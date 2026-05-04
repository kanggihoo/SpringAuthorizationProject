# Current State

## 프로젝트 위치

Spring Boot 4 기반 인증/인가 학습 프로젝트다.

진행 흐름은 다음 순서로 발전했다.

1. Step 1: 세션 기반 Form Login
2. Step 2: JWT 무상태 인증, Access/Refresh Token 분리, RTR
3. Step 3: Google OAuth2 로그인, 자체 JWT 발급, Docker Compose PostgreSQL/Redis
4. Step 4-A: Refresh Token Redis 전환, Access Token Blacklist, role 지정 취약점 수정

현재 기준으로는 Step 3이 완료 문서로 archive에 들어가 있고, Step 4-A의 사이클 1~5가 완료된 인수인계 문서가 있다.

## 현재 핵심 맥락

- RT 저장소는 PostgreSQL JPA에서 Redis로 옮기는 방향이다.
- Redis 키 패턴은 `RT:{username}`, `BL:{accessToken}`를 사용한다.
- 로그아웃은 Redis RT 삭제와 AT Blacklist 등록을 함께 수행해야 한다.
- OAuth2 로그인 성공 후에도 일반 로그인과 동일하게 자체 JWT AT/RT를 발급한다.
- Google OAuth2는 `security/oauth2`의 provider attribute 추상화 레이어를 사용한다.
- OAuth2 state는 세션이 아니라 HttpOnly cookie 기반 저장소로 관리한다.
- 기존 `docs/memory/project-status.md`는 Step 2 기준이라 최신 상태로 보지 않는다.

## 바로 볼 문서

작업을 이어받을 때는 아래 문서를 우선 확인한다.

1. `docs/plans/step4-A-handover.md`
2. `docs/review/testCodeReview.md`
3. `docs/review/retrospective-test.md`
4. 필요한 경우 `docs/plans/step4-A-troubleshooting.md`

## 현재 작업 트리 주의

최근 상태 확인 시 코드/테스트 변경과 신규 테스트 파일이 존재했다.
작업 시작 전 반드시 `git status --short`로 사용자 변경을 확인하고, 기존 변경을 되돌리지 않는다.

## 다음 진입점

`tasks.md`의 `Now`부터 시작한다.
