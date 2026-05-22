# 검증 근거

이 문서는 각 Phase의 보안 주장을 어떤 테스트로 증명하는지 정의한다. Phase는 필수 evidence가 `PASS`이거나 명시적으로 승인된 대체 evidence가 있을 때만 완료로 본다.

## 상태

- `TODO`: 필수 테스트가 아직 작성되지 않음
- `RED`: 테스트는 존재하지만 현재 실패함
- `WRITTEN`: 테스트는 존재하지만 최신 실행 결과가 이 문서에 기록되지 않음
- `PASS`: 테스트가 존재하고 기록된 최신 실행 결과가 통과함
- `BLOCKED`: 구현 또는 설계 결정이 부족해 검증할 수 없음
- `N/A`: 현재 코드베이스 구조에는 적용되지 않음

## 커버리지 목표

JaCoCo line coverage 목표는 현재 HEAD 코드베이스 전체 기준 80% 이상이다. 이는 Phase 완료 기준과 별개의 품질 기준이다. Phase 완료는 해당 Phase의 필수 evidence로 판단하고, 커버리지는 현재 코드베이스 전체를 기준으로 확인한다.

## Phase 1 - JWT Dual Token 인증

Phase 1은 Access Token과 Refresh Token을 분리하고, Protected API 접근이 JWT 인증 정책으로 제어되는지 검증한다.

발표 및 포트폴리오용 해설 문서: [Phase 1 - JWT Dual Token 인증](portfolio/phase1-jwt-dual-token.md)

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| Access Token이 Protected API 인증을 만든다 | 유효한 Access Token으로 Protected API 호출 | 200 OK 또는 인증된 `SecurityContext` 생성 | `JwtAuthenticationFilterTest.doFilter_setsAuthentication_whenBearerTokenIsValid`, `JwtSecurityIntegrationTest.returnsOk_whenUserTokenAccessesUserEndpoint` | PASS | `./gradlew.bat test --tests org.example.security.jwt.JwtAuthenticationFilterTest --tests org.example.security.jwt.JwtSecurityIntegrationTest --tests org.example.security.token.TokenLifecycleServiceImplTest`로 2026-05-21 검증 |
| Access Token이 없으면 Protected API 접근이 거부된다 | 토큰 없이 Protected API 호출 | 401 Unauthorized | `JwtSecurityIntegrationTest.returnsUnauthorized_whenTokenMissing` | PASS | `./gradlew.bat test --tests org.example.security.jwt.JwtAuthenticationFilterTest --tests org.example.security.jwt.JwtSecurityIntegrationTest --tests org.example.security.token.TokenLifecycleServiceImplTest`로 2026-05-21 검증 |
| 변조된 Access Token은 거부된다 | 서명이 깨진 JWT 사용 | JWT 예외 또는 401 Unauthorized | `JwtAuthenticationFilterTest.doFilter_throwsJwtException_whenTokenIsInvalid`, `JwtSecurityIntegrationTest.returnsUnauthorized_whenTokenIsTampered` | PASS | `./gradlew.bat test --tests org.example.security.jwt.JwtAuthenticationFilterTest --tests org.example.security.jwt.JwtSecurityIntegrationTest --tests org.example.security.token.TokenLifecycleServiceImplTest`로 2026-05-21 검증 |
| Refresh Token으로 새 토큰 쌍을 발급할 수 있다 | 유효한 Refresh Token 사용 | 새 Access Token과 새 Refresh Token 발급 | `TokenLifecycleServiceImplTest.rotate_replacesActiveRefreshToken` | PASS | `./gradlew.bat test --tests org.example.security.jwt.JwtAuthenticationFilterTest --tests org.example.security.jwt.JwtSecurityIntegrationTest --tests org.example.security.token.TokenLifecycleServiceImplTest`로 2026-05-21 검증 |

## Phase 2 - Redis Token Store + Logout Blacklist

발표 및 포트폴리오용 해설 문서: [Phase 2 - Redis Token Store + Logout Blacklist](portfolio/phase2-redis-token-store-logout-blacklist.md)

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| Refresh Token이 Redis에 저장된다 | 로컬 로그인이 성공한다 | `RT:{username}`이 저장된다 | `TokenLifecycleServiceImplTest.issue_storesRefreshTokenForJwtSubject` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| Refresh Token에 Redis TTL이 설정된다 | 토큰이 저장된다 | Redis TTL이 0보다 크다 | `TokenRedisRepositoryTest.saveRefreshToken_setsTtl` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| 로그아웃하면 Refresh Token이 삭제된다 | 로그아웃이 성공한다 | `RT:{username}`이 삭제된다 | `TokenLifecycleServiceImplTest.logout_removesRefreshTokenAndBlacklistsAccessToken` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| 로그아웃하면 Access Token이 블랙리스트에 등록된다 | 로그아웃이 성공한다 | 토큰 만료 시점까지 `BL:{accessToken}`이 저장된다 | `TokenLifecycleServiceImplTest.logout_removesRefreshTokenAndBlacklistsAccessToken` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| 블랙리스트에 등록된 Access Token은 거부된다 | 로그아웃된 Access Token으로 Protected API를 호출한다 | `SecurityContext`가 인증 상태가 되지 않는다 | `JwtAuthenticationFilterTest.doFilter_doesNotAuthenticate_whenAccessTokenIsBlacklisted` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| PostgreSQL RefreshToken 모델이 제거된다 | JPA Refresh Token이 필요한 코드 경로가 없다 | 운영 코드에서 `RefreshTokenRepository` 의존성이 없다 | 컴파일 및 테스트 스위트 | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |

## Phase 3 - Refresh Token Rotation & Reuse Detection

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| Refresh Token은 사용 시 회전된다 | 유효한 Refresh Token이 한 번 사용된다 | 새 Refresh Token이 기존 저장 토큰을 대체한다 | `TokenLifecycleServiceImplTest.rotate_replacesActiveRefreshToken` | PASS | 2026-05-22에 `./gradlew.bat test --tests org.example.security.token.TokenLifecycleServiceImplTest.rotate_replacesActiveRefreshToken`로 검증 |
| 이전 Refresh Token은 재사용할 수 없다 | 이전 토큰이 저장된 토큰과 다르다 | `REFRESH_TOKEN_REUSED` 인증 실패 | `TokenLifecycleServiceImplTest.rotate_rejectsReusedRefreshToken` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |

## Phase 4 - Account Lock & Admin Recovery

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| 잘못된 비밀번호가 반복되면 계정이 잠긴다 | 로그인 5회 연속 실패 | 423 Locked와 계정 잠금 | `AuthServiceImplTest.login_locksUser_afterFiveFailures` | TODO | 실패 횟수 필드와 로직 필요 |
| 잠긴 계정은 로그인할 수 없다 | 잠금 후 올바른 비밀번호로 로그인 | 423 Locked | `AuthServiceImplTest.login_throwsLockedException_whenUserLocked` | TODO | `LockedException` 처리 필요 |
| 잠긴 계정은 기존 Access Token을 사용할 수 없다 | 잠금 후 기존 토큰으로 Protected API 호출 | `SecurityContext`가 인증 상태가 되지 않는다 | `JwtAuthenticationFilterTest.doFilter_doesNotAuthenticate_whenUserIsLocked` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| ADMIN은 계정 잠금을 해제할 수 있다 | ADMIN이 사용자의 잠금을 해제한다 | 계정 잠금이 해제된다 | `AdminServiceImplTest.unlockUser_unlocksLockedUser` | TODO | Admin service가 아직 구현되지 않음 |
| USER는 계정 잠금을 해제할 수 없다 | USER가 admin endpoint를 호출한다 | 403 Forbidden | `AdminControllerSecurityTest.userCannotUnlockAccount` | TODO | Admin endpoint가 아직 구현되지 않음 |

## Phase 5 - Security Audit Event

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| 로그인 실패가 감사된다 | 잘못된 비밀번호로 로그인 | LoginFailed 이벤트 또는 감사 기록 | `SecurityAuditEventTest.loginFailure_isRecorded` | TODO | 이벤트 모델이 아직 구현되지 않음 |
| 계정 잠금이 감사된다 | 실패 임계값 도달 | AccountLocked 이벤트 또는 감사 기록 | `SecurityAuditEventTest.accountLock_isRecorded` | TODO | Phase 4에 의존 |
| Refresh Token 재사용이 감사된다 | 이전 Refresh Token 재사용 | TokenReuse 이벤트 또는 감사 기록 | `SecurityAuditEventTest.refreshReuse_isRecorded` | TODO | Phase 3 이벤트 결정에 의존 |

## Phase 6 - OAuth2 Service JWT Issuing

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| OAuth2 로그인은 서비스 Access Token을 발급한다 | OAuth2 success handler가 실행된다 | 서비스 JWT가 생성된다 | `OAuth2AuthenticationSuccessHandlerTest` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| OAuth2 로그인은 서비스 Refresh Token을 발급한다 | OAuth2 success handler가 실행된다 | Refresh Token cookie가 설정된다 | `OAuth2AuthenticationSuccessHandlerTest` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| OAuth2 로그인과 로컬 로그인은 Token Store 정책을 공유한다 | OAuth2 success handler가 실행된다 | 토큰 발급이 `TokenLifecycleService`를 거친다 | `OAuth2AuthenticationSuccessHandlerTest.onAuthenticationSuccess_issuesTokensThroughTokenLifecycleServiceAndRedirects` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |

## Phase 7 - OAuth2 One-time Code Exchange

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| Refresh Token 전달 정책이 중앙화된다 | 로그인, refresh, logout, OAuth2 success가 Refresh Token을 전달한다 | Cookie 생성, 만료, 읽기가 `TokenDeliveryService`를 거친다 | `TokenDeliveryServiceImplTest` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| Bearer Access Token 추출이 중앙화된다 | Protected API 또는 logout이 Authorization header를 받는다 | Bearer token 추출이 `TokenDeliveryService`를 거친다 | `TokenDeliveryServiceImplTest.resolveBearerAccessToken_returnsBearerToken` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| Access Token은 redirect URL에 노출되지 않는다 | OAuth2 로그인이 성공한다 | Redirect에는 Access Token이 아니라 one-time code가 포함된다 | `OAuth2AuthenticationSuccessHandlerTest.redirectsWithOneTimeCode` | TODO | 현재 코드는 URL fragment를 사용함 |
| One-time code는 한 번만 교환할 수 있다 | 첫 번째 교환 요청 | Access Token 응답과 Refresh Token cookie | `OAuth2TokenExchangeTest.exchangeCode_success` | TODO | Endpoint가 아직 구현되지 않음 |
| One-time code는 재사용할 수 없다 | 두 번째 교환 요청 | 400 또는 401 | `OAuth2TokenExchangeTest.exchangeCode_reuseRejected` | TODO | Redis consume-and-delete 연산 필요 |
| 만료된 code는 거부된다 | TTL 이후 교환 | 400 또는 401 | `OAuth2TokenExchangeTest.expiredCode_rejected` | TODO | Redis TTL 필요 |

## Phase 8 - RBAC & Method Security

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| USER는 ADMIN API에 접근할 수 없다 | USER가 `/admin/**`를 호출한다 | 403 Forbidden | `JwtSecurityIntegrationTest` 또는 `AdminControllerSecurityTest` | WRITTEN | 기존 security integration이 admin path를 다룸 |
| ADMIN은 ADMIN API에 접근할 수 있다 | ADMIN이 `/admin/**`를 호출한다 | 200 OK | `JwtSecurityIntegrationTest` 또는 `AdminControllerSecurityTest` | WRITTEN | 기존 security integration이 admin path를 다룸 |
| Service method는 소유권과 역할을 강제한다 | 사용자가 보호 리소스를 변경한다 | 소유자 또는 허용된 역할만 가능 | `UserServiceMethodSecurityTest` | TODO | Method security가 아직 구현되지 않음 |

## Phase 9 - Redis Failure Policy

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| Redis를 사용할 수 없으면 정책에 따라 토큰 발급이 실패한다 | 로그인 또는 OAuth2 토큰 발급 중 Redis 장애 | `TOKEN_STORE_UNAVAILABLE`과 503 | `TokenLifecycleServiceImplTest.issue_throwsTokenStoreUnavailable_whenRedisSaveFails` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| Redis를 사용할 수 없으면 정책에 따라 refresh가 실패한다 | Refresh 중 Redis 장애 | `TOKEN_STORE_UNAVAILABLE`과 503 | `TokenLifecycleServiceImplTest.rotate_throwsTokenStoreUnavailable_whenRedisReadFails` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| Redis를 사용할 수 없을 때 logout 동작이 정의되어 있다 | Logout 중 Redis 장애 | Refresh Token cookie가 만료되고 서버는 `TOKEN_STORE_UNAVAILABLE`과 503을 반환한다 | `TokenLifecycleServiceImplTest.logout_throwsTokenStoreUnavailable_whenRedisDeleteFails`, `AuthControllerTest.expiresCookieAndReturns503_whenTokenStoreUnavailable` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |
| Protected API blacklist 확인은 장애 정책을 따른다 | Access Token 검증 중 Redis 장애 | `TOKEN_STORE_UNAVAILABLE`과 503 | `TokenLifecycleServiceImplTest.isAccessTokenAllowed_throwsTokenStoreUnavailable_whenRedisReadFails` | PASS | 2026-05-20에 `./gradlew.bat test`로 검증 |

## Phase 10 - Coverage 80% & Evidence

| 주장 | 시나리오 | 기대 결과 | 대상 테스트 | 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| 현재 코드베이스가 커버리지 목표를 충족한다 | JaCoCo로 전체 테스트 스위트 실행 | Line coverage >= 80% | `./gradlew test jacocoTestReport` | TODO | JaCoCo task 또는 verification 설정이 필요할 수 있음 |
| Evidence matrix가 최신 상태를 유지한다 | Phase 주장이 변경된다 | `docs/evidence.md`가 테스트와 상태를 반영하도록 갱신된다 | 문서 리뷰 | TODO | 각 Phase 진행 중 업데이트 |
