# Step 4-A 구현 계획: Redis 마이그레이션 + AT Blacklist

## Context

Step 3까지 JWT 인증 + Google OAuth2가 완료되었지만, RT는 PostgreSQL JPA로 저장 중이고 AT Blacklist가 미구현 상태.
Docker Compose에 Redis가 이미 실행 중이고 `spring-boot-starter-data-redis` 의존성도 있지만, Redis를 사용하는 코드가 전혀 없다.

**해결할 문제:**
1. RT 저장소를 PostgreSQL → Redis로 이전 (TTL 자동 관리)
2. 로그아웃 시 AT Blacklist 등록 (로그아웃 후 AT 무효화)
3. 회원가입 시 클라이언트가 `ROLE_ADMIN` 지정 가능한 보안 취약점 수정

**TDD 규칙:**
1. **껍데기 먼저** → 2. **실패 테스트(Red)** → 3. **최소 구현(Green)** → 4. **리팩토링**
- 구현 없이 테스트 먼저 작성 금지 (import 에러 = Red 아님)
- 각 단계마다 실행해서 결과 눈으로 확인

**주석 규칙:**
- 모든 클래스에 한국어 JavaDoc 작성 (클래스 역할, 설계 의도)
- 핵심 비즈니스 로직에 인라인 한국어 주석
- 기존 코드의 주석 스타일(한국어) 유지
- 테스트 코드에도 `@DisplayName` 한국어 + 각 테스트 블록 주석

**Spring Boot 4.0.2 테스트 규칙 (spring-boot-testing 스킬 참조):**
- `@MockBean` → `@MockitoBean` (import: `org.springframework.test.context.bean.override.mockito.MockitoBean`)
- `@SpyBean` → `@MockitoSpyBean`
- `MockMvcTester` + AssertJ 스타일 assertions
- Service 테스트: 순수 단위 테스트 (Mockito @Mock + @InjectMocks, Spring Context 없음)
- Controller 테스트: `@WebMvcTest` + `MockMvcTester` + `@MockitoBean`
- Redis 통합 테스트: Testcontainers

---

## 사전 준비

### 0-1. build.gradle 의존성 추가

**파일:** `build.gradle`

추가할 의존성:
```groovy
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.springframework.boot:spring-boot-starter-data-redis-test'  // @DataRedisTest 슬라이스
testRuntimeOnly 'com.redis:testcontainers-redis:2.2.4'  // Testcontainers Redis 모듈
```

> Spring Boot 4.0에서는 Testcontainers 2.0 네이밍을 사용.

### 0-2. src/test/resources/application-test.yml 생성

**파일:** `src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
  security:
    oauth2:
      client:
        registration: {}
jwt:
  secret: dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==
  access-token-expiration: 3600000
  refresh-token-expiration: 604800000
app:
  oauth2:
    redirect-uri: http://localhost:3000/oauth2/callback
```

H2 의존성도 추가:
```groovy
testRuntimeOnly 'com.h2database:h2'
```

---

## TDD 사이클 1: SignupRequest role 보안 취약점 수정

> 가장 단순하고 독립적인 작업. 먼저 처리.

### 1-1. 껍데기 — 없음 (기존 파일 수정)

### 1-2. Red — UserServiceImpl 테스트 작성

**파일:** `src/test/java/org/example/service/UserServiceImplTest.java`

```java
// 순수 단위 테스트: @Mock + @InjectMocks
// 테스트: signup() 호출 시 role이 항상 ROLE_USER로 강제되는지 검증
// - signupRequest에 "ROLE_ADMIN" 넣어도 ROLE_USER가 할당되는지
// - roleRepository.findByName("ROLE_USER")가 호출되는지 verify
```

실행 → **Red** (현재 코드는 클라이언트 role을 그대로 사용하므로)

### 1-3. Green — SignupRequest, UserServiceImpl 수정

**수정 파일:**
- `dto/request/SignupRequest.java` — `role` 필드 제거
- `service/UserServiceImpl.java` — `signupRequest.getRole()` → `"ROLE_USER"` 하드코딩

### 1-4. 리팩토링

- Swagger `@Schema` 어노테이션에서 role 관련 문서 제거
- AuthApi 인터페이스에서 role 관련 Swagger 문서 있으면 제거

### 실행: `./gradlew test --tests "org.example.service.UserServiceImplTest"`

---

## TDD 사이클 2: RedisConfig + TokenRedisRepository

> Redis 인프라 구축. Testcontainers 통합 테스트로 실제 Redis 동작 검증.

### 2-1. 껍데기 — RedisConfig, TokenRedisRepository 생성

**생성 파일:**
- `config/RedisConfig.java` — `@Configuration`, `RedisTemplate<String, String>` 빈 (빈 메서드 껍데기)
- `repository/TokenRedisRepository.java` — `@Repository`, 메서드 시그니처만 (모두 빈 구현)

```java
// TokenRedisRepository 메서드 시그니처:
void saveRefreshToken(Long userId, String refreshToken, long ttlSeconds);
Optional<String> findRefreshToken(Long userId);
void deleteRefreshToken(Long userId);
void addToBlacklist(String accessToken, long remainingTtlMillis);
boolean isBlacklisted(String accessToken);
```

### 2-2. Red — TokenRedisRepository Testcontainers 통합 테스트

**파일:** `src/test/java/org/example/repository/TokenRedisRepositoryTest.java`

```java
// @DataRedisTest 또는 @SpringBootTest 슬라이스 + Testcontainers Redis
// @Testcontainers + @Container static RedisContainer
// @DynamicPropertySource로 spring.data.redis.host/port 주입
//
// 테스트 케이스:
// 1. saveRefreshToken() + findRefreshToken() — 저장 후 조회 가능
// 2. deleteRefreshToken() — 삭제 후 조회 시 Optional.empty()
// 3. saveRefreshToken() TTL — 저장 후 TTL이 설정되는지
// 4. addToBlacklist() + isBlacklisted() — Blacklist 등록 후 조회 시 true
// 5. isBlacklisted() — 미등록 토큰 조회 시 false
```

실행 → **Red** (껍데기만 있으므로 테스트 실패)

### 2-3. Green — RedisConfig, TokenRedisRepository 구현

**RedisConfig:**
- `StringRedisTemplate` 또는 `RedisTemplate<String, String>` 빈 등록

**TokenRedisRepository:**
- `StringRedisTemplate` 주입
- RT 키 패턴: `RT:{userId}`, BL 키 패턴: `BL:{accessToken}`
- `ValueOperations.set(key, value, timeout, TimeUnit)` 사용
- `isBlacklisted()`: `Boolean.TRUE.equals(redisTemplate.hasKey("BL:" + token))`

### 2-4. 리팩토링

- 키 접두사를 상수로 추출 (`private static final String RT_PREFIX = "RT:";`)

### 실행: `./gradlew test --tests "org.example.repository.TokenRedisRepositoryTest"`

---

## TDD 사이클 3: JwtTokenProvider.getRemainingExpiration()

### 3-1. 껍데기 — 메서드 추가

**수정 파일:** `security/jwt/JwtTokenProvider.java`

```java
public long getRemainingExpiration(String token) {
    return 0L; // 껍데기
}
```

### 3-2. Red — JwtTokenProvider 단위 테스트

**파일:** `src/test/java/org/example/security/jwt/JwtTokenProviderTest.java`

```java
// 순수 단위 테스트 (Spring Context 없음)
// new JwtTokenProvider(secret, atExp, rtExp)로 직접 생성
//
// 테스트 케이스:
// 1. generateAccessToken() 후 getRemainingExpiration() — 양수 반환, atExp보다 작거나 같음
// 2. 만료된 토큰에 대해 getRemainingExpiration() — 예외 발생 (ExpiredJwtException)
// 3. 기존 메서드 회귀 테스트: validateToken() 정상/만료/변조
```

실행 → **Red**

### 3-3. Green — getRemainingExpiration() 구현

```java
public long getRemainingExpiration(String token) {
    Date expiration = parseClaims(token).getExpiration();
    return expiration.getTime() - System.currentTimeMillis();
}
```

### 실행: `./gradlew test --tests "org.example.security.jwt.JwtTokenProviderTest"`

---

## TDD 사이클 4: AuthService 수정 (login/logout/refresh Redis 전환)

### 4-1. 껍데기 — AuthService 인터페이스 수정

**수정 파일:** `service/AuthService.java`

```java
// 변경: logout 시그니처
void logout(Long userId, String accessToken);
```

### 4-2. Red — AuthServiceImpl 단위 테스트

**파일:** `src/test/java/org/example/service/AuthServiceImplTest.java`

```java
// 순수 단위 테스트: @Mock + @InjectMocks
// Mock 대상: AuthenticationManager, JwtTokenProvider, TokenRedisRepository, UserRepository
//
// 테스트 케이스:
//
// [login]
// 1. 정상 로그인 → tokenRedisRepository.saveRefreshToken() 호출 verify
// 2. 정상 로그인 → AT + RT 반환 확인
//
// [logout]
// 3. 정상 로그아웃 → tokenRedisRepository.deleteRefreshToken(userId) 호출 verify
// 4. 정상 로그아웃 → tokenRedisRepository.addToBlacklist(accessToken, remainingTtl) 호출 verify
//
// [refresh]
// 5. 유효한 RT → 새 AT + RT 발급, Redis에 새 RT 저장 verify
// 6. Redis에 없는 RT → IllegalArgumentException
// 7. Redis의 RT와 불일치 → IllegalArgumentException (RTR 방어)
```

실행 → **Red** (AuthServiceImpl은 아직 JPA Repository 사용 중)

### 4-3. Green — AuthServiceImpl 수정

**수정 파일:** `service/AuthServiceImpl.java`

- `RefreshTokenRepository` → `TokenRedisRepository` 교체
- `JwtTokenProvider` 의존성 주입 (이미 있음)
- `login()`: `tokenRedisRepository.saveRefreshToken(userId, refreshToken, rtExpSeconds)`
- `logout(Long userId, String accessToken)`:
  - `tokenRedisRepository.deleteRefreshToken(userId)`
  - `long remaining = jwtTokenProvider.getRemainingExpiration(accessToken)`
  - `tokenRedisRepository.addToBlacklist(accessToken, remaining)`
- `refresh()`:
  - `tokenRedisRepository.findRefreshToken(userId)` 조회
  - 저장된 값과 oldRefreshToken 비교 (RTR 방어)
  - 새 토큰 발급 후 Redis 갱신

### 4-4. 리팩토링

- RT 만료 시간 계산 로직을 별도 메서드로 추출

### 실행: `./gradlew test --tests "org.example.service.AuthServiceImplTest"`

---

## TDD 사이클 5: AuthController 수정 (logout에 AT 전달)

### 5-1. 껍데기 — 없음 (기존 파일 수정)

### 5-2. Red — AuthController WebMvcTest

**파일:** `src/test/java/org/example/controller/AuthControllerTest.java`

```java
// @WebMvcTest(AuthController.class)
// @MockitoBean: AuthService, JwtTokenProvider, UserService
// MockMvcTester + AssertJ 스타일
//
// 테스트 케이스:
//
// [logout]
// 1. @WithMockUser + Authorization 헤더 → authService.logout(userId, accessToken) 호출 verify
// 2. 응답에 Refresh-Token 쿠키 삭제(maxAge=0) 포함 확인
//
// [login]
// 3. 정상 로그인 요청 → 200 + TokenResponseDto 반환
// 4. 유효하지 않은 요청 (빈 username) → 422
//
// [refresh]
// 5. Refresh-Token 쿠키 포함 요청 → 200 + 새 토큰 반환
// 6. 쿠키 없는 요청 → 400
```

> 주의: `@WebMvcTest`는 Spring Security 필터를 로드하므로 `@WithMockUser`나
> `@AutoConfigureMockMvc(addFilters = false)` 사용 필요.
> AuthController의 `@AuthenticationPrincipal CustomUserDetails`를 테스트하려면
> Security context 설정 방법 고려.

실행 → **Red**

### 5-3. Green — AuthController 수정

**수정 파일:** `controller/AuthController.java`

```java
@PostMapping("/logout")
public ResponseEntity<String> logout(
    @AuthenticationPrincipal CustomUserDetails userDetails,
    HttpServletRequest request,
    HttpServletResponse response) {

    if (userDetails != null) {
        String accessToken = resolveToken(request);
        authService.logout(userDetails.getId(), accessToken);
    }
    // ... 쿠키 삭제 (기존 로직)
}
```

### 실행: `./gradlew test --tests "org.example.controller.AuthControllerTest"`

---

## TDD 사이클 6: JwtAuthenticationFilter Blacklist 체크

### 6-1. 껍데기 — 없음 (기존 파일 수정)

### 6-2. Red — JwtAuthenticationFilter 단위 테스트

**파일:** `src/test/java/org/example/security/jwt/JwtAuthenticationFilterTest.java`

```java
// 순수 단위 테스트 또는 @WebMvcTest
// Mock: JwtTokenProvider, CustomUserDetailsService, TokenRedisRepository
//
// 테스트 케이스:
// 1. 유효한 AT + Blacklist에 없음 → SecurityContext에 인증 정보 설정됨
// 2. 유효한 AT + Blacklist에 있음 → SecurityContext 비어있음 (인증 거부)
// 3. AT 없음 (shouldNotFilter 경로) → 필터 skip
```

실행 → **Red** (Filter에 TokenRedisRepository 의존성 없음)

### 6-3. Green — JwtAuthenticationFilter 수정

**수정 파일:** `security/jwt/JwtAuthenticationFilter.java`

- `TokenRedisRepository` 의존성 추가
- `doFilterInternal()` 수정:

```java
if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
    // Blacklist 체크
    if (tokenRedisRepository.isBlacklisted(token)) {
        filterChain.doFilter(request, response);
        return;
    }
    // ... 기존 UserDetails 로딩 + SecurityContext 설정
}
```

### 실행: `./gradlew test --tests "org.example.security.jwt.JwtAuthenticationFilterTest"`

---

## TDD 사이클 7: OAuth2AuthenticationSuccessHandler Redis 전환

### 7-1. 껍데기 — 없음 (기존 파일 수정)

### 7-2. Red — SuccessHandler 단위 테스트

**파일:** `src/test/java/org/example/security/oauth2/OAuth2AuthenticationSuccessHandlerTest.java`

```java
// 순수 단위 테스트: @Mock + @InjectMocks (또는 생성자 직접 생성)
// Mock: JwtTokenProvider, TokenRedisRepository, CookieOAuth2AuthorizationRequestRepository
//
// 테스트 케이스:
// 1. onAuthenticationSuccess() → tokenRedisRepository.saveRefreshToken() 호출 verify
// 2. onAuthenticationSuccess() → response에 Refresh-Token 쿠키 설정 확인
// 3. onAuthenticationSuccess() → redirect URL에 #accessToken= 포함 확인
```

실행 → **Red**

### 7-3. Green — SuccessHandler 수정

**수정 파일:** `security/oauth2/OAuth2AuthenticationSuccessHandler.java`

- `RefreshTokenRepository` → `TokenRedisRepository` 교체
- RT 저장 로직: JPA save → `tokenRedisRepository.saveRefreshToken()`

### 실행: `./gradlew test --tests "org.example.security.oauth2.OAuth2AuthenticationSuccessHandlerTest"`

---

## TDD 사이클 8: GlobalExceptionHandler 인증 예외 세분화

### 8-1. 껍데기 — 없음 (기존 파일 수정)

### 8-2. Red — GlobalExceptionHandler 테스트

**파일:** `src/test/java/org/example/exception/GlobalExceptionHandlerTest.java`

```java
// 순수 단위 테스트: new GlobalExceptionHandler() 직접 생성
//
// 테스트 케이스:
// 1. BadCredentialsException → 401
// 2. LockedException → 423
// 3. DisabledException → 403
// 4. 기존 RuntimeException → 400 (회귀 테스트)
```

실행 → **Red** (해당 핸들러 메서드 없음)

### 8-3. Green — 핸들러 메서드 추가

**수정 파일:** `exception/GlobalExceptionHandler.java`

```java
@ExceptionHandler(BadCredentialsException.class)
→ 401 + {"error": "Unauthorized", "message": "아이디 또는 비밀번호가 올바르지 않습니다."}

@ExceptionHandler(LockedException.class)
→ 423 + {"error": "Locked", "message": "계정이 잠겼습니다. 관리자에게 문의하세요."}

@ExceptionHandler(DisabledException.class)
→ 403 + {"error": "Forbidden", "message": "비활성화된 계정입니다."}
```

### 실행: `./gradlew test --tests "org.example.exception.GlobalExceptionHandlerTest"`

---

## 마무리: 제거 + 전체 검증

### 9-1. RefreshToken 엔티티 + Repository 제거

**삭제 파일:**
- `domain/entity/RefreshToken.java`
- `repository/RefreshTokenRepository.java`

### 9-2. 전체 테스트 실행

```bash
./gradlew test
```

모든 테스트 통과 확인 + 컴파일 에러 없는지 확인.

### 9-3. 수동 검증 (Docker 환경)

```bash
docker-compose up -d
./gradlew bootRun
```

1. `POST /login` → Redis에 RT 저장 확인 (`redis-cli GET RT:{userId}`)
2. `POST /logout` (Authorization: Bearer {AT}) → AT Blacklist 등록 확인
3. 로그아웃된 AT로 `GET /user/profile` → 401
4. `POST /refresh` (쿠키) → 새 AT/RT 발급, Redis 갱신 확인
5. `POST /signup` (role: "ROLE_ADMIN") → 무시되고 ROLE_USER 할당 확인

---

## 파일 변경 요약

| 순서 | 작업 | 파일 | 변경 유형 |
|------|------|------|-----------|
| 0 | 의존성 | `build.gradle` | 수정 |
| 0 | 테스트 설정 | `src/test/resources/application-test.yml` | 신규 |
| 1 | role 보안 수정 | `dto/request/SignupRequest.java` | 수정 |
| 1 | role 보안 수정 | `service/UserServiceImpl.java` | 수정 |
| 2 | Redis 인프라 | `config/RedisConfig.java` | 신규 |
| 2 | Redis 인프라 | `repository/TokenRedisRepository.java` | 신규 |
| 3 | JWT TTL | `security/jwt/JwtTokenProvider.java` | 수정 |
| 4 | Auth Redis전환 | `service/AuthService.java` | 수정 |
| 4 | Auth Redis전환 | `service/AuthServiceImpl.java` | 수정 |
| 5 | Controller | `controller/AuthController.java` | 수정 |
| 6 | Blacklist 필터 | `security/jwt/JwtAuthenticationFilter.java` | 수정 |
| 7 | OAuth2 Redis전환 | `security/oauth2/OAuth2AuthenticationSuccessHandler.java` | 수정 |
| 8 | 예외 세분화 | `exception/GlobalExceptionHandler.java` | 수정 |
| 9 | 제거 | `domain/entity/RefreshToken.java` | 삭제 |
| 9 | 제거 | `repository/RefreshTokenRepository.java` | 삭제 |

### 테스트 파일 (신규)

| 파일 | 테스트 유형 | 대상 |
|------|-------------|------|
| `service/UserServiceImplTest.java` | 단위 (@Mock) | role 보안 수정 |
| `repository/TokenRedisRepositoryTest.java` | 통합 (Testcontainers) | Redis RT/BL |
| `security/jwt/JwtTokenProviderTest.java` | 단위 (순수) | getRemainingExpiration |
| `service/AuthServiceImplTest.java` | 단위 (@Mock) | login/logout/refresh |
| `controller/AuthControllerTest.java` | 슬라이스 (@WebMvcTest) | 엔드포인트 |
| `security/jwt/JwtAuthenticationFilterTest.java` | 단위 (@Mock) | Blacklist 필터 |
| `security/oauth2/OAuth2AuthenticationSuccessHandlerTest.java` | 단위 (@Mock) | OAuth2 RT Redis |
| `exception/GlobalExceptionHandlerTest.java` | 단위 (순수) | 예외 세분화 |

---

## 구현 시작 시 첫 작업

1. **이 계획 파일의 내용을 `docs/plans/step4-A-implementation.md`에 저장한다.**
2. 기존 `docs/plans/step4-7-roadmap.md`는 전체 로드맵으로 유지.
3. 각 TDD 사이클 완료 시마다 `docs/plans/step4-A-implementation.md`에 진행 상태를 업데이트한다.
