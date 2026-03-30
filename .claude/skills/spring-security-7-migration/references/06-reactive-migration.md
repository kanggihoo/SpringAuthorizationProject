# Spring Security 7 - Reactive 애플리케이션 마이그레이션

> 공식 문서: https://docs.spring.io/spring-security/reference/migration/reactive.html
> **Spring WebFlux(Reactive) + JWT를 사용하는 경우에만 해당**

---

## 개요

Reactive 애플리케이션에서의 Spring Security 7 마이그레이션은 Servlet 방식과 유사하지만,
`NimbusJwtDecoder` 대신 `NimbusReactiveJwtDecoder`를 사용한다는 점이 다르다.

---

## JWT `typ` 헤더 검증 변경

### 변경 내용

Servlet과 동일하게, Spring Security 6.5에서 필요했던 `validateTypes(false)` 설정과
명시적 `JwtTypeValidator` 추가가 Spring Security 7에서는 불필요해졌다.

모든 `JwtValidators.createDefaultXXX()` 메서드에 `JwtTypeValidator#jwt()`가 자동으로
포함된다.

### Before (Spring Security 6.5 방식)

```java
@Bean
ReactiveJwtDecoder jwtDecoder() {
    NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder
            .withIssuerLocation(issuerLocation)
            .validateTypes(false)  // (1) Nimbus의 typ 검증을 끔
            // ... 나머지 설정
            .build();

    // (2) JwtTypeValidator를 명시적으로 추가해야 했음
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
NimbusReactiveJwtDecoder jwtDecoder() {
    NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder
            .withIssuerLocation(issuerLocation)
            // (1) validateTypes(false) 제거 - 이제 기본값
            // ... 나머지 설정
            .build();

    // (2) createDefaultWithIssuer() 사용 - JwtTypeValidator#jwt()가 자동 포함됨
    jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerLocation));
    return jwtDecoder;
}
```

**변경 포인트:**
- `.validateTypes(false)` 제거
- `JwtValidators.createDefaultWithValidators(...)` → `JwtValidators.createDefaultWithIssuer(...)`
- 반환 타입을 `ReactiveJwtDecoder`에서 구체 타입 `NimbusReactiveJwtDecoder`로 변경 가능
- `JwtTypeValidator.jwt()`를 명시적으로 추가할 필요 없음

---

## Reactive DSL 방식은 변경 없음

`oauth2ResourceServer` Reactive DSL을 사용하는 일반적인 설정은 변경이 필요하지 않다.

```java
// 이 방식은 Spring Security 7에서도 그대로 동작
@Bean
public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .jwtDecoder(jwtDecoder())
            )
        );
    return http.build();
}
```

---

## Servlet과 Reactive 비교 요약

| 항목 | Servlet | Reactive |
|------|---------|----------|
| 디코더 클래스 | `NimbusJwtDecoder` | `NimbusReactiveJwtDecoder` |
| 빌더 메서드 | `NimbusJwtDecoder.withIssuerLocation()` | `NimbusReactiveJwtDecoder.withIssuerLocation()` |
| 검증기 설정 | `jwtDecoder.setJwtValidator(...)` | `jwtDecoder.setJwtValidator(...)` (동일) |
| DSL | `HttpSecurity.oauth2ResourceServer()` | `ServerHttpSecurity.oauth2ResourceServer()` |
| 필터 체인 Bean | `SecurityFilterChain` | `SecurityWebFilterChain` |
