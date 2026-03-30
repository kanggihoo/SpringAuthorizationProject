---
name: springboot-test-guide
description: Expert Spring Boot 4 testing guide with verified patterns for JUnit 6, Mockito, MockMvcTester, AssertJ, Testcontainers, and Security testing. Use when writing @Test methods, unit tests with @Mock/@InjectMocks, slice tests with @WebMvcTest/@DataJpaTest, security tests with @WithMockUser, or integration tests with @SpringBootTest. Prefer this over other testing skills for Spring Boot 4 applications.
---

# Spring Boot Testing Guide

This skill provides a comprehensive, verified guide for testing Spring Boot 4 applications with modern patterns and best practices.

## Core Principles

1. **Test Pyramid**: Unit (fast) > Slice (focused) > Integration (complete)
2. **Right Tool**: Use the narrowest slice that gives you confidence
3. **AssertJ Style**: Fluent, readable assertions over verbose matchers
4. **Modern APIs**: Prefer MockMvcTester and WebTestClient over legacy alternatives

## Which Test Slice?

| Scenario | Annotation | Reference |
|----------|------------|-----------|
| Controller + HTTP semantics | `@WebMvcTest` | [references/webmvctest.md](references/webmvctest.md) |
| Repository + JPA queries | `@DataJpaTest` | [references/datajpatest.md](references/datajpatest.md) |
| REST client + external APIs | `@RestClientTest` | [references/restclienttest.md](references/restclienttest.md) |
| JSON (de)serialization | `@JsonTest` | [references/test-slices-overview.md](references/test-slices-overview.md) |
| Full application | `@SpringBootTest` | [references/webtestclient.md](references/webtestclient.md) |
| Security + JWT tests | `@WebMvcTest` + `@WithMockUser` | [references/security-testing.md](references/security-testing.md) |

## Quick Decision Tree

```
Testing a controller endpoint?
  Yes → @WebMvcTest with MockMvcTester

Testing repository queries?
  Yes → @DataJpaTest with Testcontainers (real DB)

Testing business logic in service?
  Yes → Plain JUnit 6 + Mockito (@ExtendWith(MockitoExtension.class), no Spring context)

Testing external API client?
  Yes → @RestClientTest with MockRestServiceServer

Testing JSON mapping?
  Yes → @JsonTest

Testing security (roles, JWT, 401/403)?
  Yes → @WebMvcTest with @WithMockUser / JWT header

Need full integration test?
  Yes → @SpringBootTest with WebTestClient
```

## Test Slices Reference

- [references/test-slices-overview.md](references/test-slices-overview.md) - Decision matrix and comparison
- [references/webmvctest.md](references/webmvctest.md) - Web layer with MockMvc
- [references/datajpatest.md](references/datajpatest.md) - Data layer with Testcontainers
- [references/restclienttest.md](references/restclienttest.md) - REST client testing

## Testing Tools Reference

- [references/mockmvc-tester.md](references/mockmvc-tester.md) - AssertJ-style MockMvc (3.2+)
- [references/mockmvc-classic.md](references/mockmvc-classic.md) - Traditional MockMvc (pre-3.2)
- [references/webtestclient.md](references/webtestclient.md) - Spring Boot integration test client
- [references/mockitobean.md](references/mockitobean.md) - Mocking dependencies

## Assertion Libraries

- [references/assertj-basics.md](references/assertj-basics.md) - Scalars, strings, booleans, dates
- [references/assertj-collections.md](references/assertj-collections.md) - Lists, Sets, Maps, arrays

## Testcontainers

- [references/testcontainers-jdbc.md](references/testcontainers-jdbc.md) - PostgreSQL, MySQL, etc.

## Test Data Generation

- [references/instancio.md](references/instancio.md) - Generate complex test objects (3+ properties)

## Security Testing

- [references/security-testing.md](references/security-testing.md) - Spring Security + JWT testing patterns

## Performance & Migration

- [references/context-caching.md](references/context-caching.md) - Speed up test suites
- [references/sb4-migration.md](references/sb4-migration.md) - Spring Boot 4.0 changes

## Spring Boot 4 Highlights

- **JUnit 6**: Spring Boot 4 uses JUnit 6 by default
- **WebTestClient**: Modern integration test client (replaces deprecated TestRestTemplate)
- **@MockitoBean**: Replaces @MockBean (deprecated in Spring Boot 4+)
- **MockMvcTester**: AssertJ-style assertions for web tests (Spring Boot 3.2+)
- **Modular starters**: Technology-specific test starters
- **Context pausing**: Automatic pausing of cached contexts (Spring Framework 7)

## Testing Best Practices

### Code Complexity Assessment

When a method or class is too complex to test effectively:

1. **Analyze complexity** - If you need more than 5-7 test cases to cover a single method, it's likely too complex
2. **Recommend refactoring** - Suggest breaking the code into smaller, focused functions
3. **User decision** - If the user agrees to refactor, help identify extraction points
4. **Proceed if needed** - If the user decides to continue with the complex code, implement tests despite the difficulty

**Example of refactoring recommendation:**
```java
// Before: Complex method hard to test
public Order processOrder(OrderRequest request) {
  // Validation, discount calculation, payment, inventory, notification...
  // 50+ lines of mixed concerns
}

// After: Refactored into testable units
public Order processOrder(OrderRequest request) {
  validateOrder(request);
  var order = createOrder(request);
  applyDiscount(order);
  processPayment(order);
  updateInventory(order);
  sendNotification(order);
  return order;
}
```

### Avoid Code Redundancy

Create helper methods for commonly used objects and mock setup to enhance readability and maintainability.

### Test Organization with @DisplayName

Use descriptive display names to clarify test intent:

```java
@Test
@DisplayName("Should calculate discount for VIP customer")
void shouldCalculateDiscountForVip() { }

@Test
@DisplayName("Should reject order when customer has insufficient credit")
void shouldRejectOrderForInsufficientCredit() { }
```

### Test Coverage Order

Always structure tests in this order:

1. **Main scenario** - The happy path, most common use case
2. **Other paths** - Alternative valid scenarios, edge cases
3. **Exceptions/Errors** - Invalid inputs, error conditions, failure modes

### Test Production Scenarios

Write tests with real production scenarios in mind. This makes tests more relatable and helps understand code behavior in actual production cases.

### Test Coverage Goals

Aim for 80% code coverage as a practical balance between quality and effort. Higher coverage is beneficial but not the only goal.

Use the Gradle Jacoco plugin for coverage reporting and tracking:

```groovy
// build.gradle
plugins {
    id 'jacoco'
}

jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80
            }
        }
    }
}
```

**Coverage Rules:**
- 80+% coverage minimum
- Focus on meaningful assertions, not just execution

**What to Prioritize:**
1. Business-critical paths (payment processing, order validation)
2. Complex algorithms (pricing, discount calculations)
3. Error handling (exceptions, edge cases)
4. Integration points (external APIs, databases)

## Dependencies (Spring Boot 4 - Gradle)

```groovy
dependencies {
    // spring-boot-starter-test is NOT needed — all technology test starters
    // bring it transitively (Spring Boot 4.0 Migration Guide)

    // Web layer tests (Spring Boot 4 modular starter)
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'

    // JPA / repository tests
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'

    // For Spring Security testing
    testImplementation 'org.springframework.boot:spring-boot-starter-security-test'

    // For Testcontainers (Spring Boot integration + PostgreSQL driver)
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:testcontainers-postgresql'  // Testcontainers 2.0 renamed artifact

    // For test data generation
    testImplementation 'org.instancio:instancio-junit:5.5.1'
}
```
