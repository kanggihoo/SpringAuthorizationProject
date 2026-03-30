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

## import 변경 전체 요약

| 이전 | 이후 |
|---|---|
| `org.springframework.boot.test.mock.mockito.MockBean` | `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| `org.springframework.boot.test.mock.mockito.SpyBean` | `org.springframework.test.context.bean.override.mockito.MockitoSpyBean` |
| `org.springframework.boot.test.autoconfigure.properties.PropertyMapping` | `org.springframework.boot.test.context.PropertyMapping` |

---

## 체크리스트

- [ ] `@MockBean` → `@MockitoBean` (import 경로 포함)
- [ ] `@SpyBean` → `@MockitoSpyBean` (import 경로 포함)
- [ ] `MockitoTestExecutionListener` 명시 제거 → `@ExtendWith(MockitoExtension.class)` 또는 `@MockitoBean`
- [ ] `@PropertyMapping` import 경로 변경
- [ ] IDE에서 `MockBean` 전체 검색 후 일괄 교체

### IDE 일괄 교체 (IntelliJ)
`Find and Replace in Path` (Shift+Ctrl+R):
- `@MockBean` → `@MockitoBean`
- import 경로도 함께 수정 필요 (Optimize Imports로 자동 처리)
