# WebTestClient (Spring Boot Integration Testing)

`WebTestClient` is the modern integration test client for Spring Boot applications. It replaces the deprecated `TestRestTemplate` in Spring Boot 4.0 and supports both Spring WebMVC (via MockMvc) and Spring WebFlux.

## When to Use WebTestClient

- `@SpringBootTest` full integration tests that test the full HTTP stack
- Tests that need to verify real Spring Security filter behavior end-to-end
- Tests that need to test real database interactions + HTTP behavior together

For controller-only slice tests, prefer `@WebMvcTest` + `MockMvcTester` instead.

## Setup: Real Server (RANDOM_PORT)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OrderIntegrationTest {

  @Autowired
  private WebTestClient webClient;

  @Test
  void shouldCreateOrder() {
    webClient
      .post()
      .uri("/orders")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(new OrderRequest("Product A", 2))
      .exchange()
      .expectStatus().isCreated()
      .expectHeader().location("/orders/1");
  }
}
```

## Setup: MockMvc (No Real Server, Faster)

> **Spring Boot 4.0 Breaking Change**: `@SpringBootTest`에서 `MockMvc`를 주입받으려면 반드시 `@AutoConfigureMockMvc`를 명시해야 한다. 이전처럼 자동 주입되지 않는다. (`@WebMvcTest`는 영향 없음)

You can also use WebTestClient with MockMvc (no server startup):

```java
// Spring Boot 4.0: @AutoConfigureMockMvc 반드시 필요
@SpringBootTest
@AutoConfigureMockMvc           // ← 반드시 추가 (4.0 Breaking Change)
@AutoConfigureWebTestClient
class OrderMockIntegrationTest {

  @Autowired
  private WebTestClient webClient;

  @Test
  void shouldReturnOrder() {
    webClient
      .get()
      .uri("/orders/1")
      .exchange()
      .expectStatus().isOk();
  }
}
```

MockMvc를 직접 주입하는 경우도 동일하다:

```java
// Spring Boot 4.0: MockMvc 직접 주입 시에도 @AutoConfigureMockMvc 필수
@SpringBootTest
@AutoConfigureMockMvc           // ← 반드시 추가
class OrderMockMvcTest {

  @Autowired
  private MockMvc mockMvc;      // @AutoConfigureMockMvc 없으면 주입 실패

  @Test
  void shouldReturnOrder() throws Exception {
    mockMvc.perform(get("/orders/1"))
           .andExpect(status().isOk());
  }
}
```

## HTTP Methods

### GET Request

```java
@Test
void shouldGetOrder() {
  webClient
    .get()
    .uri("/orders/{id}", 1L)
    .exchange()
    .expectStatus().isOk()
    .expectBody(OrderResponse.class)
    .value(response -> {
      assertThat(response.getId()).isEqualTo(1L);
      assertThat(response.getStatus()).isEqualTo("PENDING");
    });
}
```

### POST Request

```java
@Test
void shouldCreateOrder() {
  webClient
    .post()
    .uri("/orders")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(new OrderRequest("Laptop", 1))
    .exchange()
    .expectStatus().isCreated()
    .expectHeader().valueMatches("Location", "/orders/\\d+");
}
```

### PUT Request

```java
@Test
void shouldUpdateOrder() {
  webClient
    .put()
    .uri("/orders/{id}", 1L)
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(new UpdateOrderRequest("COMPLETED"))
    .exchange()
    .expectStatus().isOk();
}
```

### DELETE Request

```java
@Test
void shouldDeleteOrder() {
  webClient
    .delete()
    .uri("/orders/{id}", 1L)
    .exchange()
    .expectStatus().isNoContent();
}
```

## Status Assertions

```java
.expectStatus().isOk()            // 200
.expectStatus().isCreated()       // 201
.expectStatus().isNoContent()     // 204
.expectStatus().isBadRequest()    // 400
.expectStatus().isUnauthorized()  // 401
.expectStatus().isForbidden()     // 403
.expectStatus().isNotFound()      // 404
.expectStatus().is5xxServerError()
.expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
```

## Body Assertions

### Single Object

```java
.expectBody(OrderResponse.class)
.value(response -> {
  assertThat(response.getId()).isNotNull();
  assertThat(response.getStatus()).isEqualTo("PENDING");
});
```

### List of Objects

```java
.expectBodyList(OrderResponse.class)
.hasSize(3)
.value(orders -> {
  assertThat(orders).extracting(OrderResponse::getStatus)
    .containsExactly("NEW", "PENDING", "COMPLETED");
});
```

### JSON Path

```java
.expectBody()
.jsonPath("$.status").isEqualTo("PENDING")
.jsonPath("$.items").isArray()
.jsonPath("$.items.length()").isEqualTo(2);
```

### Raw String Body

```java
.expectBody(String.class)
.value(body -> assertThat(body).contains("PENDING"));
```

## With Authentication (JWT Header)

```java
@Test
void shouldReturnOrderForAuthenticatedUser() {
  String jwtToken = obtainJwtToken("user@example.com", "password");

  webClient
    .get()
    .uri("/api/orders")
    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
    .exchange()
    .expectStatus().isOk();
}

private String obtainJwtToken(String email, String password) {
  return webClient
    .post()
    .uri("/api/auth/login")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(new LoginRequest(email, password))
    .exchange()
    .expectStatus().isOk()
    .returnResult(TokenResponse.class)
    .getResponseBody()
    .blockFirst()
    .getAccessToken();
}
```

## With Request Headers

```java
webClient
  .get()
  .uri("/api/data")
  .header("X-Api-Key", "secret-key")
  .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
  .exchange()
  .expectStatus().isOk();
```

## With Query Parameters

```java
webClient
  .get()
  .uri(uriBuilder -> uriBuilder
    .path("/orders")
    .queryParam("status", "PENDING")
    .queryParam("page", "0")
    .queryParam("size", "10")
    .build())
  .exchange()
  .expectStatus().isOk();
```

## Full Integration Test with Real JWT Flow

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class AuthFlowIntegrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired
  private WebTestClient webClient;

  @Test
  void shouldAuthenticateAndAccessProtectedEndpoint() {
    // Step 1: Sign up
    webClient
      .post()
      .uri("/api/auth/signup")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(new SignupRequest("user@example.com", "password123", "John"))
      .exchange()
      .expectStatus().isCreated();

    // Step 2: Login and get token
    String token = webClient
      .post()
      .uri("/api/auth/login")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(new LoginRequest("user@example.com", "password123"))
      .exchange()
      .expectStatus().isOk()
      .returnResult(TokenResponse.class)
      .getResponseBody()
      .blockFirst()
      .getAccessToken();

    assertThat(token).isNotBlank();

    // Step 3: Access protected endpoint with token
    webClient
      .get()
      .uri("/api/protected")
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .exchange()
      .expectStatus().isOk();
  }
}
```

## Migration from TestRestTemplate

`TestRestTemplate` is deprecated in Spring Boot 4.0. Migrate to `WebTestClient`:

| TestRestTemplate (deprecated) | WebTestClient |
|-------------------------------|---------------|
| `@Autowired TestRestTemplate restTemplate` | `@Autowired WebTestClient webClient` |
| `restTemplate.getForObject("/orders/1", OrderResponse.class)` | `webClient.get().uri("/orders/1").exchange().expectBody(OrderResponse.class)` |
| `restTemplate.postForLocation("/orders", request)` | `webClient.post().uri("/orders").bodyValue(request).exchange().expectStatus().isCreated()` |
| `restTemplate.exchange(...)` | `webClient.method(HttpMethod.X).uri(...).exchange()` |
| Synchronous blocking | Fluent, non-blocking API (but can be used in blocking tests) |

## Key Points

1. Use `RANDOM_PORT` for tests that need real security filter chain behavior
2. Use `@AutoConfigureWebTestClient` — do not create WebTestClient manually in @SpringBootTest
3. Prefer `@WebMvcTest` + `MockMvcTester` for faster slice tests
4. Use `WebTestClient` when you need to verify full application behavior end-to-end
5. JWT flow integration tests are best suited for `WebTestClient` with real server
6. **Spring Boot 4.0**: `@SpringBootTest`에서 `MockMvc` 주입 시 `@AutoConfigureMockMvc` 필수 — `@WebMvcTest`는 해당 없음
