# Spring Security 7 - OAuth2 / JWT 마이그레이션 (Servlet)

> 공식 문서: https://docs.spring.io/spring-security/reference/migration/servlet/oauth2.html
> **OAuth2 Resource Server 또는 JWT 디코더 사용 시 해당**

---

## 목차

1. [JWT `typ` 헤더 검증 변경 (`JwtTypeValidator`)](#1-jwt-typ-헤더-검증-변경)
2. [BearerToken 설정 변경 (`BearerTokenAuthenticationConverter`)](#2-bearertoken-설정-변경)

---

## 1. JWT `typ` 헤더 검증 변경

### 변경 내용

Spring Security 6.5에서 JWT `typ` 헤더 검증을 위해 `validateTypes(false)` + 명시적
`JwtTypeValidator` 추가가 필요했다. **Spring Security 7에서는 이 설정이 기본값으로 통합**되어
별도 설정 없이도 자동으로 `JwtTypeValidator#jwt()`가 포함된다.

### Before (Spring Security 6.5 방식)

```java
@Bean
JwtDecoder jwtDecoder() {
    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuerLocation)
            .validateTypes(false)  // Nimbus의 typ 검증을 끔
            // ... 나머지 설정
            .build();

    // JwtTypeValidator를 명시적으로 추가해야 했음
    jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
        new JwtIssuerValidator(issuerLocation),
        JwtTypeValidator.jwt()
    ));
    return jwtDecoder;
}
```

### After (Spring Security 7 방식)

```java
@Bean
JwtDecoder jwtDecoder() {
    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuerLocation)
            // validateTypes(false) 제거 - 이제 기본값
            // ... 나머지 설정
            .build();

    // createDefaultWithIssuer() 사용 - JwtTypeValidator#jwt()가 자동 포함됨
    jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerLocation));
    return jwtDecoder;
}
```

**변경 포인트:**
- `.validateTypes(false)` 제거
- `JwtValidators.createDefaultWithValidators(...)` → `JwtValidators.createDefaultWithIssuer(...)`
- `JwtTypeValidator.jwt()`를 명시적으로 추가할 필요 없음 (모든 `createDefaultXXX` 메서드에 자동 포함)

---

## 2. BearerToken 설정 변경

### 변경 내용

`BearerTokenAuthenticationFilter`에서 직접 설정하던 `BearerTokenResolver`와
`AuthenticationDetailsSource`가 deprecated되었다.
이 설정들은 이제 `BearerTokenAuthenticationConverter`에서 해야 한다.

> **주의**: `oauth2ResourceServer` DSL을 사용하는 일반적인 경우에는 이 변경이 필요하지 않다.
> `BearerTokenAuthenticationFilter`를 직접 생성하여 사용하는 경우에만 해당된다.

### Deprecated 메서드

- `BearerTokenAuthenticationFilter#setBearerTokenResolver()`
- `BearerTokenAuthenticationFilter#setAuthenticationDetailsSource()`

### Before (직접 필터에 설정)

```java
BearerTokenAuthenticationFilter filter =
    new BearerTokenAuthenticationFilter(authenticationManager);
filter.setBearerTokenResolver(myBearerTokenResolver);
filter.setAuthenticationDetailsSource(myAuthenticationDetailsSource);
```

### After (`BearerTokenAuthenticationConverter` 사용)

```java
BearerTokenAuthenticationConverter authenticationConverter =
    new BearerTokenAuthenticationConverter();
authenticationConverter.setBearerTokenResolver(myBearerTokenResolver);
authenticationConverter.setAuthenticationDetailsSource(myAuthenticationDetailsSource);

BearerTokenAuthenticationFilter filter =
    new BearerTokenAuthenticationFilter(authenticationManager, authenticationConverter);
```

**변경 포인트:**
- `BearerTokenAuthenticationConverter`를 별도로 생성
- `BearerTokenResolver`와 `AuthenticationDetailsSource`를 컨버터에 설정
- 컨버터를 필터 생성자의 두 번째 인자로 전달

---

## DSL 방식 설정은 변경 없음

`oauth2ResourceServer` DSL을 사용하는 일반적인 설정은 변경이 필요하지 않다.

```java
// 이 방식은 Spring Security 7에서도 그대로 동작
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .decoder(jwtDecoder())
            )
        );
    return http.build();
}
```
