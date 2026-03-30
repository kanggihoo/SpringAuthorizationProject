# 시스템 요구사항

## 최소 요구사항 요약

| 항목 | 최소 버전 | 권장 |
|---|---|---|
| Java | 17 | 최신 LTS (21+) |
| Kotlin | 2.2 | 최신 |
| GraalVM (native-image) | 25 | 최신 |
| Jakarta EE | 11 | - |
| Servlet | 6.1 | - |
| Spring Framework | 7.0 | - |
| Spring Security | 7.0 | - |

---

## Java 버전

Spring Boot 4.0은 **Java 17을 최소 요구**한다. Java 11, 8은 지원하지 않는다.

### 확인 방법

```bash
java -version
# java version "17.0.x" 이상이어야 함
```

### Maven 설정

```xml
<properties>
  <java.version>17</java.version>
</properties>
```

### Gradle 설정

```groovy
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

> **왜 17인가?** Spring Framework 7.0 자체가 Java 17의 sealed classes, record, text block 등
> 언어 기능을 적극 활용하기 때문이다. Java 21의 virtual thread (Project Loom)도 공식 지원한다.

---

## Kotlin 버전

Kotlin을 사용하는 경우 **2.2 이상** 필요.

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
}
```

Kotlin 1.x는 지원하지 않는다. K2 컴파일러 기반의 Kotlin 2.x로 마이그레이션 필요.

---

## GraalVM Native Image

GraalVM native-image를 사용하는 경우 **버전 25 이상** 필요.

```bash
# GraalVM 버전 확인
native-image --version
```

GraalVM 21, 22 기반 native-image 빌드는 더 이상 지원하지 않는다.

---

## Jakarta EE 11 / Servlet 6.1

Spring Boot 4.0은 **Jakarta EE 11** 기반으로 전환됐다 (Spring Boot 3.x는 Jakarta EE 9/10).

### 주요 영향

- `javax.*` 패키지는 이미 Spring Boot 3.x에서 `jakarta.*`로 전환됐으므로,
  3.x를 경유해 업그레이드하면 이 부분은 이미 완료된 상태.
- Servlet 6.1 API를 사용하는 코드는 새로운 메서드/인터페이스 확인 필요.
- **Undertow는 Servlet 6.1 비호환으로 제거됨** → `02-removed-features.md` 참고.

---

## Spring Framework / Security 버전

Spring Boot 4.0의 핵심 의존성:

| 라이브러리 | 버전 |
|---|---|
| Spring Framework | 7.0 |
| Spring Security | 7.0 |
| Spring Data | 4.0 |
| Spring Batch | 6.0 |

각 라이브러리의 자체 마이그레이션 가이드도 함께 확인하라:
- [Spring Framework 7.0 Migration Guide](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-7.x)
- [Spring Security 7.0 Migration Guide](https://docs.spring.io/spring-security/reference/migration/index.html)
