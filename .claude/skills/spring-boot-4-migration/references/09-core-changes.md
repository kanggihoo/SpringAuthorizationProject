# 핵심 기능 변경사항

JSpecify nullability, Logback charset, 패키지 이동, PropertyMapper, DevTools,
**Actuator 헬스 프로브** 등 Spring Boot 4.0의 핵심 레이어 변경사항.

---

## JSpecify Nullability 어노테이션

### 변경사항
Spring Framework 7.0이 자체 null-safety 어노테이션(`@NonNull`, `@Nullable` 등)을
**JSpecify** 표준 어노테이션으로 교체했다.

### 영향
- Spring 소스를 상속/구현하거나 Spring API를 직접 호출하는 코드에서
  null checker(IntelliJ, NullAway, Checker Framework 등) 경고/오류 발생 가능.
- 직접 비즈니스 로직에는 영향 없음.

### 의존성 추가 (null checker 사용 시)

```xml
<dependency>
  <groupId>org.jspecify</groupId>
  <artifactId>jspecify</artifactId>
</dependency>
```

### 어노테이션 변경 (Spring API 직접 사용 시)

```java
// 이전 (Spring null-safety)
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public class MyBean {
    public @NonNull String process(@Nullable String input) { ... }
}
```

```java
// 이후 (JSpecify)
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class MyBean {
    public @NonNull String process(@Nullable String input) { ... }
}
```

> Spring의 `@NonNull`/`@Nullable`은 deprecated 처리되며 당분간 유지되므로,
> 컴파일 오류는 아니다. 하지만 IDE나 null checker가 경고를 발생시킬 수 있다.

---

## Logback 기본 Charset 변경

### 변경사항
Logback의 기본 인코딩이 **UTF-8**로 변경됐다 (Log4j2와 동일).
이전에는 시스템 기본 charset을 따랐다.

### 영향
- 한국어, 한자 등 멀티바이트 문자를 로그에 출력할 때 인코딩 문제가 **해소**될 수 있음.
- 반대로, 기존에 특정 인코딩(EUC-KR 등)으로 로그를 수집하던 파이프라인이 있다면 깨질 수 있음.

### 기존 charset 유지 필요 시

`logback-spring.xml`에서 명시적으로 설정:

```xml
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <charset>EUC-KR</charset>  <!-- 기존 charset 명시 -->
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
</configuration>
```

---

## 패키지 이동

### BootstrapRegistry

```java
// 이전
import org.springframework.boot.context.BootstrapRegistry;
import org.springframework.boot.context.BootstrapRegistryInitializer;

// 이후
import org.springframework.boot.bootstrap.BootstrapRegistry;
import org.springframework.boot.bootstrap.BootstrapRegistryInitializer;
```

### EnvironmentPostProcessor

```java
// 이전
import org.springframework.boot.env.EnvironmentPostProcessor;

// 이후
import org.springframework.boot.EnvironmentPostProcessor;
```

### 패키지 이동 전체 요약

| 클래스 | 이전 패키지 | 이후 패키지 |
|---|---|---|
| `BootstrapRegistry` | `org.springframework.boot.context` | `org.springframework.boot.bootstrap` |
| `BootstrapRegistryInitializer` | `org.springframework.boot.context` | `org.springframework.boot.bootstrap` |
| `EnvironmentPostProcessor` | `org.springframework.boot.env` | `org.springframework.boot` |

---

## PropertyMapper — null 처리 동작 변경

### 변경사항
`PropertyMapper`가 `null` 값에 대해 어댑터/프레디케이트를 **더 이상 호출하지 않는다**.
이전에는 null도 체인을 통과했다.

### 영향
Auto-configuration이나 configuration properties 매핑 코드를 직접 작성한 경우.

```java
// 이전 동작: null 값도 whenNonNull()이 호출됨 (버그처럼 동작)
PropertyMapper map = PropertyMapper.get();
map.from(source::getValue)
   .whenNonNull()  // null이어도 호출됐음
   .to(target::setValue);

// 이후 동작: null이면 whenNonNull()이 호출되지 않음 (올바른 동작)
// 위 코드는 의도대로 null을 필터링함
```

### null도 포함해서 매핑이 필요한 경우

```java
// always()를 사용하면 null 포함 모든 값 매핑
PropertyMapper map = PropertyMapper.get();
map.from(source::getValue)
   .always()  // null 포함
   .to(target::setValue);
```

---

## DevTools LiveReload — 기본값 변경

### 변경사항
DevTools의 LiveReload 기능이 **기본적으로 비활성화**됐다.

### 활성화 방법

```properties
# application-local.properties (또는 application-dev.properties)
spring.devtools.livereload.enabled=true
```

### 기타 DevTools 설정 (변경 없음)

```properties
# 자동 재시작은 여전히 기본 활성화
spring.devtools.restart.enabled=true

# 정적 자원 변경 시 재시작 제외 경로
spring.devtools.restart.exclude=static/**,public/**
```

---

## Actuator 동작 변경사항

### Actuator 엔드포인트에서 org.springframework.lang.Nullable 제거

### 변경사항
Actuator 엔드포인트 파라미터에서
`org.springframework.lang.Nullable`을 사용하는 구조를 **더 이상 지원하지 않는다**.

### 영향
기존 custom Actuator endpoint에서 해당 어노테이션을 사용하였다면 JSpecify로 전환해야 한다.

```java
// 이전
@ReadOperation
public MyData getData(@org.springframework.lang.Nullable String filter) {
    // ...
}

// 이후 — JSpecify 어노테이션 사용
@ReadOperation
public MyData getData(@org.jspecify.annotations.Nullable String filter) {
    // ...
}
```

---

## Actuator Liveness / Readiness Probe — 기본값 변경

### 변경사항
Liveness와 Readiness 헬스 프로브가 **기본적으로 활성화**됨으로써,
`/actuator/health` 엔드포인트가 `liveness` 및 `readiness` 그룹을 기본으로 노출한다.

이전엔는 Kubernetes 환경에서만 활성화됐는 기능이 이제에는 로칼 환경에서도 활성화된다.

### 비활성화 방법 (Probe 불필요 시)

```properties
# application.properties
management.endpoint.health.probes.enabled=false
```

### Kubernetes 매니페스트 예시 (변경 없음)

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
```

---

## 체크리스트

- [ ] null checker 사용 시 JSpecify import 경로 확인
- [ ] Logback charset 의존 로그 파이프라인 검토
- [ ] `BootstrapRegistry` import → `org.springframework.boot.bootstrap`
- [ ] `EnvironmentPostProcessor` import → `org.springframework.boot`
- [ ] `PropertyMapper` null 처리 변경으로 인한 설정 클래스 동작 검증
- [ ] DevTools LiveReload 필요 시 `spring.devtools.livereload.enabled=true` 추가
- [ ] Custom Actuator endpoint에서 `org.springframework.lang.Nullable` → `org.jspecify.annotations.Nullable` 전환
- [ ] Liveness/Readiness Probe 자동 활성화 영향 확인 (불필요 시 `management.endpoint.health.probes.enabled=false`)
