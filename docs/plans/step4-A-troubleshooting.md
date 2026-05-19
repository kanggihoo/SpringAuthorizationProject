# Step 4-A TDD 트러블슈팅 기록

> 사이클 1 ~ 사이클 5(AuthController) 진행 중 발생한 문제와 해결 방법을 기록한다.

---

## 트러블 1: User 엔티티 기본 생성자 접근 제한

**발생 위치:** `UserServiceImplTest` 작성 중

**문제:**
```java
given(userRepository.findByUsername("existinguser"))
    .willReturn(Optional.of(new User())); // 컴파일 에러
```
`User` 엔티티가 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 선언되어 있어
테스트 코드에서 `new User()`로 직접 생성이 불가능했다.

**해결:**
빌더 패턴으로 대체
```java
User existingUser = User.builder()
    .username("existinguser")
    .password("encoded")
    .nickname("기존유저")
    .build();
```

---

## 트러블 2: Testcontainers Redis — H2 드라이버 누락

**발생 위치:** `TokenRedisRepositoryTest` 작성 중

**문제:**
`@SpringBootTest` + `application-test.yml`에 H2 datasource 설정 후 실행 시:
```
Cannot load driver class: org.h2.Driver
```
`build.gradle`에 H2 의존성이 없어 컨텍스트 로딩 실패.

**원인 분석:**
프로젝트에서 이미 `testcontainers-postgresql`을 사용하고 있으므로
H2 in-memory DB가 아닌 PostgreSQL Testcontainer를 사용하는 것이 맞는 방향이었다.

**해결:**
`application-test.yml` datasource를 Testcontainers JDBC URL 방식으로 변경:
```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///testdb
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
    username: test
    password: test
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

---

## 트러블 3: Mockito `given()` 중첩 호출 — UnfinishedStubbingException

**발생 위치:** `AuthServiceImplTest` — `refresh` 테스트

**문제:**
```java
// Claims 헬퍼 메서드 내부
private Claims createClaims(String username) {
    Claims claims = mock(Claims.class);
    given(claims.getSubject()).willReturn(username); // ← 여기서 given() 시작
    return claims;
}

// 테스트 내부
given(jwtTokenProvider.parseClaims(oldRt))
    .willReturn(createClaims(username)); // ← 여기서 또 given() 호출 → 중첩!
```
`given(...).willReturn(createClaims(...))` 호출 시 `createClaims()` 내부에서
`given(claims.getSubject())` 가 실행되면서 Mockito 내부 상태가 꼬임.

**에러:**
```
org.mockito.exceptions.misusing.UnfinishedStubbingException at AuthServiceImplTest.java:212
```

**해결:**
Claims mock을 테스트 메서드 내에서 직접 생성하고 stubbing 완료 후 `willReturn`에 전달:
```java
// given() 중첩 없이 순차 처리
io.jsonwebtoken.Claims claims = Mockito.mock(io.jsonwebtoken.Claims.class);
Mockito.when(claims.getSubject()).thenReturn(username); // stubbing 완료

given(jwtTokenProvider.parseClaims(oldRt)).willReturn(claims); // 이후 사용
```

---

## 트러블 4: LoginRequestDto — Setter 없음

**발생 위치:** `AuthServiceImplTest` — `login` 테스트

**문제:**
`LoginRequestDto`가 `@Getter` + `@NoArgsConstructor`만 있고 `@Setter`가 없어
테스트에서 필드 설정 불가:
```java
LoginRequestDto request = new LoginRequestDto();
request.setUsername("testuser"); // 컴파일 에러: setUsername() 없음
```

**해결:**
Reflection으로 private 필드에 직접 값 주입:
```java
private LoginRequestDto createLoginRequest(String username, String password) throws Exception {
    LoginRequestDto dto = new LoginRequestDto();
    Field usernameField = LoginRequestDto.class.getDeclaredField("username");
    usernameField.setAccessible(true);
    usernameField.set(dto, username);
    Field passwordField = LoginRequestDto.class.getDeclaredField("password");
    passwordField.setAccessible(true);
    passwordField.set(dto, password);
    return dto;
}
```

---

## 트러블 5: AuthService 인터페이스 시그니처 변경으로 인한 컴파일 에러

**발생 위치:** `AuthService.logout()` 시그니처 변경 시

**문제:**
`void logout(Long userId)` → `void logout(String username, String accessToken)`으로 변경하자
`AuthServiceImpl`, `AuthController`, `AuthApi` 인터페이스 3곳에서 동시에 컴파일 에러 발생.

**해결 순서:**
1. `AuthService.java` 인터페이스 변경
2. `AuthServiceImpl.java` 구현 변경
3. `AuthApi.java` Swagger 인터페이스 logout 시그니처에 `HttpServletRequest` 추가
4. `AuthController.java` logout 메서드에 `HttpServletRequest` 파라미터 + `resolveToken()` 메서드 추가

---

## 트러블 6: `@WebMvcTest` — ObjectMapper 빈 미등록

**발생 위치:** `AuthControllerTest`

**문제:**
Spring Boot 4 환경의 `@WebMvcTest` 슬라이스에서 `ObjectMapper`가 자동 등록되지 않음:
```
NoSuchBeanDefinitionException: No qualifying bean of type 'ObjectMapper'
```

**해결:**
`@Autowired` 대신 테스트 클래스 필드에서 직접 생성:
```java
private final ObjectMapper objectMapper = new ObjectMapper();
```
> Spring Boot 4는 Jackson 3.x로 업그레이드되어 패키지가 `com.fasterxml.jackson` → `tools.jackson`으로 변경됨.
> linter가 import를 자동 수정해줌.

---

## 트러블 7: `@WebMvcTest` — 302 리다이렉트 (Security 필터 간섭)

**발생 위치:** `AuthControllerTest` — 모든 테스트

**문제:**
`@WebMvcTest`가 `SecurityConfig`를 로드하면서 `/logout` 등이
`anyRequest().authenticated()` 규칙에 걸려 302 리다이렉트 발생:
```
Status expected:<200> but was:<302>
```

**해결:**
`@AutoConfigureMockMvc(addFilters = false)`로 Security 필터 체인을 비활성화:
```java
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest { ... }
```

---

## 트러블 8: `addFilters = false` 상태에서 `@AuthenticationPrincipal` null

**발생 위치:** `AuthControllerTest` — logout 테스트

**문제:**
Security 필터를 끄면 `with(user(userDetails))` post processor도 동작하지 않아
`@AuthenticationPrincipal CustomUserDetails userDetails`가 null이 됨.
결과적으로 `authService.logout()`이 호출되지 않아 verify 실패:
```
WantedButNotInvoked: authService.logout(...)
```

**해결:**
`SecurityContextHolder`에 직접 인증 정보를 주입:
```java
UsernamePasswordAuthenticationToken auth =
    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
SecurityContextHolder.getContext().setAuthentication(auth);
```
각 테스트 후 `@AfterEach`에서 `SecurityContextHolder.clearContext()` 호출로 정리.
