# @MockitoBean

Mocking dependencies in Spring Boot tests (replaces deprecated @MockBean in Spring Boot 4+).

## Overview

`@MockitoBean` replaces the deprecated `@MockBean` annotation in Spring Boot 4.0+. It creates a Mockito mock and registers it in the Spring context, replacing any existing bean of the same type.

**Key rule**: Only use `@MockitoBean` when you need a Spring context. For pure service/business-logic unit tests, use plain `@Mock` + `@InjectMocks` with `@ExtendWith(MockitoExtension.class)` instead.

## When to Use @MockitoBean vs @Mock

| Scenario | Use |
|----------|-----|
| `@WebMvcTest` — mocking service called by controller | `@MockitoBean` |
| `@SpringBootTest` — replacing a real bean with a mock | `@MockitoBean` |
| Pure service unit test — no Spring context needed | `@Mock` + `@InjectMocks` |
| Plain Java class with constructor/field injection | `@Mock` + `@InjectMocks` |

## Basic Usage — Controller Test (@WebMvcTest)

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

  @MockitoBean
  private OrderService orderService;

  @MockitoBean
  private UserService userService;
}
```

## WRONG vs CORRECT: Service Unit Test

### WRONG — Do NOT use @SpringBootTest for service unit tests

```java
// WRONG: Loads full Spring context just to test a service method
@SpringBootTest
class OrderServiceTest {
  @MockitoBean
  private OrderRepository orderRepository;

  @Autowired
  private OrderService orderService;
}
```

### CORRECT — Use @ExtendWith(MockitoExtension.class) for service unit tests

```java
// CORRECT: No Spring context, fast, focused unit test
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @InjectMocks
  private OrderServiceImpl orderService;

  @Test
  void shouldCreateOrder() {
    given(orderRepository.save(any())).willReturn(new Order(1L));

    Long id = orderService.createOrder(new OrderRequest());

    assertThat(id).isEqualTo(1L);
    verify(orderRepository).save(any(Order.class));
  }
}
```

## Supported Test Slices for @MockitoBean

- `@WebMvcTest` - Mock service/repository dependencies
- `@WebFluxTest` - Mock reactive service dependencies
- `@SpringBootTest` - Replace real beans with mocks

## Stubbing Methods

### Basic Stub

```java
@Test
void shouldReturnOrder() {
  Order order = new Order(1L, "PENDING");
  given(orderService.findById(1L)).willReturn(order);

  // Test code
}
```

### Multiple Returns

```java
given(orderService.findById(anyLong()))
  .willReturn(new Order(1L, "PENDING"))
  .willReturn(new Order(2L, "COMPLETED"));
```

### Throwing Exceptions

```java
given(orderService.findById(999L))
  .willThrow(new OrderNotFoundException(999L));
```

### Argument Matching

```java
given(orderService.create(argThat(req -> req.getQuantity() > 0)))
  .willReturn(1L);

given(orderService.findByStatus(eq("PENDING")))
  .willReturn(List.of(new Order()));
```

## Verifying Interactions

### Verify Method Called

```java
verify(orderService).findById(1L);
```

### Verify Never Called

```java
verify(orderService, never()).delete(any());
```

### Verify Count

```java
verify(orderService, times(2)).findById(anyLong());
verify(orderService, atLeastOnce()).findByStatus(anyString());
```

### Verify Order

```java
InOrder inOrder = inOrder(orderService, userService);
inOrder.verify(orderService).findById(1L);
inOrder.verify(userService).getUser(any());
```

## Resetting Mocks

Mocks are reset between tests automatically. To reset mid-test:

```java
Mockito.reset(orderService);
```

## @MockitoSpyBean for Partial Mocking

Use `@MockitoSpyBean` to wrap a real bean with Mockito.

```java
@SpringBootTest
class OrderServiceIntegrationTest {

  @MockitoSpyBean
  private PaymentGatewayClient paymentClient;

  @Test
  void shouldProcessOrder() {
    doReturn(true).when(paymentClient).processPayment(any());

    // Test with real service but mocked payment client
  }
}
```

## @TestBean for Custom Test Beans

Register a custom bean instance in the test context:

```java
@SpringBootTest
class OrderServiceTest {

  @TestBean
  private PaymentGatewayClient paymentClient() {
    return new FakePaymentClient();
  }
}
```

## Scoping: Singleton vs Prototype

Spring Framework 7+ (Spring Boot 4+) supports mocking non-singleton beans:

```java
@Component
@Scope("prototype")
public class OrderProcessor {
  public String process() { return "real"; }
}

@SpringBootTest
class OrderServiceTest {
  @MockitoBean
  private OrderProcessor orderProcessor;

  @Test
  void shouldWorkWithPrototype() {
    given(orderProcessor.process()).willReturn("mocked");
    // Test code
  }
}
```

## Common Patterns

### Mocking Repository in Service Test (CORRECT Pattern)

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @InjectMocks
  private OrderServiceImpl orderService;

  @Test
  void shouldCreateOrder() {
    given(orderRepository.save(any())).willReturn(new Order(1L));

    Long id = orderService.createOrder(new OrderRequest());

    assertThat(id).isEqualTo(1L);
    verify(orderRepository).save(any(Order.class));
  }
}
```

### Multiple Mocks of Same Type

Use bean names:

```java
@MockitoBean(name = "primaryDataSource")
private DataSource primaryDataSource;

@MockitoBean(name = "secondaryDataSource")
private DataSource secondaryDataSource;
```

## Migration from @MockBean

### Before (Deprecated)

```java
@MockBean
private OrderService orderService;
```

### After (Spring Boot 4+)

```java
@MockitoBean
private OrderService orderService;
```

## Key Differences from Mockito @Mock

| Feature | @MockitoBean | @Mock |
| ------- | ------------ | ----- |
| Context integration | Yes | No |
| Spring lifecycle | Participates | None |
| Works with @Autowired | Yes | No |
| Test slice support | Yes | Limited |
| Speed | Slower (context load) | Faster (no context) |

## Best Practices

1. Use `@MockitoBean` only when Spring context is involved
2. For pure unit tests, use Mockito's `@Mock` with `@ExtendWith(MockitoExtension.class)`
3. Always verify interactions that have side effects
4. Don't verify simple queries (stubbing is enough)
5. Reset mocks if test modifies shared mock state
