# Spring Boot 4.0 Migration

Key testing changes when migrating from Spring Boot 3.x to 4.0.

## Dependency Changes

### Modular Test Starters

Spring Boot 4.0 introduces modular test starters:

**Before (3.x):**

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-test'
```

**After (4.0) - WebMvc Testing:**

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
```

**After (4.0) - REST Client Testing:**

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-restclient-test'
```

## Annotation Migration

### @MockBean → @MockitoBean

**Deprecated (3.x):**

```java
@MockBean
private OrderService orderService;
```

**New (4.0):**

```java
@MockitoBean
private OrderService orderService;
```

### @SpyBean → @MockitoSpyBean

**Deprecated (3.x):**

```java
@SpyBean
private PaymentGatewayClient paymentClient;
```

**New (4.0):**

```java
@MockitoSpyBean
private PaymentGatewayClient paymentClient;
```

## New Testing Features

### TestRestTemplate — 패키지 이동 + @AutoConfigureTestRestTemplate 필수

`TestRestTemplate`은 Spring Boot 4.0에서 deprecated되지 않았다. 단, **패키지가 이동**됐고 `@AutoConfigureTestRestTemplate` 어노테이션이 **필수**가 됐다.

#### 패키지 이동

```java
// Before (3.x)
import org.springframework.boot.test.web.client.TestRestTemplate;

// After (4.0)
import org.springframework.boot.resttestclient.TestRestTemplate;
```

#### @AutoConfigureTestRestTemplate 필수

```java
// Before (3.x): @SpringBootTest만으로 자동 주입됐음
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OrderIntegrationTest {
  @Autowired
  private TestRestTemplate restTemplate; // 동작했음
}
```

```java
// After (4.0): @AutoConfigureTestRestTemplate 반드시 추가
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate  // ← 반드시 추가
class OrderIntegrationTest {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void shouldCreateOrder() {
    ResponseEntity<Void> response = restTemplate.postForEntity(
      "/orders", new OrderRequest("Product", 2), Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }
}
```

#### WebTestClient (대안)

동일한 목적으로 `WebTestClient`도 사용 가능하다. `WebTestClient`는 fluent API를 제공하며 `@AutoConfigureWebTestClient`로 활성화한다:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OrderIntegrationTest {

  @Autowired
  private WebTestClient webClient;

  @Test
  void shouldCreateOrder() {
    webClient
      .post()
      .uri("/orders")
      .bodyValue(new OrderRequest("Product", 2))
      .exchange()
      .expectStatus().isCreated()
      .expectHeader().location("/orders/1");
  }
}
```

See [webtestclient.md](webtestclient.md) for complete WebTestClient reference.

## JUnit 6 Support

Spring Boot 4.0 uses JUnit 6 by default:

- JUnit 4 is deprecated (use JUnit Vintage temporarily)
- All JUnit 5 features still work
- Remove JUnit 4 dependencies for clean migration

## Testcontainers 2.0

Module naming changed:

**Before (1.x):**

```groovy
testImplementation 'org.testcontainers:postgresql'
```

**After (2.0):**

```groovy
testImplementation 'org.testcontainers:testcontainers-postgresql'
```

## Non-Singleton Bean Mocking

Spring Framework 7 allows mocking prototype-scoped beans:

```java
@Component
@Scope("prototype")
public class OrderProcessor { }

@SpringBootTest
class OrderServiceTest {
  @MockitoBean
  private OrderProcessor orderProcessor; // Now works!
}
```

## SpringExtension Context Changes

Extension context is now test-method scoped by default.

If tests fail with @Nested classes:

```java
@SpringExtensionConfig(useTestClassScopedExtensionContext = true)
@SpringBootTest
class OrderTest {
  // Use old behavior
}
```

## Migration Checklist

- [ ] Replace @MockBean with @MockitoBean
- [ ] Replace @SpyBean with @MockitoSpyBean
- [ ] Update TestRestTemplate import path: `org.springframework.boot.test.web.client` → `org.springframework.boot.resttestclient`
- [ ] Add `@AutoConfigureTestRestTemplate` to all `@SpringBootTest` tests that inject `TestRestTemplate`
- [ ] Update Testcontainers dependencies to 2.0 naming
- [ ] Add modular test starters as needed
- [ ] Remove JUnit 4 dependencies
- [ ] Update custom TestExecutionListener implementations
- [ ] Test @Nested class behavior
- [ ] Change service unit tests from @SpringBootTest to @ExtendWith(MockitoExtension.class)

## Backward Compatibility

Use "classic" starters for gradual migration:

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-test-classic'
```

This provides old behavior while you migrate incrementally.
