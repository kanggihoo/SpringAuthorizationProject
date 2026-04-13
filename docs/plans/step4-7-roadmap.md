# Step 4 ~ 7 인증 시스템 구현 로드맵

## 현재 상태 (Step 3 완료 기준)

### 구현 완료
- JWT 무상태 인증 (AT/RT 분리 발급, RTR 패턴)
- Google OAuth2 로그인 + 자체 JWT 발급 (OCP 기반 추상화)
- Docker Compose (PostgreSQL 17-alpine + Redis)
- SecurityConfig: 일반 로그인 + OAuth2 로그인 동시 지원
- CustomUserDetails: enabled, accountNonLocked 필드 존재 (미활용)

### 현재 미흡한 점 (Step 4~7에서 해결)
| 문제 | 위치 | 위험도 |
|------|------|--------|
| RT가 PostgreSQL에 저장됨 (Redis 미사용) | `RefreshTokenRepository`, `RefreshToken` 엔티티 | 성능/설계 |
| AT Blacklist 미구현 — 로그아웃 후에도 AT 유효 | `AuthServiceImpl.logout()` | **보안 치명** |
| 회원가입 시 클라이언트가 `ROLE_ADMIN` 지정 가능 | `SignupRequest.role` 필드 | **보안 치명** |
| 잠긴 계정이 AT 만료 전까지 접근 가능 | `JwtAuthenticationFilter` | 보안 |
| OAuth2 AT가 URL Fragment로 노출 | `OAuth2AuthenticationSuccessHandler` | 보안 |
| 로그인 실패 횟수 제한 없음 | `AuthServiceImpl.login()` | 보안 |
| IP 기반 Brute Force 방어 없음 | 미구현 | 보안 |
| 인증 테스트 코드 없음 | `src/test/` | 품질 |

---

## Step 4-A: Redis 마이그레이션 + AT Blacklist

### 목표
RT 저장소를 PostgreSQL → Redis로 이전하고, 로그아웃된 AT를 Blacklist로 관리한다.

### 작업 목록

#### 1. RedisConfig 생성
**생성: `config/RedisConfig.java`**

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) { ... }
}
```
- `StringRedisTemplate`으로도 충분하나, 향후 확장을 위해 `RedisTemplate<String, String>` 빈 등록

#### 2. TokenRedisRepository 생성
**생성: `repository/TokenRedisRepository.java`**

Redis Key 설계:
```
RT:{userId}        →  refreshToken 문자열  (TTL: RT 유효기간, 예: 7일)
BL:{accessToken}   →  "logout"            (TTL: AT 잔여 유효시간)
```

메서드:
```java
void saveRefreshToken(Long userId, String refreshToken, long ttlSeconds);
Optional<String> findRefreshToken(Long userId);
void deleteRefreshToken(Long userId);

void addToBlacklist(String accessToken, long remainingTtlSeconds);
boolean isBlacklisted(String accessToken);
```

**왜 이 구조인가:**
- RT는 `userId` 기준 1:1이므로 String 타입으로 충분
- Blacklist는 AT 문자열 자체가 키 — 만료되면 자동 삭제되어 저장 공간이 무한히 쌓이지 않음
- DB 엔티티(`RefreshToken`)와 JPA Repository 대비 조회 성능이 O(1)

#### 3. JwtTokenProvider 메서드 추가
**수정: `security/jwt/JwtTokenProvider.java`**

```java
/**
 * 토큰의 남은 유효시간(밀리초) 반환.
 * Blacklist TTL 계산에 사용.
 */
public long getRemainingExpiration(String token) {
    Date expiration = parseClaims(token).getExpiration();
    return expiration.getTime() - System.currentTimeMillis();
}
```

#### 4. AuthServiceImpl 수정
**수정: `service/AuthServiceImpl.java`**

변경 사항:
- `RefreshTokenRepository` (JPA) → `TokenRedisRepository` (Redis)로 교체
- `login()`: Redis에 RT 저장
- `logout(Long userId, String accessToken)`: Redis RT 삭제 + AT Blacklist 등록
- `refresh(String oldRefreshToken)`: Redis에서 RT 조회/검증/교체

`logout()` 시그니처 변경:
```java
// 변경 전
void logout(Long userId);

// 변경 후
void logout(Long userId, String accessToken);
```

#### 5. AuthController 수정
**수정: `controller/AuthController.java`**

`logout()` 엔드포인트에서 Authorization 헤더의 AT를 추출하여 `authService.logout()`에 전달:
```java
@PostMapping("/logout")
public ResponseEntity<String> logout(
    @AuthenticationPrincipal CustomUserDetails userDetails,
    HttpServletRequest request,
    HttpServletResponse response) {

    String accessToken = resolveToken(request);  // Bearer 제거 후 토큰 추출
    authService.logout(userDetails.getId(), accessToken);
    // ... 쿠키 삭제
}
```

#### 6. JwtAuthenticationFilter 수정
**수정: `security/jwt/JwtAuthenticationFilter.java`**

`doFilterInternal()` 상단에 Blacklist 체크 추가:
```java
if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
    // Blacklist 체크 (로그아웃된 토큰 차단)
    if (tokenRedisRepository.isBlacklisted(token)) {
        filterChain.doFilter(request, response);
        return;  // SecurityContext에 인증 정보를 넣지 않음 → 401
    }
    // ... 기존 로직
}
```

#### 7. OAuth2AuthenticationSuccessHandler 수정
**수정: `security/oauth2/OAuth2AuthenticationSuccessHandler.java`**

`RefreshTokenRepository` (JPA) → `TokenRedisRepository` (Redis)로 교체.
기존 RT DB 저장 로직을 Redis 저장으로 변경.

#### 8. RefreshToken 엔티티 + Repository 제거
**삭제:**
- `domain/entity/RefreshToken.java`
- `repository/RefreshTokenRepository.java`
- DB `refresh_tokens` 테이블 불필요 (Redis가 TTL 자동 관리)

#### 9. [보안] SignupRequest role 클라이언트 지정 차단
**수정: `dto/request/SignupRequest.java`**

`role` 필드 제거. `UserServiceImpl.signup()`에서 서버 측에서 강제로 `ROLE_USER` 지정:
```java
// 변경 전: 클라이언트가 전달한 role 사용
Role role = roleRepository.findByName(signupRequest.getRole()) ...

// 변경 후: 서버에서 강제 지정
Role role = roleRepository.findByName("ROLE_USER")
    .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
```

### 검증 방법
1. `POST /login` → RT가 Redis에 저장되는지 확인 (`redis-cli GET RT:{userId}`)
2. `POST /logout` → AT가 Blacklist에 등록되는지 확인 (`redis-cli GET BL:{token}`)
3. 로그아웃 후 동일 AT로 API 호출 → 401 확인
4. `POST /refresh` → 새 RT가 Redis에 갱신되는지 확인
5. RT TTL이 만료되면 자동 삭제되는지 확인
6. 회원가입 시 `role: "ROLE_ADMIN"` 전송 → 무시되고 ROLE_USER 할당 확인

---

## Step 4-B: 계정 잠금 & 관리자 API

### 목표
로그인 실패 횟수 초과 시 계정을 자동 잠금하고, 관리자가 해제할 수 있다.

### 작업 목록

#### 1. User 엔티티 확장
**수정: `domain/entity/User.java`**

추가 필드:
```java
/** 연속 로그인 실패 횟수 (잠금 임계치: 5회) */
private int failureCount = 0;

/** 계정 잠금 시각 — 시간 기반 자동 해제 판단 기준 */
private LocalDateTime lockedAt;
```

비즈니스 메서드:
```java
/** 로그인 실패 시 호출. 5회 초과 시 자동 잠금 */
public void incrementFailureCount() {
    this.failureCount++;
    if (this.failureCount >= 5) {
        this.accountNonLocked = false;
        this.lockedAt = LocalDateTime.now();
    }
}

/** 로그인 성공 시 실패 횟수 초기화 */
public void resetFailureCount() {
    this.failureCount = 0;
}

/** 관리자에 의한 잠금 해제 */
public void unlock() {
    this.accountNonLocked = true;
    this.failureCount = 0;
    this.lockedAt = null;
}
```

#### 2. AuthServiceImpl 수정 — 실패 카운트 처리
**수정: `service/AuthServiceImpl.java`**

`login()` 메서드에 try-catch 추가:
```java
try {
    Authentication authentication = authenticationManager.authenticate(...);

    // 성공 시 실패 횟수 초기화
    User user = userRepository.findByUsername(requestDto.getUsername()).get();
    user.resetFailureCount();
    // ... 기존 토큰 발급 로직

} catch (BadCredentialsException e) {
    // 실패 시 카운트 증가
    User user = userRepository.findByUsername(requestDto.getUsername())
        .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
    user.incrementFailureCount();
    userRepository.save(user);
    throw e;

} catch (LockedException e) {
    throw e;  // 잠긴 계정은 GlobalExceptionHandler에서 처리
}
```

#### 3. JwtAuthenticationFilter — 잠금 계정 재확인
**수정: `security/jwt/JwtAuthenticationFilter.java`**

**왜 필요한가:** AT 발급 후 계정이 잠겨도 AT 만료 전까지 접근 가능한 문제.
`loadUserByUsername()` 호출 후 잠금/비활성화 상태 명시적 체크:
```java
UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

// 잠금/비활성화 계정의 AT 즉시 차단
if (!userDetails.isAccountNonLocked() || !userDetails.isEnabled()) {
    filterChain.doFilter(request, response);
    return;
}
```

#### 4. GlobalExceptionHandler 확장
**수정: `exception/GlobalExceptionHandler.java`**

인증 관련 예외 세분화:
```java
@ExceptionHandler(BadCredentialsException.class)
→ 401 "아이디 또는 비밀번호가 올바르지 않습니다."

@ExceptionHandler(LockedException.class)
→ 423 "계정이 잠겼습니다. 관리자에게 문의하세요."

@ExceptionHandler(DisabledException.class)
→ 403 "비활성화된 계정입니다."
```

현재 `RuntimeException` 하나로 모든 400 에러를 처리하는 구조에서 인증 예외를 분리.

#### 5. AdminController 생성
**생성: `controller/AdminController.java`**

```
POST /admin/users/{userId}/unlock   — 계정 잠금 해제
GET  /admin/users/{userId}          — 특정 유저 상세 조회 (잠금 상태, 실패 횟수 등)
```

SecurityConfig에서 `/admin/**`에 `hasRole("ADMIN")` 이미 설정 완료.

#### 6. AdminService 생성
**생성: `service/AdminService.java`, `service/AdminServiceImpl.java`**

```java
public interface AdminService {
    void unlockUser(Long userId);
    UserDetailDto getUser(Long userId);
}
```

#### 7. DTO 생성
**생성: `dto/response/UserDetailDto.java`**

```java
// 관리자용 유저 상세 정보 응답
Long id, String username, String nickname, String email,
AuthProvider provider, boolean enabled, boolean accountNonLocked,
int failureCount, LocalDateTime lockedAt, List<String> roles
```

### 검증 방법
1. 잘못된 비밀번호로 5회 로그인 시도 → 5회째에 잠금 응답(423) 확인
2. 잠긴 계정으로 로그인 시도 → 423 확인
3. 잠금 전에 발급된 AT로 API 호출 → 401 확인 (JwtAuthenticationFilter 체크)
4. `POST /admin/users/{userId}/unlock` → 잠금 해제 확인
5. 해제 후 정상 로그인 가능한지 확인
6. ROLE_USER로 `/admin/**` 접근 → 403 확인

---

## Step 5: RBAC + Method Security

### 목표
URL 패턴 수준을 넘어, 서비스 메서드 레벨에서 세밀한 인가 검증을 적용한다.
사용자 정보 수정 시 SecurityContext를 즉시 갱신한다.

### 작업 목록

#### 1. Method Security 활성화
**수정: `config/SecurityConfig.java`**

```java
@EnableMethodSecurity(prePostEnabled = true)
```

#### 2. UserController 생성
**생성: `controller/UserController.java`**

기존 `TestController`의 플레이스홀더 엔드포인트를 실제 구현으로 교체:
```
GET    /user/profile     — 본인 프로필 조회
PATCH  /user/profile     — 본인 정보 수정 (nickname)
PATCH  /user/password    — 비밀번호 변경
DELETE /user/withdraw    — 본인 탈퇴
```

#### 3. UserService 확장 + @PreAuthorize 적용
**수정: `service/UserService.java`, `service/UserServiceImpl.java`**

```java
/** 본인 프로필 조회 */
UserProfileDto getProfile(Long userId);

/**
 * 본인 정보 수정 (닉네임 등).
 * 수정 후 SecurityContext의 CustomUserDetails를 재로딩하여 즉시 반영.
 */
void updateProfile(Long userId, UpdateProfileRequest request);

/**
 * 비밀번호 변경.
 * 변경 후 해당 userId의 Redis RT를 강제 삭제하여 기존 세션을 무효화.
 */
void changePassword(Long userId, ChangePasswordRequest request);

/** 본인 탈퇴 — 관련 RT 삭제 포함 */
void withdraw(Long userId);
```

#### 4. 비밀번호 변경 시 RT 강제 만료
**수정: `service/UserServiceImpl.java`**

**왜 필요한가:**
비밀번호를 변경했다는 것은 기존 인증이 더 이상 유효하지 않다는 뜻.
다른 기기에서 로그인한 세션(RT)이 살아있으면 보안 사고.

```java
public void changePassword(Long userId, ChangePasswordRequest request) {
    // 1. 현재 비밀번호 검증
    // 2. 새 비밀번호 저장
    // 3. Redis RT 강제 삭제 → 모든 기기에서 재로그인 필요
    tokenRedisRepository.deleteRefreshToken(userId);
}
```

#### 5. SecurityContext 즉시 갱신
**수정: `service/UserServiceImpl.java`**

README 명시 요구사항: "정보 수정 시 즉시 DB와 SecurityContext에 반영"
```java
public void updateProfile(Long userId, UpdateProfileRequest request) {
    // 1. DB 업데이트
    User user = userRepository.findById(userId).orElseThrow(...);
    user.updateNickname(request.getNickname());

    // 2. SecurityContext 재로딩
    UserDetails updatedDetails = customUserDetailsService.loadUserByUsername(user.getUsername());
    UsernamePasswordAuthenticationToken newAuth =
        new UsernamePasswordAuthenticationToken(updatedDetails, null, updatedDetails.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(newAuth);
}
```

#### 6. DTO 생성
**생성:**
- `dto/response/UserProfileDto.java` — 본인 프로필 응답
- `dto/request/UpdateProfileRequest.java` — 닉네임 수정 요청
- `dto/request/ChangePasswordRequest.java` — 비밀번호 변경 요청 (currentPassword, newPassword)

### 검증 방법
1. `GET /user/profile` — 본인 정보 정상 조회
2. `PATCH /user/profile` → 닉네임 변경 후 다음 API 응답에 반영되는지 확인
3. `PATCH /user/password` → 비밀번호 변경 후 기존 RT로 `/refresh` 호출 → 실패 확인
4. 변경된 비밀번호로 재로그인 → 성공 확인
5. `DELETE /user/withdraw` → 탈퇴 후 해당 AT/RT 무효화 확인
6. ROLE_USER로 타인의 프로필 수정 시도 → 403 확인

---

## Step 6: 이벤트 기반 보안 알림

### 목표
계정 잠금, 로그인 실패 등 보안 이벤트 발생 시 비동기로 알림을 처리한다.
Spring의 ApplicationEventPublisher를 활용하여 인증 로직과 알림 로직을 분리한다.

### 작업 목록

#### 1. 비동기 설정
**생성: `config/AsyncConfig.java`**

```java
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("security-event-");
        executor.initialize();
        return executor;
    }
}
```

#### 2. 보안 이벤트 클래스 정의
**생성: `domain/event/`**

```java
/** 로그인 실패 이벤트 — 사용자에게 경고 알림 목적 */
public record LoginFailedEvent(
    String username, String email,
    int currentFailureCount, String ipAddress,
    LocalDateTime occurredAt
) {}

/** 계정 잠금 이벤트 — 즉시 알림 필요 */
public record AccountLockedEvent(
    Long userId, String username, String email,
    LocalDateTime lockedAt
) {}

/** 비밀번호 변경 이벤트 — 본인이 아닌 경우 탈취 경고 */
public record PasswordChangedEvent(
    Long userId, String username, String email,
    LocalDateTime changedAt
) {}
```

#### 3. 이벤트 발행 — AuthServiceImpl, UserServiceImpl 수정
**수정: `service/AuthServiceImpl.java`**

```java
@Autowired ApplicationEventPublisher eventPublisher;

// login() 실패 시
eventPublisher.publishEvent(new LoginFailedEvent(username, email, count, ip, now));

// 잠금 발생 시
eventPublisher.publishEvent(new AccountLockedEvent(userId, username, email, now));
```

**수정: `service/UserServiceImpl.java`**
```java
// 비밀번호 변경 시
eventPublisher.publishEvent(new PasswordChangedEvent(userId, username, email, now));
```

#### 4. 이벤트 리스너
**생성: `listener/SecurityEventListener.java`**

```java
@Slf4j
@Component
public class SecurityEventListener {

    @Async
    @EventListener
    public void onLoginFailed(LoginFailedEvent event) {
        log.warn("[보안] 로그인 실패 - user: {}, count: {}, ip: {}",
            event.username(), event.currentFailureCount(), event.ipAddress());
        // 이메일 알림 발송 (email 존재 시)
    }

    @Async
    @EventListener
    public void onAccountLocked(AccountLockedEvent event) {
        log.error("[보안] 계정 잠금 - user: {}, at: {}", event.username(), event.lockedAt());
        // 이메일 알림 발송
    }

    @Async
    @EventListener
    public void onPasswordChanged(PasswordChangedEvent event) {
        log.info("[보안] 비밀번호 변경 - user: {}", event.username());
        // "본인이 변경한 것이 아니라면 즉시 연락하세요" 이메일 발송
    }
}
```

#### 5. (선택) 이메일 발송 구성
**의존성:** `spring-boot-starter-mail`

개발 환경에서는 MailHog(Docker)를 사용하거나, 로그 출력으로 대체 가능.
OAuth2 유저는 email 필드가 있으므로 알림 발송 대상에 포함.
LOCAL 유저 중 email이 null인 경우 → 로그만 남기고 skip.

### 검증 방법
1. 로그인 실패 시 로그에 `[보안] 로그인 실패` 출력 확인
2. 5회 초과 실패 시 `[보안] 계정 잠금` 로그 확인
3. 비밀번호 변경 시 `[보안] 비밀번호 변경` 로그 확인
4. `@Async`로 인해 메인 스레드가 블로킹되지 않는지 확인 (스레드 이름 `security-event-` 접두사)

---

## Step 7-A: Rate Limiting (IP 기반 Brute Force 방어)

### 목표
동일 IP에서 짧은 시간에 반복되는 로그인 시도를 차단한다.
계정 잠금(Step 4-B)이 "계정 단위" 방어라면, Rate Limiting은 "IP 단위" 방어로 별개의 방어선.

### 왜 필요한가
계정 잠금만으로는 방어 불충분:
- 공격자가 다수 계정을 돌아가며 시도하면 개별 계정의 잠금 임계치에 도달하지 않음
- 존재하지 않는 username으로 시도하면 `failureCount` 자체가 기록되지 않음
- 잠금을 유도하는 DoS 공격 가능 (정상 사용자의 계정을 의도적으로 잠그기)

### 작업 목록

#### 1. Redis 기반 Rate Limiter
**생성: `security/RateLimiter.java`**

Redis Key 설계:
```
RATE:{ip}:{endpoint}  →  요청 횟수 (int)  (TTL: 윈도우 크기, 예: 1분)
```

```java
/**
 * 지정된 윈도우(초) 내에 maxAttempts를 초과하면 true 반환.
 * 예: 1분 내 20회 초과 시 차단
 */
public boolean isRateLimited(String ip, String endpoint, int maxAttempts, int windowSeconds);
```

#### 2. 로그인 엔드포인트에 적용
**수정: `controller/AuthController.java` 또는 별도 Filter**

적용 방식 2가지 중 선택:
- **방식 A (Controller)**: `login()` 메서드 상단에서 체크, 초과 시 429 반환
- **방식 B (Filter)**: `RateLimitFilter`를 만들어 `/login`, `/signup`, `/refresh`에 적용

```java
if (rateLimiter.isRateLimited(request.getRemoteAddr(), "/login", 20, 60)) {
    return ResponseEntity.status(429).body("너무 많은 요청입니다. 잠시 후 다시 시도하세요.");
}
```

#### 3. GlobalExceptionHandler 확장
**수정: `exception/GlobalExceptionHandler.java`**

```java
@ExceptionHandler(RateLimitExceededException.class)
→ 429 "Too Many Requests"
```

### 설정 가이드라인
| 엔드포인트 | 윈도우 | 최대 횟수 | 근거 |
|-----------|--------|----------|------|
| `/login` | 1분 | 10회 | 로그인 시도 제한 |
| `/signup` | 1분 | 5회 | 계정 대량 생성 방지 |
| `/refresh` | 1분 | 30회 | 토큰 갱신은 자동 호출이므로 여유 있게 |

### 검증 방법
1. 동일 IP에서 `/login` 11회 연속 호출 → 11회째 429 확인
2. 1분 경과 후 → 다시 호출 가능 확인
3. 다른 IP에서는 제한 없이 호출 가능 확인

---

## Step 7-B: OAuth2 One-time Code 교환 방식

### 목표
OAuth2 로그인 성공 시 AT를 URL에 직접 노출하지 않고, 일회성 단기 코드를 발급한 뒤
프론트엔드가 `POST /oauth2/token` 엔드포인트로 교환하는 방식으로 전환한다.

### 왜 필요한가
현재 `#accessToken=...` Fragment 방식의 문제:
- 브라우저 히스토리에 잔존 가능
- Fragment는 서버 로그에 남지 않지만 클라이언트 사이드 로깅에는 남을 수 있음
- 실질적인 Authorization Code Flow의 마지막 구간이 평문 토큰 전달

### 변경 흐름
```
현재:
Google → /login/oauth2/code/google → SuccessHandler → 302 redirect#accessToken=...

변경 후:
Google → /login/oauth2/code/google → SuccessHandler → 302 redirect?code={oneTimeCode}
프론트 → POST /oauth2/token { code: "..." } → 200 { accessToken, tokenType } + RT Cookie
```

### 작업 목록

#### 1. SuccessHandler 수정
**수정: `security/oauth2/OAuth2AuthenticationSuccessHandler.java`**

```java
// 변경 전
String targetUrl = redirectUri + "#accessToken=" + accessToken;

// 변경 후
String oneTimeCode = UUID.randomUUID().toString();
// Redis에 코드 저장: CODE:{code} → {userId}:{accessToken}:{refreshToken}  TTL 30초
tokenRedisRepository.saveOneTimeCode(oneTimeCode, userId, accessToken, refreshToken, 30);
String targetUrl = redirectUri + "?code=" + oneTimeCode;
```

#### 2. TokenRedisRepository 확장
**수정: `repository/TokenRedisRepository.java`**

```java
void saveOneTimeCode(String code, Long userId, String accessToken, String refreshToken, long ttlSeconds);
Optional<TokenExchangeData> consumeOneTimeCode(String code);  // 조회 후 즉시 삭제 (1회용)
```

Redis Key:
```
CODE:{uuid}  →  JSON {userId, accessToken, refreshToken}  (TTL: 30초)
```

#### 3. OAuth2 토큰 교환 엔드포인트
**수정: `controller/AuthController.java`**

```java
@PostMapping("/oauth2/token")
public ResponseEntity<TokenResponseDto> exchangeOAuth2Code(
    @RequestParam String code,
    HttpServletResponse response) {

    TokenExchangeData data = tokenRedisRepository.consumeOneTimeCode(code)
        .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 코드입니다."));

    // RT를 쿠키에 설정
    setRefreshTokenCookie(response, data.getRefreshToken());

    return ResponseEntity.ok(TokenResponseDto.builder()
        .accessToken(data.getAccessToken())
        .tokenType("Bearer")
        .build());
}
```

SecurityConfig에서 `/oauth2/token` 경로 `permitAll()` 추가.

#### 4. 프론트엔드 수정
**수정: `frontend/`**

콜백 페이지에서:
```javascript
// 변경 전: URL Fragment에서 AT 추출
const accessToken = window.location.hash.split('accessToken=')[1];

// 변경 후: Query Param의 code로 AT 교환
const code = new URLSearchParams(window.location.search).get('code');
const response = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `code=${code}`
});
const { accessToken } = await response.json();
```

### 검증 방법
1. Google OAuth2 로그인 → 리다이렉트 URL에 `?code=...`만 존재, AT 미노출 확인
2. `POST /oauth2/token?code=...` → AT 정상 수신 확인
3. 동일 code로 재요청 → "유효하지 않은 코드" 에러 확인 (1회용)
4. 30초 경과 후 code 사용 → "만료된 코드" 에러 확인

---

## Step 7-C: Spring Security 인증 테스트 코드

### 목표
인증 시스템의 핵심 시나리오를 테스트 코드로 검증하여 Step 4~7 작업 중 regression을 방지한다.

### 테스트 시나리오

#### 인증 (Authentication)
```
[AT 없이 보호 경로 접근]     GET /user/profile (no token)      → 401
[유효한 AT로 접근]            GET /user/profile (valid AT)      → 200
[만료된 AT로 접근]            GET /user/profile (expired AT)    → 401
[Blacklist AT로 접근]         GET /user/profile (logged-out AT) → 401
[잠긴 계정 AT로 접근]         GET /user/profile (locked user)   → 401
```

#### 인가 (Authorization)
```
[ROLE_USER → ADMIN 경로]     GET /admin/manage (ROLE_USER)     → 403
[ROLE_ADMIN → ADMIN 경로]    GET /admin/manage (ROLE_ADMIN)    → 200
[본인 프로필 수정]             PATCH /user/profile (own)         → 200
```

#### 계정 잠금
```
[5회 실패 → 잠금]             POST /login (wrong pw × 5)       → 423
[잠금 후 올바른 비밀번호]       POST /login (correct pw)         → 423
[관리자 잠금 해제 후 로그인]    POST /admin/.../unlock → login   → 200
```

#### Rate Limiting
```
[한도 내 요청]                POST /login × 10                 → 200 (또는 401)
[한도 초과]                   POST /login × 11                 → 429
```

#### 토큰 갱신
```
[유효한 RT로 갱신]            POST /refresh (valid RT cookie)   → 200 + 새 AT/RT
[사용된 RT로 재갱신]          POST /refresh (old RT)            → 401 (RTR 위반)
[비밀번호 변경 후 RT 갱신]     POST /refresh (after pw change)  → 401
```

### 기술 구성
- `@SpringBootTest` + `@AutoConfigureMockMvc`
- `@Testcontainers` (PostgreSQL + Redis) — 실제 인프라와 동일한 환경
- `@WithMockUser` — 인가 테스트 간소화

### 검증 방법
- `./gradlew test` 전체 통과 확인
- Step별 작업 완료 후 테스트 실행하여 regression 없는지 확인

---

## 단계 간 의존 관계

```
Step 4-A (Redis)  ──────────────────────→  필수 선행
    │
    ├──→ Step 4-B (계정 잠금)
    │        │
    │        ├──→ Step 5 (RBAC + Method Security)
    │        │
    │        └──→ Step 6 (이벤트 알림) ← Step 5의 비밀번호 변경 이벤트 포함
    │
    ├──→ Step 7-A (Rate Limiting) ← Step 4-B와 독립 (병행 가능)
    │
    └──→ Step 7-B (One-time Code) ← Step 4-A Redis 필요
                                     Step 5와 독립 (병행 가능)

Step 7-C (테스트) ← 모든 Step 완료 후 또는 각 Step마다 점진적 추가
```

---

## 구현 순서 요약

| 순서 | Step | 핵심 내용 | 새로 생성하는 파일 |
|------|------|-----------|--------------------|
| 1 | 4-A | Redis RT + AT Blacklist | `RedisConfig`, `TokenRedisRepository` |
| 2 | 4-B | 계정 잠금 + Admin API | `AdminController`, `AdminService(Impl)`, `UserDetailDto` |
| 3 | 5 | @PreAuthorize + 프로필 관리 | `UserController`, `UserProfileDto`, `ChangePasswordRequest`, `UpdateProfileRequest` |
| 4 | 6 | 비동기 보안 이벤트 | `AsyncConfig`, `LoginFailedEvent`, `AccountLockedEvent`, `PasswordChangedEvent`, `SecurityEventListener` |
| 5 | 7-A | IP Rate Limiting | `RateLimiter` (+ `RateLimitFilter` 선택) |
| 6 | 7-B | OAuth2 One-time Code | `TokenExchangeData` + 기존 파일 수정 |
| 7 | 7-C | 인증 테스트 코드 | `AuthenticationTest`, `AuthorizationTest`, `AccountLockTest`, `RateLimitTest` |
