# Security Testing in Spring Boot

Testing Spring Security configuration, authentication, authorization, and JWT-based endpoints.

## Overview

Spring Security filters are loaded automatically in `@WebMvcTest`. This means:
- Unauthenticated requests to protected endpoints return 401
- Authenticated users without the right role return 403
- Use `@WithMockUser`, `@WithUserDetails`, or JWT headers to authenticate in tests

## Dependencies

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
```

## @WebMvcTest with Security

Spring Security is active by default in `@WebMvcTest`. You must either:
1. Import your `SecurityConfig` to use real security rules
2. Or use `@AutoConfigureMockMvc(addFilters = false)` to skip filters

### Importing SecurityConfig

```java
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerSecurityTest {

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private OrderService orderService;

  // If SecurityConfig depends on these beans, mock them:
  @MockitoBean
  private UserDetailsService userDetailsService;

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;
}
```

## @WithMockUser — Testing with an Authenticated User

`@WithMockUser` bypasses the actual authentication mechanism and injects a mock user into the security context.

### Basic Usage

```java
@Test
@WithMockUser
void shouldAccessProtectedEndpointAsDefaultUser() {
  assertThat(mvc.get().uri("/api/protected"))
    .hasStatusOk();
}
```

### With Custom Username and Roles

```java
@Test
@WithMockUser(username = "admin@example.com", roles = "ADMIN")
void adminShouldAccessAdminEndpoint() {
  assertThat(mvc.delete().uri("/api/admin/orders/1"))
    .hasStatus(HttpStatus.NO_CONTENT);
}

@Test
@WithMockUser(username = "user@example.com", roles = "USER")
void regularUserShouldBeForbiddenFromAdminEndpoint() {
  assertThat(mvc.delete().uri("/api/admin/orders/1"))
    .hasStatus(HttpStatus.FORBIDDEN);
}
```

### With Authorities (not roles)

Use `authorities` instead of `roles` when your security config uses `hasAuthority()`:

```java
@Test
@WithMockUser(authorities = "ROLE_ADMIN")
void shouldGrantAdminAccess() {
  assertThat(mvc.get().uri("/api/admin/stats"))
    .hasStatusOk();
}
```

## @WithUserDetails — Testing with Real UserDetails

`@WithUserDetails` loads a real `UserDetails` from your `UserDetailsService`. Use this when you need the actual user object loaded.

```java
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerWithUserDetailsTest {

  @MockitoBean
  private UserDetailsService userDetailsService;

  @MockitoBean
  private OrderService orderService;

  @BeforeEach
  void setUp() {
    // Register the user that @WithUserDetails will look up
    UserDetails user = User.withUsername("user@example.com")
      .password("{noop}password")
      .roles("USER")
      .build();
    given(userDetailsService.loadUserByUsername("user@example.com")).willReturn(user);
  }

  @Test
  @WithUserDetails("user@example.com")
  void shouldLoadRealUserDetails() {
    assertThat(mvc.get().uri("/api/orders"))
      .hasStatusOk();
  }
}
```

## @WithAnonymousUser — Testing Anonymous Access

```java
@Test
@WithAnonymousUser
void anonymousUserShouldBeRedirectedToLogin() {
  assertThat(mvc.get().uri("/api/protected"))
    .hasStatus(HttpStatus.UNAUTHORIZED);
}
```

## Testing 401 Unauthorized

An unauthenticated request to a protected endpoint:

```java
@Test
void unauthenticatedRequestShouldReturn401() {
  // No @WithMockUser — anonymous request
  assertThat(mvc.get().uri("/api/orders"))
    .hasStatus(HttpStatus.UNAUTHORIZED);
}
```

## Testing 403 Forbidden

An authenticated user without the required role:

```java
@Test
@WithMockUser(roles = "USER")
void userWithoutAdminRoleShouldReturn403() {
  assertThat(mvc.delete().uri("/api/admin/orders/1"))
    .hasStatus(HttpStatus.FORBIDDEN);
}
```

## JWT Bearer Token Testing

When your application uses JWT-based authentication (stateless), you cannot use `@WithMockUser` to test the actual JWT validation path. Instead, pass the JWT in the `Authorization` header.

### Option 1: Mock the JwtTokenProvider (Unit-level)

```java
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerJwtTest {

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private OrderService orderService;

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private CustomUserDetailsService userDetailsService;

  @Test
  void shouldAuthenticateWithValidJwt() {
    // Arrange: Mock JWT validation
    String token = "valid.jwt.token";
    UserDetails userDetails = User.withUsername("user@example.com")
      .password("{noop}password")
      .roles("USER")
      .build();

    given(jwtTokenProvider.validateToken(token)).willReturn(true);
    given(jwtTokenProvider.getUsername(token)).willReturn("user@example.com");
    given(userDetailsService.loadUserByUsername("user@example.com")).willReturn(userDetails);

    // Act & Assert
    assertThat(mvc.get().uri("/api/orders")
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .hasStatusOk();
  }

  @Test
  void shouldReturn401ForMissingJwt() {
    assertThat(mvc.get().uri("/api/orders"))
      .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldReturn401ForInvalidJwt() {
    String invalidToken = "invalid.token";
    given(jwtTokenProvider.validateToken(invalidToken)).willReturn(false);

    assertThat(mvc.get().uri("/api/orders")
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken))
      .hasStatus(HttpStatus.UNAUTHORIZED);
  }
}
```

### Option 2: Classic MockMvc with JWT Header

```java
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerClassicJwtTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private CustomUserDetailsService userDetailsService;

  @MockitoBean
  private OrderService orderService;

  @Test
  void shouldAuthenticateWithJwt() throws Exception {
    String token = "valid.jwt.token";
    UserDetails userDetails = User.withUsername("user@example.com")
      .password("{noop}password")
      .roles("USER")
      .build();

    given(jwtTokenProvider.validateToken(token)).willReturn(true);
    given(jwtTokenProvider.getUsername(token)).willReturn("user@example.com");
    given(userDetailsService.loadUserByUsername("user@example.com")).willReturn(userDetails);

    mvc.perform(get("/api/orders")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isOk());
  }
}
```

## Full Auth Controller Test

A complete test for login and signup endpoints:

```java
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private UserDetailsService userDetailsService;

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;

  @Test
  void shouldLoginSuccessfully() {
    TokenResponseDto tokenResponse = new TokenResponseDto("access.jwt.token", "refresh.jwt.token");
    given(authService.login(any(LoginRequestDto.class))).willReturn(tokenResponse);

    assertThat(mvc.post().uri("/api/auth/login")
      .contentType(MediaType.APPLICATION_JSON)
      .content("""
        {
          "email": "user@example.com",
          "password": "password123"
        }
        """))
      .hasStatusOk()
      .bodyJson()
      .convertTo(TokenResponseDto.class)
      .satisfies(response -> {
        assertThat(response.getAccessToken()).isEqualTo("access.jwt.token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh.jwt.token");
      });
  }

  @Test
  void shouldReturn401ForInvalidCredentials() {
    given(authService.login(any(LoginRequestDto.class)))
      .willThrow(new BadCredentialsException("Invalid credentials"));

    assertThat(mvc.post().uri("/api/auth/login")
      .contentType(MediaType.APPLICATION_JSON)
      .content("""
        {
          "email": "user@example.com",
          "password": "wrong-password"
        }
        """))
      .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldSignupNewUser() {
    given(authService.signup(any(SignupRequest.class))).willReturn(new UserCreatedResponse(1L));

    assertThat(mvc.post().uri("/api/auth/signup")
      .contentType(MediaType.APPLICATION_JSON)
      .content("""
        {
          "email": "newuser@example.com",
          "password": "password123",
          "name": "New User"
        }
        """))
      .hasStatus(HttpStatus.CREATED);
  }

  @Test
  void shouldReturn400ForDuplicateEmail() {
    given(authService.signup(any(SignupRequest.class)))
      .willThrow(new DuplicateEmailException("Email already registered"));

    assertThat(mvc.post().uri("/api/auth/signup")
      .contentType(MediaType.APPLICATION_JSON)
      .content("""
        {
          "email": "existing@example.com",
          "password": "password123",
          "name": "User"
        }
        """))
      .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldRefreshToken() {
    TokenResponseDto newTokens = new TokenResponseDto("new.access.token", "new.refresh.token");
    given(authService.refresh("valid.refresh.token")).willReturn(newTokens);

    assertThat(mvc.post().uri("/api/auth/refresh")
      .contentType(MediaType.APPLICATION_JSON)
      .content("""
        {
          "refreshToken": "valid.refresh.token"
        }
        """))
      .hasStatusOk()
      .bodyJson()
      .convertTo(TokenResponseDto.class)
      .satisfies(response -> {
        assertThat(response.getAccessToken()).isEqualTo("new.access.token");
      });
  }
}
```

## @SpringBootTest Full Integration Security Test

For full end-to-end security testing with real JWT generation:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class SecurityIntegrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired
  private WebTestClient webClient;

  @Test
  void shouldDenyAccessWithoutToken() {
    webClient
      .get()
      .uri("/api/orders")
      .exchange()
      .expectStatus().isUnauthorized();
  }

  @Test
  void shouldDenyAccessWithExpiredToken() {
    webClient
      .get()
      .uri("/api/orders")
      .header(HttpHeaders.AUTHORIZATION, "Bearer expired.jwt.token")
      .exchange()
      .expectStatus().isUnauthorized();
  }

  @Test
  void shouldGrantAccessAfterLogin() {
    // First: create a user via signup
    webClient
      .post()
      .uri("/api/auth/signup")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(new SignupRequest("test@example.com", "password123", "Test User"))
      .exchange()
      .expectStatus().isCreated();

    // Then: login to get a real JWT
    String accessToken = webClient
      .post()
      .uri("/api/auth/login")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(new LoginRequest("test@example.com", "password123"))
      .exchange()
      .expectStatus().isOk()
      .returnResult(TokenResponseDto.class)
      .getResponseBody()
      .blockFirst()
      .getAccessToken();

    assertThat(accessToken).isNotBlank();

    // Finally: access protected endpoint with real JWT
    webClient
      .get()
      .uri("/api/user/me")
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
      .exchange()
      .expectStatus().isOk();
  }
}
```

## Skipping Security Filters (When Needed)

If you just want to test controller logic without security:

```java
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerNoSecurityTest {

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private OrderService orderService;

  @Test
  void shouldReturnOrderWithoutAuthentication() {
    // Security filters are disabled — only test controller logic
    given(orderService.findById(1L)).willReturn(new Order(1L, "PENDING"));

    assertThat(mvc.get().uri("/api/orders/1"))
      .hasStatusOk();
  }
}
```

## Key Points

1. `@WebMvcTest` loads Spring Security by default — always account for authentication in tests
2. Use `@WithMockUser` to test authorization (role-based access) without real authentication
3. Import `SecurityConfig` with `@Import` when you need your actual security rules applied
4. Mock `JwtTokenProvider` and `UserDetailsService` when testing JWT filter behavior at the slice level
5. Use `@SpringBootTest` + `WebTestClient` for full end-to-end JWT flow tests
6. `@WithMockUser` does NOT test JWT parsing — it bypasses authentication entirely
7. Always test both the happy path (authenticated) and the error paths (401, 403)
