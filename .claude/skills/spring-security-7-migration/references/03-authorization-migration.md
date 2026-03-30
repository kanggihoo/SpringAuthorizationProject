# Spring Security 7 - Authorization 마이그레이션

> 공식 문서: https://docs.spring.io/spring-security/reference/migration/servlet/authorization.html
> **레거시 Access API(`AccessDecisionManager`, `AccessDecisionVoter`) 사용 시 해당**

---

## 개요

Spring Security 5부터 기존 Access API(`AccessDecisionManager`, `AccessDecisionVoter`)는
더 현대적인 Authorization API로 대체되었고, Spring Security 7에서는 이 레거시 컴포넌트들이
별도 모듈(`spring-security-access`)로 분리되었다.

---

## 변경 사항: 레거시 Access API 모듈 분리

다음 클래스들이 핵심 모듈에서 제거되고 `spring-security-access` 모듈로 이동되었다:

- `AccessDecisionManager`
- `AccessDecisionVoter`
- 관련 레거시 Authorization 클래스들

### 영향받는 코드 확인 방법

프로젝트 내에서 아래 import를 사용 중인지 확인한다:

```java
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.access.vote.ConsensusBased;
import org.springframework.security.access.vote.UnanimousBased;
```

이런 import가 있다면 아래 의존성을 추가해야 한다.

---

## 대응 방법 1: `spring-security-access` 모듈 추가 (단기 대응)

레거시 코드를 바로 교체하기 어려운 경우, 별도 모듈을 추가하면 기존 코드를 그대로 유지할 수 있다.

**Maven (`pom.xml`):**

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-access</artifactId>
</dependency>
```

**Gradle (`build.gradle`):**

```gradle
dependencies {
    implementation 'org.springframework.security:spring-security-access'
}
```

---

## 대응 방법 2: Authorization API로 마이그레이션 (권장)

`spring-security-access`는 레거시 지원 모듈이므로, 장기적으로는 현대적인 Authorization API로
전환하는 것을 권장한다.

### Before (레거시 Access API)

```java
@Bean
public AccessDecisionManager accessDecisionManager() {
    List<AccessDecisionVoter<?>> voters = Arrays.asList(
        new RoleVoter(),
        new AuthenticatedVoter()
    );
    return new AffirmativeBased(voters);
}

@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeRequests(authorize -> authorize
            .anyRequest().authenticated()
        )
        .accessDecisionManager(accessDecisionManager());
    return http.build();
}
```

### After (현대적 Authorization API)

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest().authenticated()
        );
    return http.build();
}
```

**주요 변경 포인트:**
- `authorizeRequests()` → `authorizeHttpRequests()` (Spring Security 6.x부터 권장)
- `AccessDecisionManager` 직접 설정 불필요
- 커스텀 `AccessDecisionVoter` 로직은 `AuthorizationManager`로 대체

### 커스텀 Voter → AuthorizationManager 전환 예시

```java
// Before: 커스텀 AccessDecisionVoter
public class CustomVoter implements AccessDecisionVoter<Object> {
    @Override
    public int vote(Authentication authentication, Object object,
                    Collection<ConfigAttribute> attributes) {
        // 커스텀 로직
        return ACCESS_GRANTED;
    }
}

// After: AuthorizationManager
@Bean
public AuthorizationManager<RequestAuthorizationContext> customAuthorizationManager() {
    return (authentication, context) -> {
        // 커스텀 로직
        return new AuthorizationDecision(true);
    };
}

@Bean
public SecurityFilterChain filterChain(HttpSecurity http,
        AuthorizationManager<RequestAuthorizationContext> customAuthorizationManager)
        throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest().access(customAuthorizationManager)
        );
    return http.build();
}
```

---

## Method Security 관련 변경

`@EnableGlobalMethodSecurity`는 Spring Security 6.x에서 이미 deprecated되었다.
Spring Security 7에서는 `@EnableMethodSecurity`를 사용한다.

```java
// Before (deprecated)
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)

// After
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
```
