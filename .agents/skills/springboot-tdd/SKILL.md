---
name: springboot-tdd-v2
description: Advanced Test-driven development for Spring Boot using JUnit 5, BDDMockito, MockMvc, Testcontainers, and JaCoCo. Emphasizes Inside-Out strategy and BDD style.
origin: ECC
---

# Spring Boot TDD Workflow (V2)

Advanced TDD guidance for Spring Boot services focusing on the **Inside-Out (Bottom-Up)** approach. Aim for 80%+ coverage (unit + integration).

## 1. TDD Strategy: Inside-Out

Build from the core business logic outwards to the web layer.

1. **Domain/Service Layer (Unit Tests)**: Fast, pure Java tests using Mockito.
2. **Repository Layer (Slice Tests)**: DB interaction using `@DataJpaTest`.
3. **Web Layer (Slice Tests)**: HTTP API validation using `@WebMvcTest`.
4. **Integration (E2E Tests)**: Full application context using `@SpringBootTest`.

## 2. Core Principles

- **Red-Green-Refactor**: Write a failing test -> Make it pass -> Refactor.
- **BDD Style (Given-When-Then)**: Use `BDDMockito` over standard `Mockito` for better readability.
- **Hierarchical Tests**: Use `@Nested` and `@DisplayName`을 사용하여 테스트 케이스를 문맥별로 조직화합니다.

## 3. Layer 1: Domain & Service Tests (Unit)

Use `@ExtendWith(MockitoExtension.class)` for fast execution without Spring Context.

```java
@ExtendWith(MockitoExtension.class)
class MarketServiceTest {
  @Mock private MarketRepository repo;
  @InjectMocks private MarketService service;

  @Nested
  @DisplayName("create() 메서드는")
  class Describe_create {

    @Test
    @DisplayName("유효한 요청이 주어지면 마켓을 생성하고 반환한다")
    void it_creates_and_returns_market() {
      // Given
      CreateMarketRequest req = new CreateMarketRequest("name", "desc");
      given(repo.save(any(Market.class))).willAnswer(inv -> inv.getArgument(0));

      // When
      Market result = service.create(req);

      // Then
      assertThat(result.name()).isEqualTo("name");
      then(repo).should(times(1)).save(any(Market.class));
    }
  }
}
```

## 4. Layer 2: Repository Tests (DataJpaTest)

Verify custom queries, JPA mappings, and DB constraints.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestContainersConfig.class)
class MarketRepositoryTest {
  @Autowired private MarketRepository repo;

  @Test
  @DisplayName("이름으로 마켓을 정상적으로 조회할 수 있다")
  void savesAndFinds() {
    // Given
    MarketEntity entity = new MarketEntity("Test");
    repo.save(entity);

    // When
    Optional<MarketEntity> found = repo.findByName("Test");

    // Then
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Test");
  }
}
```

## 5. Layer 3: Web Layer Tests (WebMvcTest)

Verify HTTP status, request validation (`@Valid`), and JSON serialization.

```java
@WebMvcTest(MarketController.class)
class MarketControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private MarketService marketService;

  @Test
  @DisplayName("GET /api/markets 요청 시 200 OK와 리스트를 반환한다")
  void returnsMarkets() throws Exception {
    // Given
    given(marketService.list(any())).willReturn(Page.empty());

    // When & Then
    mockMvc.perform(get("/api/markets")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andDo(print());
  }
}
```

## 6. Layer 4: Integration Tests (SpringBootTest)

Test the entire flow from Controller to Database (E2E).

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private MarketRepository repository;

  @BeforeEach
  void setUp() {
    // 각 테스트 격리를 위해 데이터 초기화
    repository.deleteAll();
  }

  @Test
  @DisplayName("새로운 마켓을 생성하는 전체 흐름을 검증한다")
  void createsMarket() throws Exception {
    mockMvc.perform(post("/api/markets")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name":"Test","description":"Desc","endDate":"2030-01-01T00:00:00Z","categories":["general"]}
        """))
      .andExpect(status().isCreated())
      .andDo(print());

    // DB에 정상적으로 데이터가 적재되었는지 추가 확인
    assertThat(repository.findAll()).hasSize(1);
  }
}
```

## 7. Infrastructure & Tooling

### Testcontainers

Avoid H2 for integration tests if production uses PostgreSQL/MySQL. Use Testcontainers for exact environmental parity.

### Assertions & Best Practices

- Use **AssertJ** (`assertThat`, `extracting`, `containsExactly`) for fluent assertions.
- **One Assert Per Test (Conceptually)**: 테스트 하나당 하나의 행위(결과)만 검증하세요.
- For async testing, use **Awaitility** (`await().untilAsserted(...)`).

### Test Data Fixtures

Use the Factory/Builder pattern or libraries like `FixtureMonkey` / `Instancio` to generate robust test data and avoid boilerplate.

```java
class MarketFixture {
  public static Market createDefault() {
    return new Market(1L, "Test", MarketStatus.ACTIVE);
  }
}
```

### Coverage Rule (JaCoCo)

Enforce coverage minimums in CI pipeline:

```bash
# Maven
mvn verify jacoco:report jacoco:check
# Gradle
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```
