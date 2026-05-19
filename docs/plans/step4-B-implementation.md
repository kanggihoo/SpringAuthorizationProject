# Step 4-B 구현 계획: 계정 잠금 & 관리자 API

## Context

Step 4-A(Redis 마이그레이션 + AT Blacklist)가 완료된 상태를 전제로 한다.
현재 `User` 엔티티에 `accountNonLocked`, `enabled` 필드가 존재하지만 실제 잠금 로직이 없다.
로그인 실패 횟수 제한이 없어 Brute Force 공격에 취약하고, 잠긴 계정의 AT가 만료 전까지 유효한 문제가 있다.

**해결할 문제:**
1. 로그인 5회 연속 실패 시 계정 자동 잠금
2. 잠긴 계정의 AT를 JwtAuthenticationFilter에서 즉시 차단
3. 관리자(ADMIN)가 잠금 해제할 수 있는 API 제공
4. 인증 예외(BadCredentials, Locked, Disabled)를 적절한 HTTP 상태 코드로 세분화

**전제 조건 (Step 4-A 완료):**
- `TokenRedisRepository` 사용 중 (RT Redis 저장, AT Blacklist)
- `AuthService.logout(Long userId, String accessToken)` 시그니처
- `JwtAuthenticationFilter`에 Blacklist 체크 로직 존재
- `SignupRequest`에서 role 필드 제거 완료
- `RefreshToken` 엔티티/Repository 삭제 완료

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
- `@MockitoBean` (import: `org.springframework.test.context.bean.override.mockito.MockitoBean`)
- `MockMvcTester` + AssertJ 스타일 assertions
- Service 테스트: 순수 단위 테스트 (Mockito `@Mock` + `@InjectMocks`)
- Controller 테스트: `@WebMvcTest` + `MockMvcTester` + `@MockitoBean`

---

## TDD 사이클 1: User 엔티티 잠금 필드 + 비즈니스 메서드

### 1-1. 껍데기 — User 엔티티 확장

**수정 파일:** `src/main/java/org/example/domain/entity/User.java`

추가할 필드:
```java
/** 연속 로그인 실패 횟수. 잠금 임계치: 5회 */
@Column(nullable = false)
private int failureCount = 0;

/** 계정 잠금 시각. 시간 기반 자동 해제 판단 기준. null이면 잠금 이력 없음 */
@Column(nullable = true)
private LocalDateTime lockedAt;
```

추가할 메서드 (빈 구현):
```java
public void incrementFailureCount() { }
public void resetFailureCount() { }
public void unlock() { }
```

### 1-2. Red — User 엔티티 단위 테스트

**파일:** `src/test/java/org/example/domain/entity/UserTest.java`

```java
// 순수 단위 테스트 (Spring Context 없음)
// User 객체를 직접 생성하여 비즈니스 메서드 검증
//
// 테스트 케이스:
// 1. incrementFailureCount() 1회 호출 → failureCount == 1, accountNonLocked == true
// 2. incrementFailureCount() 5회 호출 → failureCount == 5, accountNonLocked == false, lockedAt != null
// 3. resetFailureCount() → failureCount == 0 (accountNonLocked 변경 안 됨)
// 4. unlock() → accountNonLocked == true, failureCount == 0, lockedAt == null
// 5. 초기 상태 검증 → failureCount == 0, accountNonLocked == true, lockedAt == null
```

### 1-3. Green — 비즈니스 메서드 구현

```java
/** 로그인 실패 시 호출. 5회 도달 시 자동 잠금 */
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

### 실행: `./gradlew test --tests "org.example.domain.entity.UserTest"`

---

## TDD 사이클 2: AuthServiceImpl 로그인 실패 카운트

### 2-1. 껍데기 — 없음 (기존 파일 수정)

Step 4-A에서 AuthServiceImpl은 이미 `TokenRedisRepository`를 사용 중.
`login()` 메서드에 try-catch 구조를 추가할 준비.

### 2-2. Red — AuthServiceImpl 로그인 실패 테스트

**파일:** `src/test/java/org/example/service/AuthServiceImplTest.java` (Step 4-A에서 생성됨, 추가)

```java
// 기존 Step 4-A 테스트에 추가할 테스트 케이스:
//
// [login 실패 처리]
// 1. 잘못된 비밀번호 → BadCredentialsException 발생 + user.incrementFailureCount() 호출
// 2. 잘못된 비밀번호 → userRepository.save(user) 호출 verify (실패 횟수 저장)
// 3. 존재하지 않는 username → UsernameNotFoundException, incrementFailureCount 미호출
//
// [login 성공 시 초기화]
// 4. 정상 로그인 → user.resetFailureCount() 호출
//
// [잠긴 계정 로그인 시도]
// 5. accountNonLocked == false인 유저 → LockedException 발생
```

### 2-3. Green — AuthServiceImpl.login() 수정

**수정 파일:** `src/main/java/org/example/service/AuthServiceImpl.java`

```java
@Override
@Transactional
public TokenResponseDto login(LoginRequestDto requestDto) {
    // 1. 사용자 존재 여부 확인 (실패 카운트 증가를 위해 먼저 조회)
    User user = userRepository.findByUsername(requestDto.getUsername())
        .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

    // 2. 잠긴 계정 체크
    if (!user.isAccountNonLocked()) {
        throw new LockedException("계정이 잠겼습니다. 관리자에게 문의하세요.");
    }

    try {
        // 3. 인증 처리
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                requestDto.getUsername(), requestDto.getPassword()));

        // 4. 성공 시 실패 횟수 초기화
        user.resetFailureCount();

        // 5. 토큰 발급 (기존 로직)
        // ...

    } catch (BadCredentialsException e) {
        // 6. 실패 시 카운트 증가 + 저장
        user.incrementFailureCount();
        userRepository.save(user);
        throw e;
    }
}
```

### 실행: `./gradlew test --tests "org.example.service.AuthServiceImplTest"`

---

## TDD 사이클 3: JwtAuthenticationFilter 잠금 계정 차단

### 3-1. 껍데기 — 없음 (기존 파일 수정)

### 3-2. Red — JwtAuthenticationFilter 잠금 체크 테스트

**파일:** `src/test/java/org/example/security/jwt/JwtAuthenticationFilterTest.java` (Step 4-A에서 생성됨, 추가)

```java
// 기존 Step 4-A 테스트에 추가할 테스트 케이스:
//
// 1. 유효한 AT + 잠긴 계정(isAccountNonLocked=false) → SecurityContext 비어있음
// 2. 유효한 AT + 비활성화 계정(isEnabled=false) → SecurityContext 비어있음
// 3. 유효한 AT + 정상 계정 → SecurityContext에 인증 정보 설정됨 (회귀 테스트)
```

### 3-3. Green — JwtAuthenticationFilter 수정

**수정 파일:** `src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java`

```java
// Step 4-A에서 추가된 Blacklist 체크 이후에 추가:
UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

// 잠금/비활성화 계정의 AT 즉시 차단
// (AT 발급 후 계정이 잠겨도 만료 전까지 접근 가능한 문제 해결)
if (!userDetails.isAccountNonLocked() || !userDetails.isEnabled()) {
    filterChain.doFilter(request, response);
    return;
}

// 기존 SecurityContext 설정 로직...
```

### 실행: `./gradlew test --tests "org.example.security.jwt.JwtAuthenticationFilterTest"`

---

## TDD 사이클 4: GlobalExceptionHandler 인증 예외 세분화

> Step 4-A 계획에도 이 항목이 있지만, Step 4-B에서 실제 LockedException을 던지는 코드가 생기므로
> 여기서 함께 구현하는 것이 TDD 흐름에 자연스럽다.
> Step 4-A 구현 시 이미 완료되었다면 이 사이클은 skip.

### 4-1. 껍데기 — 없음 (기존 파일 수정)

### 4-2. Red — GlobalExceptionHandler 테스트

**파일:** `src/test/java/org/example/exception/GlobalExceptionHandlerTest.java` (Step 4-A에서 생성됨 또는 신규)

```java
// 순수 단위 테스트: new GlobalExceptionHandler() 직접 생성
//
// 테스트 케이스:
// 1. BadCredentialsException → 401 + {"error": "Unauthorized", "message": "..."}
// 2. LockedException → 423 + {"error": "Locked", "message": "..."}
// 3. DisabledException → 403 + {"error": "Forbidden", "message": "..."}
// 4. 기존 RuntimeException → 400 (회귀 테스트)
```

### 4-3. Green — 핸들러 메서드 추가

**수정 파일:** `src/main/java/org/example/exception/GlobalExceptionHandler.java`

```java
/**
 * 로그인 실패(잘못된 비밀번호) 예외 처리.
 * AuthServiceImpl.login()에서 BadCredentialsException 발생 시 호출.
 */
@ExceptionHandler(BadCredentialsException.class)
public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException e) {
    // → 401 + {"error": "Unauthorized", "message": "아이디 또는 비밀번호가 올바르지 않습니다."}
}

/**
 * 계정 잠금 예외 처리.
 * 로그인 5회 실패 후 잠긴 계정으로 로그인 시도 시 호출.
 */
@ExceptionHandler(LockedException.class)
public ResponseEntity<Map<String, String>> handleLocked(LockedException e) {
    // → 423 + {"error": "Locked", "message": "계정이 잠겼습니다. 관리자에게 문의하세요."}
}

/**
 * 비활성화 계정 예외 처리.
 * 관리자가 비활성화한 계정으로 로그인 시도 시 호출.
 */
@ExceptionHandler(DisabledException.class)
public ResponseEntity<Map<String, String>> handleDisabled(DisabledException e) {
    // → 403 + {"error": "Forbidden", "message": "비활성화된 계정입니다."}
}
```

### 실행: `./gradlew test --tests "org.example.exception.GlobalExceptionHandlerTest"`

---

## TDD 사이클 5: AdminService + AdminServiceImpl

### 5-1. 껍데기 — AdminService 인터페이스 + 구현체 생성

**생성 파일:**
- `src/main/java/org/example/service/AdminService.java`
- `src/main/java/org/example/service/AdminServiceImpl.java`

```java
/**
 * 관리자 전용 사용자 관리 서비스.
 * 계정 잠금 해제, 유저 조회 등 ADMIN 권한이 필요한 작업을 처리한다.
 */
public interface AdminService {
    /** 특정 유저 상세 조회 (관리자용) */
    UserDetailDto getUser(Long userId);

    /** 잠긴 계정 해제 */
    void unlockUser(Long userId);
}
```

구현체 껍데기:
```java
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final UserRepository userRepository;

    @Override
    public UserDetailDto getUser(Long userId) {
        return null; // 껍데기
    }

    @Override
    public void unlockUser(Long userId) {
        // 껍데기
    }
}
```

### 5-2. Red — AdminServiceImpl 단위 테스트

**파일:** `src/test/java/org/example/service/AdminServiceImplTest.java`

```java
// 순수 단위 테스트: @Mock + @InjectMocks
// Mock 대상: UserRepository
//
// 테스트 케이스:
//
// [getUser]
// 1. 존재하는 userId → UserDetailDto 반환 (id, username, nickname, provider, failureCount, lockedAt 등)
// 2. 존재하지 않는 userId → IllegalArgumentException
//
// [unlockUser]
// 3. 잠긴 유저 → user.unlock() 호출 verify, accountNonLocked == true
// 4. 존재하지 않는 userId → IllegalArgumentException
// 5. 이미 잠금 해제된 유저 → 정상 처리 (멱등성)
```

### 5-3. Green — AdminServiceImpl 구현

```java
@Override
@Transactional(readOnly = true)
public UserDetailDto getUser(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    return UserDetailDto.from(user);
}

@Override
@Transactional
public void unlockUser(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    user.unlock();
}
```

### 실행: `./gradlew test --tests "org.example.service.AdminServiceImplTest"`

---

## TDD 사이클 6: UserDetailDto

### 6-1. 껍데기 — DTO 생성

**생성 파일:** `src/main/java/org/example/dto/response/UserDetailDto.java`

```java
/**
 * 관리자용 유저 상세 정보 응답 DTO.
 * 비밀번호 등 민감 정보를 제외하고 관리에 필요한 정보만 포함한다.
 */
public record UserDetailDto(
    Long id,
    String username,
    String nickname,
    String email,
    AuthProvider provider,
    boolean enabled,
    boolean accountNonLocked,
    int failureCount,
    LocalDateTime lockedAt,
    List<String> roles
) {
    /** User 엔티티에서 DTO 변환 */
    public static UserDetailDto from(User user) {
        return null; // 껍데기
    }
}
```

### 6-2. Red — UserDetailDto 단위 테스트

**파일:** `src/test/java/org/example/dto/response/UserDetailDtoTest.java`

```java
// 순수 단위 테스트
//
// 테스트 케이스:
// 1. User 엔티티 → UserDetailDto 변환 시 모든 필드 매핑 확인
// 2. roles 필드에 Role.getName() 문자열 리스트 반환 확인
// 3. password 필드가 DTO에 포함되지 않는지 확인 (필드 자체가 없음)
```

### 6-3. Green — from() 메서드 구현

```java
public static UserDetailDto from(User user) {
    List<String> roleNames = user.getRoles().stream()
        .map(Role::getName)
        .toList();
    return new UserDetailDto(
        user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
        user.getProvider(), user.isEnabled(), user.isAccountNonLocked(),
        user.getFailureCount(), user.getLockedAt(), roleNames
    );
}
```

### 실행: `./gradlew test --tests "org.example.dto.response.UserDetailDtoTest"`

---

## TDD 사이클 7: AdminController

### 7-1. 껍데기 — AdminController 생성

**생성 파일:** `src/main/java/org/example/controller/AdminController.java`

```java
/**
 * 관리자 전용 API 컨트롤러.
 *
 * <p>모든 엔드포인트는 {@code /admin/**} 경로 하위에 위치하며,
 * SecurityConfig에서 {@code hasRole("ADMIN")}으로 보호된다.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** 특정 유저 상세 조회 */
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDetailDto> getUser(@PathVariable Long userId) {
        return null; // 껍데기
    }

    /** 계정 잠금 해제 */
    @PostMapping("/users/{userId}/unlock")
    public ResponseEntity<String> unlockUser(@PathVariable Long userId) {
        return null; // 껍데기
    }
}
```

### 7-2. Red — AdminController WebMvcTest

**파일:** `src/test/java/org/example/controller/AdminControllerTest.java`

```java
// @WebMvcTest(AdminController.class)
// @AutoConfigureMockMvc(addFilters = false)  // Security 필터 비활성화하여 순수 컨트롤러 테스트
// @MockitoBean: AdminService
// MockMvcTester + AssertJ 스타일
//
// 테스트 케이스:
//
// [GET /admin/users/{userId}]
// 1. 존재하는 유저 → 200 + UserDetailDto JSON 반환
// 2. 존재하지 않는 유저 → 400 (IllegalArgumentException)
//
// [POST /admin/users/{userId}/unlock]
// 3. 잠긴 유저 잠금 해제 → 200 + "계정 잠금이 해제되었습니다."
// 4. 존재하지 않는 유저 → 400
//
// [권한 테스트 — Security 필터 활성화]
// 5. ROLE_USER로 /admin/** 접근 → 403
// 6. ROLE_ADMIN으로 /admin/** 접근 → 200
// 7. 인증 없이 /admin/** 접근 → 401
```

> 권한 테스트(5~7)는 별도 테스트 클래스(`AdminControllerSecurityTest.java`)로 분리하여
> `@WebMvcTest` + Security 필터 활성화 + `@WithMockUser(roles = "ADMIN")` 사용.

### 7-3. Green — AdminController 구현

```java
@GetMapping("/users/{userId}")
public ResponseEntity<UserDetailDto> getUser(@PathVariable Long userId) {
    UserDetailDto user = adminService.getUser(userId);
    return ResponseEntity.ok(user);
}

@PostMapping("/users/{userId}/unlock")
public ResponseEntity<String> unlockUser(@PathVariable Long userId) {
    adminService.unlockUser(userId);
    return ResponseEntity.ok("계정 잠금이 해제되었습니다.");
}
```

### 실행: `./gradlew test --tests "org.example.controller.AdminControllerTest"`

---

## TDD 사이클 8: AdminController 권한 테스트

### 8-1. 껍데기 — 없음

### 8-2. Red — 권한 통합 테스트

**파일:** `src/test/java/org/example/controller/AdminControllerSecurityTest.java`

```java
// @WebMvcTest(AdminController.class)
// Security 필터 활성화 (addFilters = false 없음)
// @MockitoBean: AdminService + Spring Security 의존 빈들
// MockMvcTester + AssertJ 스타일
//
// 테스트 케이스:
// 1. @WithMockUser(roles = "ADMIN") + GET /admin/users/1 → 200
// 2. @WithMockUser(roles = "USER") + GET /admin/users/1 → 403
// 3. 인증 없이 GET /admin/users/1 → 401
// 4. @WithMockUser(roles = "ADMIN") + POST /admin/users/1/unlock → 200
// 5. @WithMockUser(roles = "USER") + POST /admin/users/1/unlock → 403
```

### 8-3. Green — 이미 구현 완료

SecurityConfig에 `.requestMatchers("/admin/**").hasRole("ADMIN")` 이미 설정되어 있으므로
테스트만 통과하면 됨.

> 주의: `@WebMvcTest`에서 Security 필터가 정상 동작하려면 `SecurityConfig`,
> `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler` 등이 컨텍스트에 로드되어야 함.
> 필요한 빈을 `@MockitoBean` 또는 `@Import`로 제공.

### 실행: `./gradlew test --tests "org.example.controller.AdminControllerSecurityTest"`

---

## 마무리: 전체 검증

### 9-1. 전체 테스트 실행

```bash
./gradlew test
```

### 9-2. 수동 검증 (Docker 환경)

```bash
docker-compose up -d
./gradlew bootRun
```

1. 잘못된 비밀번호로 5회 로그인 시도 → 5회째에 423 Locked 응답 확인
2. 잠긴 계정으로 올바른 비밀번호 로그인 → 423 확인
3. 잠금 전 발급된 AT로 `GET /user/profile` → 401 확인 (JwtAuthenticationFilter 체크)
4. `ROLE_USER`로 `GET /admin/users/1` → 403 확인
5. `ROLE_ADMIN`으로 `GET /admin/users/1` → 200 + UserDetailDto 확인
6. `POST /admin/users/{userId}/unlock` → 200 + 잠금 해제 확인
7. 해제 후 정상 로그인 → 200 + 토큰 발급 확인
8. 정상 로그인 후 failureCount가 0으로 초기화되었는지 확인

---

## 파일 변경 요약

| 순서 | 작업 | 파일 | 변경 유형 |
|------|------|------|-----------|
| 1 | 잠금 필드/메서드 | `domain/entity/User.java` | 수정 |
| 2 | 로그인 실패 카운트 | `service/AuthServiceImpl.java` | 수정 |
| 3 | 잠금 계정 필터 차단 | `security/jwt/JwtAuthenticationFilter.java` | 수정 |
| 4 | 예외 세분화 | `exception/GlobalExceptionHandler.java` | 수정 (Step 4-A에서 미완료 시) |
| 5 | Admin 서비스 | `service/AdminService.java` | 신규 |
| 5 | Admin 서비스 구현 | `service/AdminServiceImpl.java` | 신규 |
| 6 | 유저 상세 DTO | `dto/response/UserDetailDto.java` | 신규 |
| 7 | Admin 컨트롤러 | `controller/AdminController.java` | 신규 |

### 테스트 파일 (신규)

| 파일 | 테스트 유형 | 대상 |
|------|-------------|------|
| `domain/entity/UserTest.java` | 단위 (순수) | 잠금 비즈니스 메서드 |
| `service/AuthServiceImplTest.java` | 단위 (@Mock) 추가 | 로그인 실패 카운트 |
| `security/jwt/JwtAuthenticationFilterTest.java` | 단위 (@Mock) 추가 | 잠금 계정 필터 |
| `exception/GlobalExceptionHandlerTest.java` | 단위 (순수) | 예외 세분화 |
| `service/AdminServiceImplTest.java` | 단위 (@Mock) | 잠금 해제, 유저 조회 |
| `dto/response/UserDetailDtoTest.java` | 단위 (순수) | DTO 변환 |
| `controller/AdminControllerTest.java` | 슬라이스 (@WebMvcTest) | 엔드포인트 |
| `controller/AdminControllerSecurityTest.java` | 슬라이스 (@WebMvcTest) | 권한 검증 |

---

## Step 4-A와의 관계

| 항목 | Step 4-A | Step 4-B |
|------|----------|----------|
| GlobalExceptionHandler 예외 세분화 | 계획에 포함 | 4-A에서 미완료 시 여기서 구현 |
| JwtAuthenticationFilter Blacklist | 구현 | 잠금 체크 추가 |
| AuthServiceImpl login() | Redis RT 저장으로 변경 | try-catch + 실패 카운트 추가 |
| User 엔티티 | 변경 없음 | failureCount, lockedAt 추가 |
