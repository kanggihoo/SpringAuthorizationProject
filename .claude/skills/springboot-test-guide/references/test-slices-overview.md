# Test Slices Overview

Quick reference for selecting the right Spring Boot test slice.

## Decision Matrix

| Annotation | Use When | Loads | Speed |
| ---------- | -------- | ----- | ----- |
| **None** (plain JUnit) | Testing pure business logic | Nothing | Fastest |
| `@WebMvcTest` | Controller + HTTP layer | Controllers, MVC, Jackson | Fast |
| `@DataJpaTest` | Repository queries | Repositories, JPA, DataSource | Fast |
| `@RestClientTest` | REST client code | RestTemplate/RestClient, Jackson | Fast |
| `@JsonTest` | JSON serialization | ObjectMapper only | Fastest slice |
| `@WebFluxTest` | Reactive controllers | Controllers, WebFlux | Fast |
| `@DataJdbcTest` | JDBC repositories | Repositories, JDBC | Fast |
| `@DataMongoTest` | MongoDB repositories | Repositories, MongoDB | Fast |
| `@DataRedisTest` | Redis repositories | Repositories, Redis | Fast |
| `@SpringBootTest` | Full integration | Entire application | Slow |

## Selection Guide

### Use NO Annotation (Plain Unit Test)

```java
@ExtendWith(MockitoExtension.class)
class PriceCalculatorTest {

  @Mock
  private DiscountRepository discountRepository;

  @InjectMocks
  private PriceCalculator calculator;

  @Test
  void shouldApplyDiscount() {
    given(discountRepository.findRate()).willReturn(0.1);
    var result = calculator.applyDiscount(100);
    assertThat(result).isEqualTo(new BigDecimal("90.00"));
  }
}
```

**When**: Pure business logic, no Spring context needed. Services with injected dependencies should use `@Mock` + `@InjectMocks`.

### Use @WebMvcTest

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {
  @Autowired private MockMvcTester mvc;
  @MockitoBean private OrderService orderService;
}
```

**When**: Testing request mapping, validation, JSON mapping, security, filters.

**What you get**: MockMvc, ObjectMapper, Spring Security (if present), exception handlers.

### Use @DataJpaTest

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryTest {
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
}
```

**When**: Testing custom JPA queries, entity mappings, transaction behavior, cascade operations.

**What you get**: Repository beans, EntityManager, TestEntityManager, transaction support.

### Use @RestClientTest

```java
@RestClientTest(WeatherService.class)
class WeatherServiceTest {
  @Autowired private WeatherService weatherService;
  @Autowired private MockRestServiceServer server;
}
```

**When**: Testing REST clients that call external APIs.

**What you get**: MockRestServiceServer to stub HTTP responses.

### Use @JsonTest

```java
@JsonTest
class OrderJsonTest {
  @Autowired private JacksonTester<Order> json;
}
```

**When**: Testing custom serializers/deserializers, complex JSON mapping.

### Use @SpringBootTest

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OrderIntegrationTest {
  @Autowired private WebTestClient webClient;
}
```

**When**: Testing full request flow, security filters, database interactions together.

**What you get**: Full application context, embedded server (optional), real beans.

## Common Mistakes

1. **Using @SpringBootTest for service unit tests** - Use `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` instead
2. **Using @SpringBootTest for everything** - Slows down your test suite unnecessarily
3. **@WebMvcTest without mocking services** - Causes context loading failures
4. **@DataJpaTest with @MockitoBean on repositories** - Defeats the purpose (you want real repositories)
5. **Multiple slices in one test** - Each slice is a separate test class

## Java 21 Features in Tests (Java 15+ TextBlocks, Java 21 Records/SequencedCollections)

### Records for Test Data (Java 16+)

```java
record OrderRequest(String product, int quantity) {}
record OrderResponse(Long id, String status, BigDecimal total) {}
```

### Pattern Matching Switch in Tests (Java 21+)

```java
@Test
void shouldHandleDifferentOrderTypes() {
  var order = orderService.create(new OrderRequest("Product", 2));

  String message = switch (order) {
    case PhysicalOrder po -> "Ship to: " + po.getShippingAddress();
    case DigitalOrder dgo -> "Download at: " + dgo.getDownloadLink();
    default -> throw new IllegalStateException("Unknown order type");
  };

  assertThat(message).isNotBlank();
}
```

### Text Blocks for JSON (Java 15+)

```java
@Test
void shouldParseComplexJson() {
  var json = """
    {
      "id": 1,
      "status": "PENDING",
      "items": [
        {"product": "Laptop", "price": 999.99},
        {"product": "Mouse", "price": 29.99}
      ]
    }
    """;

  assertThat(mvc.post().uri("/orders")
    .contentType(APPLICATION_JSON)
    .content(json))
    .hasStatus(CREATED);
}
```

### Sequenced Collections (Java 21+)

```java
@Test
void shouldReturnOrdersInSequence() {
  var orders = orderRepository.findAll();

  assertThat(orders.getFirst().getStatus()).isEqualTo("NEW");
  assertThat(orders.getLast().getStatus()).isEqualTo("COMPLETED");
  assertThat(orders.reversed().getFirst().getStatus()).isEqualTo("COMPLETED");
}
```

## Dependencies by Slice (Gradle)

```groovy
dependencies {
    // WebMvcTest
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'

    // DataJpaTest
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // RestClientTest
    testImplementation 'org.springframework.boot:spring-boot-starter-restclient-test'

    // Testcontainers
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:testcontainers-postgresql'
}
```
