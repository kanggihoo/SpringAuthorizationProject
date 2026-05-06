# Step 4-A Handover

작성일: 2026-05-06
브랜치: `step4`
상태: 완료

## 완료 요약

Step 4-A 목표였던 세 가지가 모두 반영됐다.

1. Refresh Token 저장소를 PostgreSQL JPA 에서 Redis 로 전환
2. 로그아웃 이후 Access Token blacklist 처리 추가
3. 회원가입 시 role 지정 취약점 수정

추가로 다음 마무리 작업도 완료됐다.

- `JwtAuthenticationFilter` blacklist 체크 추가
- `OAuth2AuthenticationSuccessHandler` Redis 전환
- `GlobalExceptionHandler` 인증 예외 세분화
- JPA `RefreshToken` 엔티티 / 리포지토리 삭제

## 최종 반영 사항

### 1. Redis RT / BL 전략

- RT key: `RT:{username}`
- BL key: `BL:{accessToken}`
- 일반 로그인과 OAuth2 로그인 모두 Redis 에 RT 저장
- 로그아웃 시 RT 삭제 + AT blacklist 등록

### 2. Filter / Handler 변경

- `JwtAuthenticationFilter`
  - blacklist 등록된 AT 는 인증을 주입하지 않음
- `OAuth2AuthenticationSuccessHandler`
  - JPA `RefreshTokenRepository` 제거
  - `TokenRedisRepository.saveRefreshToken(username, rt, ttlSeconds)` 사용
- `GlobalExceptionHandler`
  - `BadCredentialsException` -> 401
  - `LockedException` -> 423
  - `DisabledException` -> 403

### 3. 삭제된 구성

- `src/main/java/org/example/domain/entity/RefreshToken.java`
- `src/main/java/org/example/repository/RefreshTokenRepository.java`

## 테스트 상태

신규/갱신 테스트:

- `JwtAuthenticationFilterTest`
- `OAuth2AuthenticationSuccessHandlerTest`
- `GlobalExceptionHandlerTest`
- `JwtSecurityTestConfig` wiring 보정
- `AuthControllerTest` wiring 보정

검증 결과:

```powershell
./gradlew test -x checkstyleMain -x checkstyleTest
```

전체 테스트 통과.

## 다음 작업

다음 진입점은 Step 4-B 이다.

- 로그인 실패 5회 계정 잠금
- 잠긴 계정 Access Token 즉시 차단
- 관리자 unlock API
