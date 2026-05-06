# Step 4-A Implementation

상태: 완료
최종 갱신일: 2026-05-06

## 목표

- Refresh Token 저장소를 Redis 로 전환
- Access Token blacklist 처리 도입
- 회원가입 role 지정 취약점 제거

## 완료된 구현

### Step 1. 회원가입 role 취약점 수정

- `SignupRequest` 에서 role 필드 제거
- `UserServiceImpl` 에서 항상 `ROLE_USER` 부여

### Step 2. Redis 저장소 도입

- `RedisConfig` 에 `StringRedisTemplate` 등록
- `TokenRedisRepository` 로 RT / BL 저장
- key 규칙:
  - `RT:{username}`
  - `BL:{accessToken}`

### Step 3. JWT 남은 만료시간 계산

- `JwtTokenProvider.getRemainingExpiration(String token)` 추가

### Step 4. AuthService Redis 전환

- login: Redis 에 RT 저장
- logout: Redis RT 삭제 + AT blacklist 등록
- refresh: Redis RT 비교 후 RTR 수행

### Step 5. AuthController 테스트 정리

- 기존 controller 테스트를 현재 auth 흐름 기준으로 유지

### Step 6. JwtAuthenticationFilter blacklist 체크

- 유효한 JWT 라도 blacklist 등록 토큰이면 인증 주입 없이 통과

### Step 7. OAuth2AuthenticationSuccessHandler Redis 전환

- OAuth2 로그인 성공 시 Redis 에 RT 저장
- JPA `RefreshTokenRepository` 의존 제거

### Step 8. GlobalExceptionHandler 인증 예외 세분화

- `BadCredentialsException` -> 401
- `LockedException` -> 423
- `DisabledException` -> 403

### Step 9. JPA RefreshToken 제거

- `RefreshToken` 엔티티 삭제
- `RefreshTokenRepository` 삭제

## 테스트

추가/수정 테스트:

- `UserServiceImplTest`
- `TokenRedisRepositoryTest`
- `JwtTokenProviderTest`
- `AuthServiceImplTest`
- `AuthControllerTest`
- `JwtAuthenticationFilterTest`
- `OAuth2AuthenticationSuccessHandlerTest`
- `GlobalExceptionHandlerTest`

최종 검증:

```powershell
./gradlew test -x checkstyleMain -x checkstyleTest
```

결과: 전체 테스트 통과.

## 구현 후 메모

- `JwtAuthenticationFilter` 생성자에 `TokenRedisRepository` 가 추가되어 테스트 전용 security config 도 함께 수정해야 한다.
- 이후 단계는 Step 4-B 계정 잠금 기능이다.
