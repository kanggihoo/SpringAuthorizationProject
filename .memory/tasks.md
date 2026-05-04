# Task Queue

앞으로 진행해야 할 작업 목록입니다. 위에서부터 순서대로 진행합니다.

## 🟢 NOW (즉시 실행할 작업)
**[Step 4-A] 사이클 6: `JwtAuthenticationFilter` Blacklist 검증 로직 구현**
- **목표**: 로그아웃 처리되어 Redis Blacklist에 들어간 Access Token을 사용한 API 요청을 원천 차단.
- **TDD (Red)**: `src/test/java/org/example/security/jwt/JwtAuthenticationFilterTest.java`에 Blacklist에 존재하는 토큰인 경우 SecurityContext가 비어있는지(인증 실패) 확인하는 테스트 추가.
- **TDD (Green)**: `JwtAuthenticationFilter`에 `TokenRedisRepository`를 주입받고, `isBlacklisted(token)`이 true일 경우 로직 수행 없이 `filterChain.doFilter`로 넘기도록 분기 처리.

## 🟡 NEXT (현재 세션 내 수행할 작업)
1. **[Step 4-A] 사이클 7: `OAuth2AuthenticationSuccessHandler` Redis 전환**
   - JPA `RefreshTokenRepository` 대신 `TokenRedisRepository`를 주입받아, OAuth2 로그인 성공 시 생성된 RT를 Redis에 저장(`saveRefreshToken`)하도록 수정.
2. **[Step 4-A] 사이클 8: `GlobalExceptionHandler` 인증 예외 응답 세분화**
   - `BadCredentialsException` → 401 Unauthorized
   - `LockedException` → 423 Locked
   - `DisabledException` → 403 Forbidden
3. **[Step 4-A] 마무리 및 청소**
   - 더 이상 사용하지 않는 `RefreshToken.java` 엔티티와 `RefreshTokenRepository.java` 인터페이스 삭제.
   - 전체 테스트(`./gradlew test`) 통과 및 컴파일 점검.

## 🔴 BACKLOG (향후 로드맵)
- **[Step 4-B] 계정 잠금 시스템 & 관리자 API**: 5회 이상 로그인 실패 시 계정 강제 잠금. `JwtAuthenticationFilter`에서 잠긴 계정의 토큰 사용 즉시 차단. 관리자(`/admin/users/*/unlock`) 전용 잠금 해제 API 구현.
- **[Step 5] RBAC & Method Security**: `@PreAuthorize`를 활용한 메서드 보안, 프로필 수정 및 비밀번호 변경 API 구현 (비밀번호 변경 시 RT 즉시 만료 적용).
- **[Step 6] 보안 이벤트 비동기 처리**: `ApplicationEventPublisher`를 이용해 로그인 실패, 계정 잠금, 비밀번호 변경 알림 비동기 로깅.
- **[Step 7-A] IP Rate Limiting**: Redis를 활용한 무차별 대입(Brute-force) 공격 방어 로직.
- **[Step 7-B] OAuth2 일회성 코드 교환**: AT가 URL Fragment(`#accessToken`)로 노출되는 취약점을 해결하기 위해 단기 교환 코드로 변경.
- **[Step 7-C] E2E 인증 테스트**: E2E 시나리오 테스트 작성.
