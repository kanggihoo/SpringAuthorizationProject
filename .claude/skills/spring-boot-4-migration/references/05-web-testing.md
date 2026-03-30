# 웹 프레임워크 테스트 변경사항

MockMvc, TestRestTemplate 등 웹 계층 테스트 도구의 사용 방식이 변경됐다.

---

## MockMvc — @AutoConfigureMockMvc 필수

### 변경 이유
MockMvc 설정이 더 이상 `@SpringBootTest`에 묵시적으로 포함되지 않는다.
명시적 opt-in 방식으로 전환됐다.

### Before / After

```java
// 이전: @SpringBootTest만으로 MockMvc 자동 주입 가능
@SpringBootTest
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc; // 동작했음

    @Test
    void getUser() throws Exception {
        mockMvc.perform(get("/api/users/1"))
               .andExpect(status().isOk());
    }
}
```

```java
// 이후: @AutoConfigureMockMvc 반드시 추가
@SpringBootTest
@AutoConfigureMockMvc  // ← 반드시 추가
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getUser() throws Exception {
        mockMvc.perform(get("/api/users/1"))
               .andExpect(status().isOk());
    }
}
```

### @WebMvcTest는 영향 없음

`@WebMvcTest`를 사용하는 슬라이스 테스트는 변경 없이 그대로 동작한다.

```java
// 변경 없음 — @WebMvcTest는 MockMvc를 자동으로 설정함
@WebMvcTest(UserController.class)
class UserControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean  // @MockBean이 아닌 @MockitoBean 사용 (06-testing-annotations.md 참고)
    private UserService userService;
}
```

---

## TestRestTemplate — 어노테이션 + 패키지 이동

### 패키지 이동

```java
// 이전
import org.springframework.boot.test.web.client.TestRestTemplate;

// 이후
import org.springframework.boot.resttestclient.TestRestTemplate;
```

### @AutoConfigureTestRestTemplate 필수

```java
// 이전
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate; // 자동 주입됐음
}
```

```java
// 이후
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate  // ← 반드시 추가
class MyIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void callApi() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

---

## 정적 리소스 — Font 경로 추가

폰트 파일 서빙을 위한 경로가 공통 정적 경로에 추가됐다.

- `/fonts/**` 경로가 기본 static resource 매핑에 포함
- Security 설정에서도 해당 경로가 허용 목록에 자동 추가
- `.woff`, `.woff2`, `.ttf`, `.otf`, `.eot` 등 웹 폰트 파일 형식이 자동으로 `Cache-Control` 대상에 포함

별도 설정 없이 `src/main/resources/static/fonts/` 경로의 폰트 파일이 서빙된다.

### Font 경로를 제외하고 싶을 때

`PathRequest.toStaticResources()` 사용 시 특정 경로를 명시적으로 제외할 수 있다.

```java
// Spring Security 설정에서 FONTS 경로 제외
pathRequest.toStaticResources().atCommonLocations().excluding(StaticResourceLocation.FONTS);
```

---

## HttpMessageConverters — Deprecated

`HttpMessageConverters`가 deprecated됐다. 메시지 컨버터 커스터마이징 방식이 변경됐다.

### 서버 측 컨버터 커스터마이징

```java
// 이전
@Bean
public HttpMessageConverters customConverters() {
    return new HttpMessageConverters(new MappingJackson2HttpMessageConverter());
}
```

```java
// 이후 — ServerHttpMessageConvertersCustomizer 사용
@Bean
public ServerHttpMessageConvertersCustomizer serverConverterCustomizer() {
    return converters -> {
        converters.add(new MappingJackson2HttpMessageConverter());
    };
}
```

### 클라이언트 측 컨버터 커스터마이징 (RestTemplate, WebClient)

```java
// 이후 — ClientHttpMessageConvertersCustomizer 사용
@Bean
public ClientHttpMessageConvertersCustomizer clientConverterCustomizer() {
    return converters -> {
        converters.add(new MappingJackson2HttpMessageConverter());
    };
}
```

---

## Spring Session — 프로퍼티 이름 변경

Spring Session의 프로퍼티 접두사가 변경됐다.

### Redis 세션

```properties
# 이전
spring.session.redis.flush-mode=on-save
spring.session.redis.save-mode=on-set-value

# 이후
spring.session.data.redis.flush-mode=on-save
spring.session.data.redis.save-mode=on-set-value
```

### MongoDB 세션

```properties
# 이전
spring.session.mongodb.collection-name=sessions

# 이후
spring.session.data.mongodb.collection-name=sessions
```

> `spring-boot-properties-migrator`를 사용하면 자동으로 런타임에서 임시 마이그레이션된다.

---

## Jersey — Jackson 3 비호환 주의

Spring Boot 4.0은 Jersey 4.0을 지원하나, **Jersey 4.0은 아직 Jackson 3를 지원하지 않는다**.

### 해결 방법

Jersey에서 JSON 처리가 필요하다면 `spring-boot-jackson2` 의존성을 추가해야 한다.

```xml
<!-- Maven: spring-boot-jackson2 추가 (Jackson 2 호환) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-jackson2</artifactId>
</dependency>
```

```kotlin
// Gradle
implementation("org.springframework.boot:spring-boot-jackson2")
```

> `spring-boot-jackson2`는 deprecated 상태로 제공되며 추후 릴리즈에서 제거될 예정이다.
> Jersey의 Jackson 3 지원이 완료되면 전환하라.

---

## Tomcat — WAR 배포 시 의존성 변경 필수

WAR 파일을 Tomcat 서버에 배포할 경우 의존성을 변경해야 한다.

### Before / After

```xml
<!-- 이전 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-tomcat</artifactId>
  <scope>provided</scope>
</dependency>
```

```xml
<!-- 이후: spring-boot-starter-tomcat-runtime 으로 변경 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-tomcat-runtime</artifactId>
  <scope>provided</scope>
</dependency>
```

```kotlin
// Gradle
providedRuntime("org.springframework.boot:spring-boot-starter-tomcat-runtime")
```

> Embedded Tomcat으로 실행(jar 배포)하는 경우에는 변경하지 않아도 된다.
> 이 변경은 **외부 Tomcat에 WAR로 배포**하는 경우에만 해당한다.

---

## 체크리스트

- [ ] `@SpringBootTest` + `MockMvc` 사용 테스트에 `@AutoConfigureMockMvc` 추가
- [ ] `TestRestTemplate` import 경로 변경 (`org.springframework.boot.resttestclient`)
- [ ] `@SpringBootTest` + `TestRestTemplate` 사용 테스트에 `@AutoConfigureTestRestTemplate` 추가
- [ ] `HttpMessageConverters` 빈 사용 코드를 `ServerHttpMessageConvertersCustomizer` / `ClientHttpMessageConvertersCustomizer`로 전환
- [ ] `spring.session.redis.*` → `spring.session.data.redis.*` 프로퍼티 변경
- [ ] `spring.session.mongodb.*` → `spring.session.data.mongodb.*` 프로퍼티 변경
- [ ] Jersey 사용 시 `spring-boot-jackson2` 의존성 추가 여부 검토
- [ ] Tomcat WAR 배포 시 `spring-boot-starter-tomcat` → `spring-boot-starter-tomcat-runtime` 변경
