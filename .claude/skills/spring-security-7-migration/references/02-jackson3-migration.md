# Spring Security 7 - Jackson 2 → Jackson 3 마이그레이션

> 공식 문서: https://docs.spring.io/spring-security/reference/migration/index.html
> **모든 Spring Security 7 사용자에게 해당되는 필수 Breaking Change**

---

## 개요

Spring Security 7은 Jackson 3를 기본으로 사용한다. Jackson 3은 패키지 구조가 변경되었으며
(`com.fasterxml.jackson` → `tools.jackson`), Spring Security의 직렬화/역직렬화 모듈도
이에 맞춰 교체되었다.

**변경 요약:**
- `SecurityJackson2Modules` → `SecurityJacksonModules`
- `ObjectMapper` → `JsonMapper`
- Jackson 2 포맷으로 직렬화된 데이터는 Jackson 3으로도 역직렬화 가능 (하위 호환)

---

## 코드 변경

### Before (Spring Security 6.x - Jackson 2)

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.jackson2.SecurityJackson2Modules;

ObjectMapper objectMapper = new ObjectMapper();
objectMapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
```

### After (Spring Security 7 - Jackson 3)

```java
import tools.jackson.databind.json.JsonMapper;
import org.springframework.security.jackson3.SecurityJacksonModules;

JsonMapper mapper = JsonMapper.builder()
    .addModules(SecurityJacksonModules.getModules(classLoader))
    .build();
```

**핵심 변경 포인트:**
- `ObjectMapper` 대신 `JsonMapper.builder()` 패턴 사용
- `SecurityJackson2Modules` → `SecurityJacksonModules` (패키지도 `jackson2` → `jackson3`)
- `SecurityJacksonModules`를 사용하면 자동으로 타입 정보 포함 및 `PolymorphicTypeValidator` 설정

---

## OAuth2 Authorization Server 의존성 변경

`spring-security-oauth2-authorization-server`는 Spring Security 7부터 Jackson 3을 기본으로 사용한다.

### Jackson 3 계속 사용 (권장)

별도 설정 불필요. Spring Boot 4.0 사용 시 자동으로 처리된다.

### Jackson 2를 계속 써야 하는 경우 (레거시 호환)

Jackson 3 의존성을 제외하고 Jackson 2를 명시적으로 추가한다.

**Maven (`pom.xml`):**

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-authorization-server</artifactId>
    <version>7.0.4</version>
    <exclusions>
        <exclusion>
            <groupId>tools.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.18.0</version>
</dependency>
```

**Gradle (`build.gradle`):**

```gradle
dependencies {
    implementation('org.springframework.security:spring-security-oauth2-authorization-server:7.0.4') {
        exclude group: 'tools.jackson.core', module: 'jackson-databind'
    }
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.0'
}
```

---

## 하위 호환성

Jackson 3의 직렬화 포맷은 Jackson 2 포맷과 **역방향 호환**된다.
즉, Jackson 2로 직렬화된 세션/토큰 데이터는 Jackson 3 환경에서도 역직렬화 가능하다.
단, Jackson 3으로 직렬화된 데이터는 Jackson 2로 역직렬화할 수 없으므로,
롤백이 필요한 경우 주의가 필요하다.

---

## 참고: Jackson 3 공식 마이그레이션 가이드

더 자세한 Jackson 2 → 3 전환 방법은 아래를 참조:
https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md
