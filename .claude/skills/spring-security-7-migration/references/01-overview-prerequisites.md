# Spring Security 7 마이그레이션 - 개요 및 사전 조건

> 공식 문서: https://docs.spring.io/spring-security/reference/migration/index.html

---

## 권장 마이그레이션 경로

Spring Security 7로 바로 업그레이드하는 것보다 **6.5를 먼저 적용**하는 것을 강력히 권장한다.
6.5에는 7.0 방식으로 동작을 미리 전환해볼 수 있는 opt-in 설정이 포함되어 있다.

```
기존 (6.x 이하)  →  Spring Security 6.5  →  Spring Security 7.0
```

---

## 필수 사전 조건

| 항목 | 요구 버전 |
|------|----------|
| Spring Boot | 4.0 최신 패치 버전 |
| Spring Security | 7.0 최신 패치 버전 |
| Java | 17 이상 |

> Spring Boot 4.0에는 Spring Security 7이 기본 포함된다.
> Spring Boot를 사용 중이라면 별도로 Spring Security 버전을 명시하지 않아도 된다.

---

## 의존성 업데이트 방법

### Maven (`pom.xml`)

Spring Boot를 사용하는 경우 parent 버전만 올리면 자동으로 Spring Security 7이 적용된다.

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.0</version>
</parent>
```

Spring Boot 없이 Spring Security만 단독으로 사용하는 경우 BOM을 직접 import 한다.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-bom</artifactId>
            <version>7.0.4</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-config</artifactId>
    </dependency>
</dependencies>
```

---

### Gradle (`build.gradle`)

Spring Boot를 사용하는 경우 플러그인 버전만 올린다.

```gradle
plugins {
    id 'org.springframework.boot' version '4.0.0'
    id 'io.spring.dependency-management' version '1.1.0'
}
```

Spring Boot 없이 단독 사용 시 BOM을 직접 적용한다.

```gradle
dependencies {
    implementation platform('org.springframework.security:spring-security-bom:7.0.4')
    implementation 'org.springframework.security:spring-security-web'
    implementation 'org.springframework.security:spring-security-config'
}
```

---

## 다음 단계

의존성 업데이트 후 각 영역별 마이그레이션을 진행한다:

1. **Jackson 3 마이그레이션** → `02-jackson3-migration.md` (모든 프로젝트 해당)
2. **Authorization 변경** → `03-authorization-migration.md` (레거시 Access API 사용 시)
3. **OAuth2/JWT 변경** → `04-oauth2-jwt-migration.md` (OAuth2 Resource Server 사용 시)
4. **SAML2 변경** → `05-saml2-migration.md` (SAML2 인증 사용 시)
5. **Reactive 변경** → `06-reactive-migration.md` (Spring WebFlux 사용 시)
