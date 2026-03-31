# 테스트 어노테이션 변경

Spring Boot 4.0에서 가장 빈번히 마주치는 변경 사항.
`@MockBean`, `@SpyBean` 등 기존 테스트 어노테이션의 deprecated 및 대체재.

---

## @MockBean / @SpyBean → @MockitoBean / @MockitoSpyBean

### 변경 이유
Spring Framework 7.0이 Mockito 통합을 직접 지원하게 되면서
Spring Boot의 `@MockBean`/`@SpyBean`이 deprecated되고
Spring Framework 네이티브 어노테이션으로 대체됐다.

### 단순 교체 (가장 흔한 케이스)

```java
// 이전 (deprecated)
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpringBootTest
class UserServiceTest {

    @MockBean
    private UserRepository userRepository;

    @SpyBean
    private EmailService emailService;
}
```

```java
// 이후
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class UserServiceTest {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoSpyBean
    private EmailService emailService;
}
```

### @WebMvcTest / @DataJpaTest 슬라이스 테스트에서도 동일 적용

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean  // @MockBean → @MockitoBean
    private UserService userService;

    @Test
    void getUser() throws Exception {
        given(userService.findById(1L)).willReturn(new User(1L, "홍길동"));

        mockMvc.perform(get("/api/users/1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.name").value("홍길동"));
    }
}
```

---

## MockitoTestExecutionListener 제거

`MockitoTestExecutionListener`가 완전히 제거됐다.

### 영향
`@TestExecutionListeners`에 `MockitoTestExecutionListener`를 명시적으로 추가한 경우.

```java
// 이전 — MockitoTestExecutionListener가 존재했음
@TestExecutionListeners(MockitoTestExecutionListener.class) // 더 이상 존재하지 않음
class MyTest { ... }
```

### 대체재

```java
// 이후 — Mockito 공식 확장 사용
@ExtendWith(MockitoExtension.class)  // JUnit 5 Mockito 확장
class MyTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;
}
```

> **참고**: `@SpringBootTest`와 함께 사용하는 경우라면 `@MockitoBean`이 올바른 대체재다.
> `@ExtendWith(MockitoExtension.class)`는 Spring Context 없이 순수 단위 테스트에 적합하다.

---

## @PropertyMapping 패키지 이동

자동 구성 테스트에서 사용하던 `@PropertyMapping`의 패키지가 이동됐다.

```java
// 이전
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;

// 이후
import org.springframework.boot.test.context.PropertyMapping;
```

---

## @DataJpaTest / TestEntityManager / AutoConfigureTestDatabase 패키지 이동

Spring Boot 4에서 JPA 테스트 관련 클래스가 모듈 분리로 인해 패키지가 전면 재편됐다.

### 변경된 import

```java
// 이전 (Boot 3)
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

// 이후 (Boot 4)
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
```

### 각 클래스가 속한 모듈

| 클래스 | 모듈 JAR |
|---|---|
| `@DataJpaTest` | `spring-boot-data-jpa-test` |
| `TestEntityManager` | `spring-boot-jpa-test` |
| `@AutoConfigureTestDatabase` | `spring-boot-jdbc-test` |

이 모듈들은 `spring-boot-starter-data-jpa-test`에 모두 포함되어 있으므로,
`build.gradle`에 다음 한 줄만 추가하면 된다:

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
```

### 패키지 이동 클래스를 직접 찾는 방법

컴파일 오류로 클래스가 사라진 경우, Gradle 캐시에서 직접 탐색:

```bash
# 1) resolve된 JAR 목록 확인
./gradlew dependencies --configuration testCompileClasspath

# 2) 특정 클래스가 어느 JAR에 있는지 전수 탐색
find ~/.gradle/caches/modules-2/files-2.1/org.springframework.boot \
  -name "*.jar" ! -name "*sources*" ! -name "*javadoc*" | \
  while read jar; do
    result=$(jar tf "$jar" 2>/dev/null | grep "TargetClassName")
    if [ -n "$result" ]; then echo "=== $jar ==="; echo "$result"; fi
  done
```

`TargetClassName` 자리에 찾고 싶은 클래스명(예: `TestEntityManager`, `DataJpaTest`)을 넣으면 된다.

---

## import 변경 전체 요약

| 이전 | 이후 |
|---|---|
| `org.springframework.boot.test.mock.mockito.MockBean` | `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| `org.springframework.boot.test.mock.mockito.SpyBean` | `org.springframework.test.context.bean.override.mockito.MockitoSpyBean` |
| `org.springframework.boot.test.autoconfigure.properties.PropertyMapping` | `org.springframework.boot.test.context.PropertyMapping` |
| `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` |
| `org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager` | `org.springframework.boot.jpa.test.autoconfigure.TestEntityManager` |
| `org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase` |

---

## 체크리스트

- [ ] `@MockBean` → `@MockitoBean` (import 경로 포함)
- [ ] `@SpyBean` → `@MockitoSpyBean` (import 경로 포함)
- [ ] `MockitoTestExecutionListener` 명시 제거 → `@ExtendWith(MockitoExtension.class)` 또는 `@MockitoBean`
- [ ] `@PropertyMapping` import 경로 변경
- [ ] `@DataJpaTest` / `TestEntityManager` / `@AutoConfigureTestDatabase` import 경로 변경
- [ ] `spring-boot-starter-data-jpa-test` 의존성 추가 확인
- [ ] IDE에서 `MockBean` 전체 검색 후 일괄 교체

### IDE 일괄 교체 (IntelliJ)
`Find and Replace in Path` (Shift+Ctrl+R):
- `@MockBean` → `@MockitoBean`
- import 경로도 함께 수정 필요 (Optimize Imports로 자동 처리)
