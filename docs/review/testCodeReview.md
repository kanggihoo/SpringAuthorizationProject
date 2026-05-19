# 🔍 Spring Security 7 스킬 기준 테스트 코드 리뷰

## 1. `AuthControllerTest.java` — ⚠️ 개선 필요 (핵심 이슈)

### 🔴 이슈 ① `addFilters = false` + `SecurityContextHolder` 직접 주입 (핵심 안티패턴)

**현재 (line 46, 68-79, 94-97)**
```java
@AutoConfigureMockMvc(addFilters = false)
...
SecurityContextHolder.getContext().setAuthentication(auth);
```

**문제점**
- Spring Security 필터 체인을 꺼버려서 **보안 설정 자체가 검증되지 않습니다**. 스킬(06-test-mockmvc-setup.md)의 "FilterChainProxy를 Filter로 추가" 원칙 위반.
- `SecurityContextHolder`를 수동으로 세팅 → 스레드 누수 위험 (그래서 `@AfterEach clearSecurityContext()`가 필요한 것. 이건 증상 땜질일 뿐).
- 스킬 07-test-mockmvc-authentication.md 의 **공식 방식**(`.with(user(...))` / `.with(authentication(...))` / `@WithMockUser`)을 사용해야 함.

**고쳐야 할 방향**
- `@AutoConfigureMockMvc(addFilters = false)` 제거.
- `logout_*` 테스트에서는 `SecurityContextHolder` 수동 주입 대신:
  ```java
  mockMvc.perform(post("/logout")
      .with(csrf())
      .with(user(createUserDetails("testuser")))   // ← 공식 방식
      .header("Authorization", "Bearer test-access-token"))
  ```
- `@AfterEach clearSecurityContext()`도 불필요해집니다 (필터가 알아서 정리).

### 🟡 이슈 ② POST `/logout` 에 CSRF 토큰 누락 (line 83)
스킬 08번: **POST/PUT/DELETE 는 반드시 `.with(csrf())`**. 현재 logout 테스트만 CSRF 없이 호출 중 — `addFilters=false` 때문에 통과는 되지만, 필터를 정상 적용하는 순간 실패합니다. => 잘못된 리뷰 

현재 프로젝트는 `SecurityConfig` 에서 `csrf().disable()` 로 **전역 비활성화** 했으므로 `.with(csrf())` 는 **필요 없습니다**

### 다만 남는 질문 (일관성)
그렇다면 `login` / `refresh` 테스트에 붙은 `.with(csrf())` 도 **무의미한 호출**이므로 제거하는 게 일관됩니다. 현재는:

| 테스트    | `.with(csrf())` | 실제 효과                    |
| --------- | --------------- | ---------------------------- |
| `logout`  | ❌ 없음         | 무관 (disabled)              |
| `login`   | ✅ 있음         | 무관 (disabled) — **노이즈** |
| `refresh` | ✅ 있음         | 무관 (disabled) — **노이즈** |

### 권고
- **옵션 A (현재 정책 유지)**: JWT 무상태 API 이므로 CSRF disable 유지 → `login`/`refresh` 의 `.with(csrf())` 도 제거해 일관성 확보.

### 🟡 이슈 ③ Jackson 3 사용 중인데 수동 `new ObjectMapper()` (line 14, 53)
```java
import tools.jackson.databind.ObjectMapper;     // Jackson 3
private final ObjectMapper objectMapper = new ObjectMapper();
```
- Spring Security 7은 Jackson 3 기반(스킬 02번: `SecurityJacksonModules`). 수동 생성 시 Security 관련 모듈이 등록되지 않습니다.
- 가능하면 Spring이 구성한 ObjectMapper를 `@Autowired` 로 주입받는 게 안전.

### 🟢 잘 되어 있는 부분
- `@MockitoBean` 사용 (Spring Boot 3.4+ / SB 4 권장 방식, `@MockBean` 아님) ✅
- `login`, `refresh` 에는 `.with(csrf())` 정상 포함 ✅

---

## 2. `AuthServiceImplTest.java` — ✅ 대체로 양호 (순수 단위 테스트)

### 🟡 이슈 ① Reflection으로 DTO 필드 주입 (line 203-212)
```java
Field usernameField = LoginRequestDto.class.getDeclaredField("username");
usernameField.setAccessible(true);
```
- Security 스킬 범위는 아니지만, 테스트 유지보수성 문제. `LoginRequestDto` 에 생성자 / `@Builder` / setter 를 추가해 Reflection 제거 권장.

### 🟡 이슈 ② `io.jsonwebtoken.Claims` Mocking (line 146-147, 174, 191)
- Security 스킬과 직접 관련은 없지만, 외부 JWT 라이브러리 내부 객체 mocking은 취약. `Jwts.claims().subject(username).build()` 같은 **실제 Claims 빌더** 사용이 안전.

### 🟢 잘 되어 있는 부분
- `@ExtendWith(MockitoExtension.class)` + 순수 단위 테스트 분리 ✅
- Spring Context 로드 없음 → 빠르고 격리된 테스트 ✅

---

## 3. `UserServiceImplTest.java` — ✅ 양호

- Security 관련 특이사항 없음. 비즈니스 로직 단위 테스트로 적절.
- `PasswordEncoder` 를 mock 처리한 것도 정석 ✅

---

## 4. `JwtTokenProviderTest.java` — 🟡 경미한 이슈

### 🟡 이슈 ① `Thread.sleep(10)` 의존 (line 53, 81)
- 만료 테스트를 sleep 기반으로 하면 느리고 flaky. `Clock` 주입 패턴 또는 만료 시각을 과거로 설정할 수 있는 생성자 확장을 권장.
- Spring Security 스킬 범위 밖이지만, 토큰 만료 검증은 `Clock` 주입이 정석.

### 🟢 잘 되어 있는 부분
- Spring Context 없이 순수 생성자 주입 ✅
- 정상/만료/변조 케이스 모두 커버 ✅

---

## 5. `TokenRedisRepositoryTest.java` — ✅ 양호

- Testcontainers + `@DynamicPropertySource` 로 실제 Redis 사용 ✅ (mock 기반 안티패턴 회피)
- `@BeforeEach flushAll()` 로 테스트 격리 ✅
- Security 스킬 범위 밖이지만, 인프라 테스트로 적절.

---

# 📋 우선순위별 수정 권고 요약

| 우선순위  | 파일                   | 이슈                                                                               | 스킬 근거      |
| --------- | ---------------------- | ---------------------------------------------------------------------------------- | -------------- |
| 🔴 **P0** | `AuthControllerTest`   | `addFilters=false` 제거 후 `.with(user(...))` / `.with(csrf())` 공식 방식으로 전환 | 06, 07, 08     |
| 🔴 **P0** | `AuthControllerTest`   | `SecurityContextHolder` 수동 주입 제거 (필터 체인에 위임)                          | 07             |
| 🟡 **P1** | `AuthControllerTest`   | `logout` POST 에 `.with(csrf())` 추가                                              | 08             |
| 🟡 **P2** | `AuthControllerTest`   | `ObjectMapper` 수동 생성 → `@Autowired` 주입                                       | 02 (Jackson 3) |
| 🟢 **P3** | `AuthServiceImplTest`  | Reflection DTO 주입 제거 (별개 리팩터링)                                           | 스킬 외        |
| 🟢 **P3** | `JwtTokenProviderTest` | `Thread.sleep` → `Clock` 주입                                                      | 스킬 외        |

## ✨ 핵심 한 줄 요약
> **`AuthControllerTest` 의 `addFilters=false` + `SecurityContextHolder` 직접 세팅 조합이 가장 큰 문제** — Spring Security 7 공식 테스트 방식(`springSecurity()` 필터 + `.with(user(...))` + `.with(csrf())`)으로 전환하면 실제 보안 설정까지 함께 검증되는 의미 있는 슬라이스 테스트가 됩니다. 나머지 단위 테스트들은 Security 스킬 관점에서 크게 문제 없습니다.


# 🔍 Spring Boot 4 Testing 스킬 기준 테스트 코드 리뷰


## 📊 전체 요약

| 파일                       | 테스트 슬라이스                  | 스킬 준수도 | 주요 이슈                                                     |
| -------------------------- | -------------------------------- | ----------- | ------------------------------------------------------------- |
| `AuthControllerTest`       | `@WebMvcTest`                    | 🟡 50%      | MockMvcTester 미사용, 수동 `ObjectMapper`, `addFilters=false` |
| `AuthServiceImplTest`      | 순수 단위                        | 🟢 85%      | Reflection 기반 DTO 주입 (테스트 용이성)                      |
| `UserServiceImplTest`      | 순수 단위                        | 🟢 95%      | `@DisplayName` 패턴만 약간 개선 여지                          |
| `JwtTokenProviderTest`     | 순수 단위                        | 🟢 90%      | `Thread.sleep` 의존                                           |
| `TokenRedisRepositoryTest` | `@SpringBootTest`+Testcontainers | 🟡 70%      | 전체 컨텍스트 로드 과다                                       |

---

## 1. `AuthControllerTest.java` — 🔴 개선 다수 필요

### 🔴 이슈 ① Classic MockMvc 사용 (스킬의 최우선 권고 위반)
**스킬 핵심 권고** (mockmvc-tester.md, webmvctest.md):
> **Spring Boot 3.2+ 부터 `MockMvcTester` 사용이 권장됨.** AssertJ 스타일로 타입 안전성 + 가독성 확보.

**현재 (line 34, 83)**
```java
@Autowired private MockMvc mockMvc;
mockMvc.perform(post("/logout")...).andExpect(status().isOk());
```

**개선 방향 (예시)**
```java
@Autowired private MockMvcTester mvc;

assertThat(mvc.post().uri("/login")
    .contentType(MediaType.APPLICATION_JSON)
    .content(json))
    .hasStatusOk()
    .bodyJson()
    .convertTo(TokenResponseDto.class)
    .satisfies(res -> assertThat(res.getAccessToken()).isEqualTo("access-token"));
```

### 🔴 이슈 ② `jsonPath()` 사용 (스킬 권고: `convertTo()` 선호)
**스킬 원문**
> "Prefer `convertTo()` over `extractingPath()` - Type-safe, refactorable"

**현재 (line 127, 161)**
```java
.andExpect(jsonPath("$.accessToken").value("access-token"));
```
→ 필드명 리팩터링 시 IDE가 추적 못 함. DTO로 변환해 타입 안전하게 단언하는 게 스킬 방향.

### 🟡 이슈 ③ 수동 `new ObjectMapper()` (line 53)
**스킬 권고 (webmvctest.md line 28)**
> "`@WebMvcTest`가 Jackson ObjectMapper를 자동으로 로드함"

**현재**
```java
private final ObjectMapper objectMapper = new ObjectMapper();
```
→ Spring Boot가 구성한 ObjectMapper 대신 기본 인스턴스 사용. `@Autowired ObjectMapper` 또는 `JacksonTester<T>` 사용 권장.

```java
@Autowired private JacksonTester<LoginRequest> jsonLogin;
// content(jsonLogin.write(request).getJson())
```

### 🟡 이슈 ④ 테스트 구조: "happy path → edge → error" 순서 준수 여부
**스킬 권고 (Test Coverage Order)**
> "1. Main scenario → 2. Other paths → 3. Exceptions/Errors"

**현재 순서**: `logout_*` (success) → `login_*` (success) → `login_422` (error) → `refresh_200` → `refresh_400`
→ 각 엔드포인트 내부에선 맞지만, `@Nested` 로 엔드포인트별 그룹핑하면 훨씬 명확해짐.

```java
@Nested class LoginEndpoint { ... }
@Nested class LogoutEndpoint { ... }
@Nested class RefreshEndpoint { ... }
```

### 🟢 스킬 준수 부분
- `@WebMvcTest(AuthController.class)` 로 슬라이스 한정 ✅
- `@MockitoBean` 사용 (deprecated `@MockBean` 회피) ✅
- `@DisplayName` 으로 의도 명시 ✅

---

## 2. `AuthServiceImplTest.java` — 🟢 대체로 우수

### 🟢 스킬 정답에 부합
**스킬 Quick Decision Tree**
> "Testing business logic in service? → Plain JUnit + Mockito (no Spring context)"

→ `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks` 조합은 스킬이 명시한 정답.

### 🟡 이슈 ① Reflection 기반 DTO 주입 (line 203-212) — 코드 복잡도 신호
**스킬 권고 (Code Complexity Assessment)**
> "If you need workarounds to test, recommend refactoring the production code"

**현재**
```java
Field usernameField = LoginRequestDto.class.getDeclaredField("username");
usernameField.setAccessible(true);
```
→ **프로덕션 DTO 리팩터링 권장**: `@Builder`, `public record LoginRequest(...)`, 또는 테스트용 팩토리 메서드. Reflection은 코드 냄새.

### 🟡 이슈 ② 중복되는 given 블록 (Avoid Code Redundancy 위반)
**스킬 권고**
> "Create helper methods for commonly used objects and mock setup"

**현재** — `login_callsSaveRefreshToken`, `login_returnsTokenResponse` 두 테스트에 동일 stub 5개가 완전히 중복:
```java
given(authenticationManager.authenticate(any())).willReturn(auth);
given(jwtTokenProvider.generateAccessToken(...)).willReturn("access-token");
given(jwtTokenProvider.generateRefreshToken(...)).willReturn("refresh-token");
given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);
```
→ `setupLoginMocks()` 헬퍼로 추출 권장.

### 🟡 이슈 ③ AssertJ 활용도 낮음
**스킬 권고 (AssertJ Style)**
> "Fluent, readable assertions over verbose matchers"

**현재 (line 104-105)**
```java
assertThat(result.getAccessToken()).isEqualTo("access-token");
assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
```
→ `satisfies()` 체인으로 묶으면 더 표현력 좋음:
```java
assertThat(result).satisfies(r -> {
    assertThat(r.getAccessToken()).isEqualTo("access-token");
    assertThat(r.getRefreshToken()).isEqualTo("refresh-token");
});
```

---

## 3. `UserServiceImplTest.java` — 🟢 스킬 모범 사례

### 🟢 매우 잘 됨
- 순수 Mockito 단위 테스트 ✅
- 해피 패스 → 엣지(role 강제) → 예외(중복 아이디) 순서 ✅
- `verify(..., never())` 로 음성 검증까지 포함 ✅
- `@DisplayName` 한국어로 의도 명확 ✅

### 🟡 경미한 개선점
- `signup_roleIsAlwaysRoleUser_whenAdminRequested` 테스트명 — "ROLE_ADMIN을 넣어도"라 하지만 실제 코드에선 `role` 필드가 없음(주석에 명시). 테스트 의도와 실행이 불일치. **제목을 "role 필드 제거로 ROLE_USER가 강제된다"로 명확화** 권장.

---

## 4. `JwtTokenProviderTest.java` — 🟢 양호

### 🟡 이슈 ① `Thread.sleep` 기반 만료 테스트 (line 52-56, 80-84)
**스킬 권고 (Testing Best Practices)**
> "Test production scenarios with real scenarios in mind"

**문제점**
- flaky: CI 느려지면 10ms 안에 만료 안 될 수도.
- 테스트 속도 저하.

**개선 방향**: `JwtTokenProvider` 에 `Clock` 주입 → 테스트에선 고정 `Clock.fixed(...)` 사용. 코드 변경 필요하므로 **프로덕션 리팩터링 권고** 항목.

### 🟢 잘 됨
- 생성자 주입만 사용, Spring Context 없음 ✅ (스킬 Decision Tree 부합)
- 정상/만료/변조 3가지 시나리오 커버 ✅

---

## 5. `TokenRedisRepositoryTest.java` — 🟡 개선 여지

### 🟡 이슈 ① `@SpringBootTest` 과다 (스킬 Test Pyramid 위반)
**스킬 원칙**
> "Use the narrowest slice that gives you confidence. Unit > Slice > Integration"

**현재**
```java
@SpringBootTest   // ← 전체 애플리케이션 컨텍스트 로드
@Testcontainers
```
**문제점**
- `TokenRedisRepository` 는 Redis만 필요한데 Security/JPA/Web 전부 로딩됨 → 테스트 느려짐.
- Boot 4 에서는 `@DataRedisTest` 슬라이스(또는 `@Import(TokenRedisRepository.class)` + `@AutoConfigureDataRedis`) 사용이 더 적합.

### 🟡 이슈 ② `BeforeEach` 의 `flushAll()` 체인 길이 (line 47)
```java
stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
```
→ 가독성 위해 헬퍼 추출 권장.

### 🟢 잘 됨
- Testcontainers + `@DynamicPropertySource` ✅ (스킬 testcontainers-jdbc.md 패턴과 동일)
- 각 테스트 전 초기화 ✅

---

## 6. 전반적 누락 항목 (프로젝트 차원)

### 🔴 Jacoco 커버리지 미확인
**스킬 권고**
> "80+% coverage minimum. Use Jacoco maven plugin for coverage reporting"

→ `build.gradle`에 Jacoco 플러그인 설정 여부 확인 필요 (이번 리뷰 범위 밖).

### 🔴 `@DataJpaTest` 통합 테스트 부재
- `UserRepository` / `RoleRepository` 에 대한 실제 DB 쿼리 검증이 없음.
- 스킬 Decision Tree: "Testing repository queries? → @DataJpaTest with Testcontainers" 를 따르는 테스트 추가 권장.

### 🟡 `RestTestClient` 미사용
- 스킬은 Spring Boot 4 에서 `TestRestTemplate` 대신 `RestTestClient` 권장. 현재 E2E 통합 테스트 자체가 없는데, 추가한다면 `RestTestClient` 기반으로.

---

## 📋 우선순위별 수정 권고 요약

| 우선순위  | 파일                       | 이슈                                                   | 스킬 근거                   |
| --------- | -------------------------- | ------------------------------------------------------ | --------------------------- |
| 🔴 **P0** | `AuthControllerTest`       | `MockMvc` → `MockMvcTester` 로 전환                    | mockmvc-tester.md           |
| 🔴 **P0** | `AuthControllerTest`       | `jsonPath()` → `convertTo(Dto.class).satisfies(...)`   | mockmvc-tester.md Key Point |
| 🔴 **P0** | 프로젝트                   | `UserRepository` 용 `@DataJpaTest` 추가                | Decision Tree               |
| 🟡 **P1** | `AuthControllerTest`       | `new ObjectMapper()` → `@Autowired` or `JacksonTester` | webmvctest.md               |
| 🟡 **P1** | `AuthServiceImplTest`      | 중복 stub → 헬퍼 메서드 추출                           | Avoid Code Redundancy       |
| 🟡 **P1** | `AuthServiceImplTest`      | Reflection DTO → 프로덕션 DTO 리팩터링                 | Code Complexity             |
| 🟡 **P1** | `TokenRedisRepositoryTest` | `@SpringBootTest` → Redis 슬라이스로 축소              | Test Pyramid                |
| 🟢 **P2** | `AuthControllerTest`       | 엔드포인트별 `@Nested` 그룹핑                          | Test Organization           |
| 🟢 **P2** | `JwtTokenProviderTest`     | `Thread.sleep` → `Clock` 주입                          | 프로덕션 리팩터링           |
| 🟢 **P3** | 전체                       | Jacoco 커버리지 설정/확인                              | 80% 목표                    |

## ✨ 핵심 한 줄 요약
> **가장 큰 격차는 `AuthControllerTest` 가 여전히 Classic MockMvc + `jsonPath()` 패턴이라는 점** — 스킬이 가장 강력히 권고하는 **MockMvcTester + `convertTo().satisfies()`** 로의 전환이 Spring Boot 4 시대 스타일의 핵심. 서비스/JWT 단위 테스트는 스킬 Decision Tree 의 "순수 JUnit + Mockito" 정답에 잘 부합하지만, **중복 stub 추출**과 **Reflection 제거**는 리팩터링 신호로 받아들여야 합니다.


---


    1. AuthControllerTest — addFilters=false 제거 방향

    /logout은 anyRequest().authenticated()로 보호된 엔드포인트입니다. addFilters=false를 제거하면 실제 JWT 필터 체인이
    동작합니다.

    현재 /logout 테스트는 SecurityContextHolder 직접 주입 대신 .with(user(userDetails))로 전환할 예정인데, 이 방식은
    springSecurity() 필터가 활성화된 상태에서 동작합니다.

    질문: @WebMvcTest 환경에서 JwtAuthenticationFilter, ExceptionHandlerFilter 같은 커스텀 필터들도 함께 테스트에서
    활성화되길 원하시나요? 아니면 Spring Security의 기본 보안 설정만 활성화하고 커스텀 JWT 필터는 제외하고 싶으신가요?

    ▎ - A안 (권장): addFilters=false 제거 + @WebMvcTest가 자동으로 올리는 Security Filter 활성화 →
    ▎ .with(user(userDetails)) 로 인증 주입
    ▎ - B안: addFilters=false 유지하되 SecurityContextHolder 대신 .with(authentication(...)) 사용 (덜 이상적이지만
  현실적)

    ---
    2. MockMvcTester 전환 범위

    리뷰 P0 이슈로 MockMvc → MockMvcTester 전환이 권고되어 있습니다. 그런데 TokenResponseDto의 refreshToken 필드가
    @JsonIgnore라서 응답 JSON에 없습니다. convertTo(TokenResponseDto.class)로 변환해도 accessToken과 tokenType만 검증
    가능합니다.

    질문: MockMvcTester 전환 시 응답 검증을 convertTo(TokenResponseDto.class).satisfies(...) 패턴으로 전면 교체할까요?
    (현재 jsonPath("$.accessToken") → convertTo 방식)

    ---
    3. AuthServiceImplTest — Reflection 제거

    LoginRequestDto는 @NoArgsConstructor + @Getter만 있고 setter/builder가 없어서 현재 Reflection으로 필드를
  주입합니다.
    리뷰에서 DTO 리팩터링을 권고하고 있는데:

    질문: LoginRequestDto에 @AllArgsConstructor 또는 @Builder를 추가해도 괜찮으신가요? (프로덕션 코드 변경이
  필요합니다)

    ---
    4. TokenRedisRepositoryTest — @SpringBootTest 유지 여부

    리뷰에서 @DataRedisTest 슬라이스로 교체를 권고하지만, Spring Boot 4에서 @DataRedisTest는 Redis 관련 빈만 로드하여
    TokenRedisRepository의 의존성(Security/JPA 등)과 충돌할 수 있습니다.

    질문: @SpringBootTest를 @DataRedisTest로 교체 시도해 볼까요? 아니면 현재 @SpringBootTest를 유지하되 다른 이슈들만
    수정할까요?

    ---


### 1. AuthController 테스트 — **A안 채택**
> "A안으로 가주세요. 단, 커스텀 JWT 필터(`JwtAuthenticationFilter`, `ExceptionHandlerFilter`)는 `@WebMvcTest` 에서 자동 로드되지 않으므로 **`@Import(SecurityConfig.class)`** 또는 필요한 필터 빈만 명시적으로 `@Import` 해서 올리세요. JWT 필터까지 함께 돌리면 사실상 통합 테스트가 되니, **슬라이스 테스트에서는 `.with(user(userDetails))` 로 인증 상태만 주입하고 JWT 필터 자체 검증은 별도 통합 테스트(`@SpringBootTest`)로 분리**하는 게 스킬의 Test Pyramid 원칙에 맞습니다."

### 2. MockMvcTester 전환 — **전면 전환 OK, 단 `@JsonIgnore` 필드는 예외**
> "네, `convertTo(TokenResponseDto.class).satisfies(...)` 로 전면 교체하세요. `refreshToken` 은 `@JsonIgnore` 라 응답 JSON에 없으므로 **`accessToken`, `tokenType` 만 검증**하면 됩니다. `refreshToken` 은 어차피 **Set-Cookie 헤더**로 내려가므로 `.hasCookie("Refresh-Token", ...)` 로 별도 검증하는 게 원래 의도에 맞습니다. (리뷰의 convertTo 권고와 충돌 없음)"

### 3. LoginRequestDto — **`@Builder` 추가 OK**
> "네, **`@Builder` 추가를 선호**합니다. `@AllArgsConstructor` 만 추가하면 필드 순서 의존성이 생겨 유지보수가 나쁩니다. `@Builder` 는 프로덕션 코드에서도 DTO 생성 시 가독성이 좋아지고, 테스트의 Reflection 제거 목적에도 완벽히 부합합니다. 단, **API 역직렬화용 `@NoArgsConstructor` 는 유지**해야 Jackson 이 동작합니다 (`@Builder` 와 공존 가능)."

### 4. TokenRedisRepository 테스트 — **현재 유지 + `@Import` 로 좁히기**
> "Spring Boot 4 기준 **`@DataRedisTest` 는 `TokenRedisRepository` 가 `@Repository` 가 아닌 `@Component` 면 빈으로 등록되지 않는 이슈**가 있고, Security/JPA 의존성이 있으면 충돌합니다. **현재 `@SpringBootTest` 유지하되, 가능하면 `@SpringBootTest(classes = { TokenRedisRepository.class, RedisConfig.class })` 로 좁히는 것을 시도**해주세요. 실패하면 그냥 유지하고 다른 P0/P1 이슈에 집중하는 게 실용적입니다. 이건 **P2 우선순위라 이번 라운드에서 스킵해도 OK**."

---

## 📋 작업 권고 순서
1. **Q3 먼저** (`@Builder` 추가) — 다른 테스트 수정의 전제조건
2. **Q1** (`AuthControllerTest` 필터 전략 결정)
3. **Q2** (MockMvcTester 전환)
4. **Q4** (시간 남으면, 안 되면 스킵)