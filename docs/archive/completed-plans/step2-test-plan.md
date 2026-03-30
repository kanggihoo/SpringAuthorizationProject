# 테스트 코드 구현 계획 — Spring Boot 4 / Java 21 JWT 인증 프로젝트

## Context

현재 프로젝트(step2)는 JWT 기반 무상태 인증/인가 시스템이 완성된 상태이나 테스트 코드가 전혀 없다.
Unit → Slice → Integration 계층별로 테스트를 작성하여 핵심 인증 흐름(로그인·로그아웃·RTR·권한제어)과 각 레이어의 동작을 검증한다.

---

## 1. build.gradle 변경사항

### 제거
```groovy
// 존재하지 않는 아티팩트 — 삭제
testImplementation 'org.springframework.boot:spring-boot-starter-security-oauth2-client-test'
```

### 추가
```groovy
// WebTestClient (@SpringBootTest 통합 테스트용)
testImplementation 'org.springframework.boot:spring-boot-starter-webflux-test'

// Testcontainers — PostgreSQL 실제 DB로 @DataJpaTest 및 통합 테스트
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:testcontainers-postgresql'  // Testcontainers 2.0 아티팩트명
```

> **이유 (webflux-test)**: `WebTestClient`는 WebFlux 서버 전용이 아니라 **HTTP 클라이언트 라이브러리**다.
> 내부적으로 Reactor(`Mono`, `Flux`)를 사용하므로 MVC 앱을 테스트할 때도 `spring-webflux`가 테스트 클래스패스에 필요하다.
> `testImplementation` 스코프이므로 프로덕션 서버는 기존대로 Servlet MVC로 기동되며 영향 없다.
>
> **이유 (Testcontainers)**: H2 대신 실제 PostgreSQL 도커 이미지를 사용하여 운영 환경과 동일한 DB 동작(타입, 제약, 쿼리)을 검증한다. `spring-boot-testcontainers`가 `@ServiceConnection`을 제공하여 datasource 자동 연결된다.

### 플러그인 추가
```groovy
plugins {
    // 기존 유지...
    id 'jacoco'   // 커버리지 리포트
}
```

### jacoco 설정 추가 (tasks 블록 아래)
```groovy
tasks.named('test') {
    useJUnitPlatform()
    finalizedBy jacocoTestReport
}

jacoco { toolVersion = "0.8.12" }

jacocoTestReport {
    dependsOn test
    reports { xml.required = true; html.required = true }
}

jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = 0.80 } } }
}
```

---

## 2. 테스트 디렉토리 구조

```
src/test/
├── java/org/example/
│   ├── security/
│   │   ├── jwt/
│   │   │   ├── JwtTokenProviderTest.java          ← Unit
│   │   │   ├── JwtAuthenticationFilterTest.java   ← Unit
│   │   │   └── ExceptionHandlerFilterTest.java    ← Unit
│   │   ├── CustomUserDetailsServiceTest.java      ← Unit
│   │   └── exception/
│   │       ├── CustomAccessDeniedHandlerTest.java         ← Unit
│   │       └── CustomAuthenticationEntryPointTest.java    ← Unit
│   ├── service/
│   │   ├── AuthServiceImplTest.java               ← Unit
│   │   └── UserServiceImplTest.java               ← Unit
│   ├── repository/
│   │   ├── UserRepositoryTest.java                ← @DataJpaTest
│   │   └── RefreshTokenRepositoryTest.java        ← @DataJpaTest
│   ├── controller/
│   │   ├── AuthControllerTest.java                ← @WebMvcTest
│   │   └── TestControllerTest.java                ← @WebMvcTest
│   ├── integration/
│   │   └── AuthFlowIntegrationTest.java           ← @SpringBootTest
│   └── TestFixtures.java                          ← 공통 픽스처 유틸
└── resources/
    └── application-test.yml                       ← JWT 설정 (DB는 Testcontainers 자동 주입)
```

### application-test.yml
Testcontainers `@ServiceConnection`이 datasource를 자동 주입하므로 DB URL 설정 불필요.
JWT 설정과 JPA DDL만 명시.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
jwt:
  secret: dGVzdC1zZWNyZXQta2V5LWZvci1qdW5pdC10ZXN0aW5nLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM=
  access-token-expiration: 3600000
  refresh-token-expiration: 604800000
```

---

## 3. 테스트 작성 공통 컨벤션

### @DisplayName 필수 적용

모든 테스트 메서드에 한국어 `@DisplayName`을 추가한다.
메서드명은 개발자용 식별자로 유지하고, `@DisplayName`은 IDE·HTML 리포트에서 문서처럼 읽히도록 비즈니스 언어로 작성한다.

```java
@Test
@DisplayName("로그인 성공 시 AccessToken(Body)과 RefreshToken(쿠키)을 반환한다")
void login_returnsTokens_andCreatesNewRefreshToken() { ... }

@Test
@DisplayName("이미 존재하는 RT가 있으면 새 RT로 갱신(updateToken)하고 save는 호출하지 않는다")
void login_updatesExistingRefreshToken_onReLogin() { ... }

@Test
@DisplayName("DB에 없는 RT로 재발급 시도 시 탈취로 간주하고 예외를 던진다")
void refresh_throwsException_whenTokenNotInDb() { ... }
```

**작성 원칙**:
- 주어(누가/무엇이) + 조건(언제/어떤 상황에서) + 결과(어떻게 된다) 구조 권장
- 클래스 레벨에도 `@DisplayName("AuthServiceImpl — 인증 서비스")` 형태로 그룹화

---

## 4. 공통 픽스처: TestFixtures.java

`ReflectionTestUtils.setField`로 id 주입, User/RefreshToken/CustomUserDetails 생성 팩터리 메서드 제공.

```java
// 예시 메서드
static User buildUser(Long id, String username, String nickname, String roleName)
static RefreshToken buildRefreshToken(Long userId, String token)
static CustomUserDetails buildUserDetails(String username, String roleName)
```

---

## 5. Unit Tests — @ExtendWith(MockitoExtension.class), Spring 컨텍스트 없음

### 5.1 JwtTokenProviderTest (9개)

직접 생성: `new JwtTokenProvider(SECRET, 3_600_000L, 604_800_000L)`

| 테스트 메서드 | 시나리오 |
|---|---|
| `generateAccessToken_containsUsernameAndRoles` | claims.subject = username, claims["roles"] 포함 |
| `generateRefreshToken_containsOnlySubject` | claims["roles"] null |
| `validateToken_returnsTrueForValidToken` | 정상 토큰 |
| `validateToken_throwsExpiredJwtException` | -1ms 만료 토큰 |
| `validateToken_throwsMalformedJwtException` | `"abc.def"` 형식 불량 토큰 |
| `validateToken_throwsJwtException_forBlankToken` | 빈 문자열 |
| `validateToken_throwsJwtException_forWrongSignature` | 다른 키로 서명된 토큰 |
| `validateToken_throwsUnsupportedJwtException_forUnsignedToken` | none 알고리즘 토큰 |
| `getRefreshTokenExpiration_returnsConfiguredValue` | 604_800_000L 반환 |

### 5.2 CustomUserDetailsServiceTest (3개)

| 테스트 메서드 | 시나리오 |
|---|---|
| `loadUserByUsername_returnsCustomUserDetails_whenFound` | username 조회 성공 |
| `loadUserByUsername_throwsUsernameNotFoundException_whenNotFound` | Optional.empty() |
| `loadUserByUsername_reflectsAccountLockFlag` | accountNonLocked=false 반영 |

### 5.3 AuthServiceImplTest (8개)

| 테스트 메서드 | 시나리오 |
|---|---|
| `login_returnsTokens_andCreatesNewRefreshToken` | RT 없음 → save() 호출 |
| `login_updatesExistingRefreshToken_onReLogin` | RT 있음 → updateToken() 호출, save() 미호출 |
| `login_propagatesException_onBadCredentials` | BadCredentialsException 전파 |
| `logout_deletesRefreshToken_byUserId` | deleteByUserId 호출 검증 |
| `refresh_returnsNewTokens_andUpdatesDb` | 정상 RTR 순환 |
| `refresh_throwsException_whenTokenNotInDb` | DB 불일치 → 탈취 간주 |
| `refresh_propagatesExpiredException` | 만료 RT → DB 미조회 |
| `refresh_throwsUsernameNotFoundException_whenUserDeleted` | userId 조회 실패 |

### 5.4 UserServiceImplTest (4개)

| 테스트 메서드 | 시나리오 |
|---|---|
| `signup_savesUser_withEncodedPassword_andExistingRole` | 정상 가입 |
| `signup_createsNewRole_whenRoleNotFound` | Role 자동 생성 |
| `signup_throwsRuntimeException_onDuplicateUsername` | 중복 username |
| `signup_neverCallsSave_whenDuplicateDetected` | 중복 시 save() 미호출 |

### 5.5 JwtAuthenticationFilterTest (8개)

`MockHttpServletRequest / MockHttpServletResponse / MockFilterChain` 활용

| 테스트 메서드 | 시나리오 |
|---|---|
| `doFilterInternal_setsAuthentication_forValidBearerToken` | SecurityContextHolder 세팅 확인 |
| `doFilterInternal_doesNotSetAuthentication_whenNoHeader` | 헤더 없음 → Context 비어있음 |
| `doFilterInternal_propagatesException_whenTokenInvalid` | MalformedJwtException 전파 |
| `shouldNotFilter_returnsTrue_forLoginPath` | /login |
| `shouldNotFilter_returnsTrue_forSignupPath` | /signup |
| `shouldNotFilter_returnsTrue_forRefreshPath` | /refresh |
| `shouldNotFilter_returnsTrue_forSwaggerPath` | /swagger-ui/index.html |
| `shouldNotFilter_returnsFalse_forApiPath` | /user/profile |

### 5.6 ExceptionHandlerFilterTest (3개)

| 테스트 메서드 | 시나리오 |
|---|---|
| `doFilterInternal_callsNextFilter_whenNoException` | resolver 미호출 |
| `doFilterInternal_delegatesToResolver_whenFilterChainThrows` | RuntimeException → resolver 호출 |
| `doFilterInternal_delegatesToResolver_forJwtException` | ExpiredJwtException → resolver 호출 |

---

## 6. Slice Tests

### 6.1 UserRepositoryTest — @DataJpaTest + Testcontainers PostgreSQL (5개)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)  // H2 교체 비활성화
@Testcontainers
@ActiveProfiles("test")
class UserRepositoryTest {
    @Container
    @ServiceConnection  // datasource 자동 연결 (spring-boot-testcontainers 제공)
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestEntityManager entityManager;
    @Autowired UserRepository userRepository;
}
```

> `@AutoConfigureTestDatabase(replace = NONE)` 필수 — 기본값은 임베디드 DB로 교체하므로 Testcontainers 컨테이너가 무시됨.

| 테스트 메서드 | 시나리오 |
|---|---|
| `findByUsername_returnsUser_whenExists` | 정상 조회 |
| `findByUsername_returnsEmpty_whenNotExists` | Optional.empty() |
| `findByUsername_returnsEmpty_afterDeleted` | 삭제 후 조회 |
| `save_generatesIdAndPersistsRoles` | 저장 후 EAGER 로드 검증 |
| `save_enforcesUniqueUsername` | 중복 저장 → DataIntegrityViolationException |

### 6.2 RefreshTokenRepositoryTest — @DataJpaTest + Testcontainers PostgreSQL (7개)

`UserRepositoryTest`와 동일한 `@Container @ServiceConnection static PostgreSQLContainer<?>` 패턴 적용.
두 클래스가 동일한 컨테이너 이미지 + 설정을 공유하므로 Spring 컨텍스트 캐시가 재사용됨 (컨테이너 재기동 없음).

| 테스트 메서드 | 시나리오 |
|---|---|
| `findByUserId_returnsToken_whenExists` | 정상 조회 |
| `findByUserId_returnsEmpty_whenNotFound` | Optional.empty() |
| `findByRefreshToken_returnsToken_whenExists` | 토큰 문자열로 조회 |
| `findByRefreshToken_returnsEmpty_forUnknownToken` | 미존재 토큰 |
| `deleteByUserId_removesToken` | 삭제 후 조회 → empty |
| `save_enforcesUniqueUserId` | userId 중복 → DataIntegrityViolationException |
| `updateToken_changesValueAndExpiry` | RTR 갱신 검증 |

### 6.3 AuthControllerTest — @WebMvcTest(AuthController.class) + @Import(SecurityConfig.class) (13개)

**필수 @MockitoBean** (SecurityConfig 생성자 의존성):
`AuthService, UserService, JwtTokenProvider, CustomUserDetailsService,`
`JwtAuthenticationFilter, ExceptionHandlerFilter, CustomAuthenticationEntryPoint, CustomAccessDeniedHandler`

**MockMvcTester** AssertJ 스타일 사용

| 그룹 | 테스트 메서드 | 시나리오 |
|---|---|---|
| POST /signup | `signup_returns200_forValidRequest` | 정상 가입 |
| | `signup_returns422_forBlankUsername` | @NotBlank 위반 |
| | `signup_returns422_forTooShortUsername` | @Size(min=4) 위반 |
| | `signup_returns422_forShortPassword` | @Size(min=8) 위반 |
| | `signup_returns400_whenServiceThrowsDuplicate` | RuntimeException → 400 |
| POST /login | `login_returns200_withAccessToken_andRefreshTokenCookie` | accessToken in body + Set-Cookie 검증 |
| | `login_returns422_forBlankCredentials` | 유효성 실패 |
| | `login_returns400_whenAuthenticationFails` | RuntimeException → 400 |
| POST /logout | `logout_returns200_andClearsCookie_whenAuthenticated` | `@WithUserDetails("alice")` + cookie Max-Age=0 검증 |
| | `logout_returns401_whenNotAuthenticated` | 미인증 → 401 |
| POST /refresh | `refresh_returns200_withNewTokens` | `MockCookie("Refresh-Token", "old-rt")` 사용 |
| | `refresh_returns400_whenNoCookiePresent` | 쿠키 없음 → 400 |
| | `refresh_returns401_whenTokenExpired` | ExpiredJwtException → 401 |

> **주의**: `@WithMockUser`는 Spring Security `User` 객체를 주입 → `AuthController.logout()`이 `CustomUserDetails`로 캐스팅 실패.
> `/logout` 테스트는 `@WithUserDetails("alice")`와 `customUserDetailsService` 스텁 조합 사용.
>
> **주의**: `TokenResponseDto.refreshToken`은 `@JsonIgnore` → 응답 Body가 아닌 `Set-Cookie` 헤더로만 검증.

### 6.4 TestControllerTest — @WebMvcTest(TestController.class) + @Import(SecurityConfig.class) (8개)

동일한 `@MockitoBean` 세트 (SecurityConfig 의존성)

| 테스트 메서드 | 시나리오 |
|---|---|
| `index_returns200_forAnonymousUser` | 공개 엔드포인트 |
| `index_returns200_withNickname_forAuthenticatedUser` | `@WithMockUser` |
| `userProfile_returns401_forAnonymousUser` | 미인증 → 401 |
| `userProfile_returns200_forUserRole` | `@WithMockUser(roles="USER")` |
| `userProfile_returns200_forAdminRole` | `@WithMockUser(roles="ADMIN")` (hasAnyRole 포함) |
| `adminManage_returns401_forAnonymousUser` | 미인증 → 401 |
| `adminManage_returns403_forUserRole` | `@WithMockUser(roles="USER")` → 403 |
| `adminManage_returns200_forAdminRole` | `@WithMockUser(roles="ADMIN")` → 200 |

---

## 7. Integration Tests — @SpringBootTest(RANDOM_PORT) + Testcontainers PostgreSQL

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired WebTestClient webClient;
}
```

**@Transactional 미사용** — 실제 DB 커밋으로 크로스-요청 상태 검증

| @Order | 테스트 메서드 | 시나리오 |
|---|---|---|
| 1 | `signup_returns200_forValidRequest` | 정상 가입 |
| 2 | `signup_returns400_forDuplicateUsername` | 중복 username |
| 3 | `signup_returns422_forInvalidInput` | 유효성 실패 |
| 4 | `login_returns200_withAccessToken_andRefreshTokenCookie` | 토큰 발급 + HttpOnly 쿠키 확인 |
| 5 | `login_returns400_forWrongPassword` | 잘못된 비밀번호 |
| 6 | `accessProtectedEndpoint_returns200_withValidJwt` | `Authorization: Bearer <at>` → /user/profile 200 |
| 7 | `accessProtectedEndpoint_returns401_withoutJwt` | 토큰 없음 → 401 JSON |
| 8 | `accessAdminEndpoint_returns403_forUserRole` | USER 토큰 → /admin/manage 403 JSON |
| 9 | `refresh_rotatesTokens_andReturns200` | RT 쿠키 전달 → 새 AT+RT 발급, 기존과 다른 값 확인 |
| 10 | `refresh_returns400_onRtrAttack_whenOldTokenReused` | 이전 RT 재사용 → 400 |
| 11 | `refresh_returns400_whenNoCookieProvided` | 쿠키 없음 → 400 |
| 12 | `logout_returns200_andClearsCookie` | 로그아웃 → Max-Age=0 쿠키 확인 |
| 13 | `accessProtectedEndpoint_returns200_afterLogout_withSameAt` | AT는 여전히 유효 (stateless 설계 검증) |

> **시나리오 13 중요**: 로그아웃 후에도 기존 AT는 만료 전까지 유효 → 200 반환.
> 이는 의도된 stateless 설계이며 테스트로 명시해야 함. RT로 재발급 불가 → 400.

---

## 8. 검증 방법

```bash
# 전체 테스트 실행
./gradlew test

# 커버리지 리포트 생성 (build/reports/jacoco/test/html/index.html)
./gradlew test jacocoTestReport

# 커버리지 80% 미만 시 빌드 실패
./gradlew jacocoTestCoverageVerification

# 통합 테스트만 먼저 확인 (컨텍스트 로드 검증)
./gradlew test --tests "org.example.integration.*"
```

---

## 9. 주요 함정 및 대응책

| 함정 | 대응책 |
|---|---|
| `@WebMvcTest` + `SecurityConfig` 임포트 시 4개 필터/핸들러 빈 미등록 → `NoSuchBeanDefinitionException` | `JwtAuthenticationFilter`, `ExceptionHandlerFilter`, `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler` 모두 `@MockitoBean` 등록 |
| `@WithMockUser`는 `CustomUserDetails`가 아닌 `User` 주입 → `AuthController.logout()` 캐스팅 오류 | `/logout` 테스트는 `@WithUserDetails("alice")` + `customUserDetailsService` 스텁 조합 |
| `TokenResponseDto.refreshToken`이 `@JsonIgnore` → 응답 body에 없음 | Set-Cookie 헤더로만 refresh token 값 검증 |
| `User.id`는 setter 없음 → 단위 테스트에서 ID 주입 불가 | `ReflectionTestUtils.setField(user, "id", 1L)` 사용 |
| `@MockBean` 사용 시 컴파일 경고 또는 deprecated | Spring Boot 4에서는 반드시 `@MockitoBean` / `@MockitoSpyBean` 사용 |
| `@DataJpaTest` 기본값이 H2로 교체 → Testcontainers 무시됨 | `@AutoConfigureTestDatabase(replace = NONE)` 필수 |
| Jackson 3 패키지 변경 — `com.fasterxml.jackson` → `tools.jackson.databind` | 테스트 config에서 구 패키지 사용 금지 |

---

## 10. 파일별 예상 테스트 수 요약

| 클래스 | 계층 | 테스트 수 |
|---|---|---|
| JwtTokenProviderTest | Unit | 9 |
| CustomUserDetailsServiceTest | Unit | 3 |
| AuthServiceImplTest | Unit | 8 |
| UserServiceImplTest | Unit | 4 |
| JwtAuthenticationFilterTest | Unit | 8 |
| ExceptionHandlerFilterTest | Unit | 3 |
| UserRepositoryTest | @DataJpaTest | 5 |
| RefreshTokenRepositoryTest | @DataJpaTest | 7 |
| AuthControllerTest | @WebMvcTest | 13 |
| TestControllerTest | @WebMvcTest | 8 |
| AuthFlowIntegrationTest | @SpringBootTest | 13 |
| **합계** | | **~81개** |
