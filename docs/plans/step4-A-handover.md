# Step 4-A 인수인계 요약

> 작성일: 2026-04-15  
> 브랜치: `step4`  
> 목표: RT 저장소 PostgreSQL → Redis 전환 + AT Blacklist 구현 + role 보안 취약점 수정

---

## 완료된 작업 (사이클 1~5)

### 사이클 1 — SignupRequest role 보안 취약점 수정 ✅

**변경 파일:**
- [SignupRequest.java](../../src/main/java/org/example/dto/request/SignupRequest.java) — `role` 필드 제거 (클라이언트가 role 지정 불가)
- [UserServiceImpl.java](../../src/main/java/org/example/service/UserServiceImpl.java) — `signupRequest.getRole()` → `"ROLE_USER"` 하드코딩

**테스트:** `UserServiceImplTest` — 3개 테스트 모두 통과

---

### 사이클 2 — Redis 인프라 구축 ✅

**신규 파일:**
- [RedisConfig.java](../../src/main/java/org/example/config/RedisConfig.java) — `StringRedisTemplate` 빈 등록
- [TokenRedisRepository.java](../../src/main/java/org/example/repository/TokenRedisRepository.java) — RT/BL Redis 저장소

**Redis 키 패턴:**
- `RT:{username}` — Refresh Token 저장
- `BL:{accessToken}` — Blacklist 등록된 Access Token

**테스트:** `TokenRedisRepositoryTest` — Testcontainers Redis로 5개 통합 테스트 통과

**테스트 설정:**
- [application-test.yml](../../src/test/resources/application-test.yml) 신규 생성
  - DataSource: `jdbc:tc:postgresql:16:///testdb` (Testcontainers JDBC URL)
  - Redis: `@DynamicPropertySource`로 컨테이너 host/port 동적 주입

---

### 사이클 3 — JwtTokenProvider.getRemainingExpiration() 추가 ✅

**변경 파일:**
- [JwtTokenProvider.java](../../src/main/java/org/example/security/jwt/JwtTokenProvider.java) — `getRemainingExpiration(String token)` 메서드 추가

**테스트:** `JwtTokenProviderTest` — 5개 테스트 통과

---

### 사이클 4 — AuthService/AuthServiceImpl Redis 전환 ✅

**변경 파일:**
- [AuthService.java](../../src/main/java/org/example/service/AuthService.java) — `logout(Long userId)` → `logout(String username, String accessToken)`
- [AuthServiceImpl.java](../../src/main/java/org/example/service/AuthServiceImpl.java) — JPA `RefreshTokenRepository` 완전 제거, `TokenRedisRepository`로 전환
- [AuthController.java](../../src/main/java/org/example/controller/AuthController.java) — logout에 `HttpServletRequest` + `resolveToken()` 추가
- [AuthApi.java](../../src/main/java/org/example/controller/docs/AuthApi.java) — logout 시그니처 동기화

**핵심 로직 변경:**
- `login()`: Redis `saveRefreshToken(username, rt, ttlSeconds)`
- `logout()`: Redis `deleteRefreshToken(username)` + `addToBlacklist(at, remainingTtl)`
- `refresh()`: RT 파싱 → `username` 추출 → Redis 조회 → DB 없이 RTR 검증

**테스트:** `AuthServiceImplTest` — 7개 테스트 통과

---

### 사이클 5 — AuthController 테스트 ✅

**테스트:** `AuthControllerTest` — 6개 테스트 통과  
`@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)` 방식 사용

---

## 미완료 작업 (다음에 시작할 곳)

### 사이클 6 — JwtAuthenticationFilter Blacklist 체크 ← **여기서부터 시작**

**해야 할 일:**

**Step 6-1: 껍데기** — 없음 (기존 파일 수정)

**Step 6-2: Red 테스트 작성**
파일: `src/test/java/org/example/security/jwt/JwtAuthenticationFilterTest.java`
```
테스트 케이스:
1. 유효한 AT + Blacklist에 없음 → SecurityContext에 인증 정보 설정됨
2. 유효한 AT + Blacklist에 있음 → SecurityContext 비어있음 (인증 거부)
3. AT 없음 (shouldNotFilter 경로) → 필터 skip
```

**Step 6-3: Green 구현**
파일: [JwtAuthenticationFilter.java](../../src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java)
```java
// TokenRedisRepository 의존성 추가
private final TokenRedisRepository tokenRedisRepository;

// doFilterInternal() 수정
if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
    if (tokenRedisRepository.isBlacklisted(token)) {
        filterChain.doFilter(request, response);
        return; // Blacklist 등록된 AT → 인증 거부
    }
    // 기존 UserDetails 로딩 + SecurityContext 설정
}
```

**실행:** `./gradlew test --tests "org.example.security.jwt.JwtAuthenticationFilterTest" -x checkstyleMain -x checkstyleTest`

---

### 사이클 7 — OAuth2AuthenticationSuccessHandler Redis 전환

파일: [OAuth2AuthenticationSuccessHandler.java](../../src/main/java/org/example/security/oauth2/OAuth2AuthenticationSuccessHandler.java)
- `RefreshTokenRepository` → `TokenRedisRepository` 교체
- JPA RT 저장 로직 → `tokenRedisRepository.saveRefreshToken(username, rt, ttlSeconds)`

---

### 사이클 8 — GlobalExceptionHandler 인증 예외 세분화

파일: [GlobalExceptionHandler.java](../../src/main/java/org/example/exception/GlobalExceptionHandler.java)
추가할 핸들러:
- `BadCredentialsException` → 401
- `LockedException` → 423
- `DisabledException` → 403

---

### 마무리 — 정리 및 전체 검증

**삭제 파일:**
- `src/main/java/org/example/domain/entity/RefreshToken.java`
- `src/main/java/org/example/repository/RefreshTokenRepository.java`

> ⚠️ 사이클 6, 7 완료 후 삭제할 것 (아직 `OAuth2AuthenticationSuccessHandler`가 참조 중)

**전체 테스트 실행:**
```bash
./gradlew test -x checkstyleMain -x checkstyleTest
```

---

## 테스트 실행 명령어 치트시트

```bash
# 사이클별 단독 실행
./gradlew test --tests "org.example.service.UserServiceImplTest" -x checkstyleMain -x checkstyleTest
./gradlew test --tests "org.example.repository.TokenRedisRepositoryTest" -x checkstyleMain -x checkstyleTest
./gradlew test --tests "org.example.security.jwt.JwtTokenProviderTest" -x checkstyleMain -x checkstyleTest
./gradlew test --tests "org.example.service.AuthServiceImplTest" -x checkstyleMain -x checkstyleTest
./gradlew test --tests "org.example.controller.AuthControllerTest" -x checkstyleMain -x checkstyleTest

# 다음에 추가할 테스트
./gradlew test --tests "org.example.security.jwt.JwtAuthenticationFilterTest" -x checkstyleMain -x checkstyleTest
./gradlew test --tests "org.example.security.oauth2.OAuth2AuthenticationSuccessHandlerTest" -x checkstyleMain -x checkstyleTest
./gradlew test --tests "org.example.exception.GlobalExceptionHandlerTest" -x checkstyleMain -x checkstyleTest
```

---

## 주요 설계 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| Redis 키 패턴 | `RT:{username}`, `BL:{accessToken}` | JWT subject = username = 고유 식별자이므로 userId 불필요 |
| refresh() userId 조회 | RT 파싱 → username 추출 → Redis 직접 조회 | DB 조회 없이 Redis만으로 처리 |
| 테스트 DataSource | Testcontainers PostgreSQL (`jdbc:tc:postgresql:...`) | H2 대신 실제 DB와 동일 환경 유지 |
| Controller 테스트 | `@AutoConfigureMockMvc(addFilters = false)` | SecurityConfig 복잡한 OAuth2 빈 로딩 회피 |
| logout 인증 주입 | `SecurityContextHolder` 직접 설정 | `addFilters=false` 환경에서 `with(user())` 미동작 |
