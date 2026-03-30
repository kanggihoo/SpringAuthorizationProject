# 빌드 플러그인 변경

Maven/Gradle 빌드 설정, 스타터 이름 변경, Spring Authorization Server 통합 등.

---

## Maven — Optional 의존성 uber jar 제외

### 변경사항
Optional로 선언된 의존성이 Spring Boot Maven Plugin의 uber jar에서 **기본적으로 제외**된다.
이전에는 optional 의존성도 uber jar에 포함됐다.

### Optional 의존성을 uber jar에 포함시키려면

```xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <configuration>
    <includeOptional>true</includeOptional>
  </configuration>
</plugin>
```

### 언제 영향받는가?

```xml
<!-- 이런 optional 의존성이 있을 때 -->
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-library</artifactId>
  <optional>true</optional>
</dependency>
```

이 경우 uber jar를 만들면 `some-library`가 포함되지 않는다.
런타임에 `ClassNotFoundException`이 발생한다면 이 변경사항을 확인하라.

---

## AOP 스타터 이름 변경

### 변경사항

```xml
<!-- 이전 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- 이후 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
```

### 주의사항

`spring-boot-starter-aop`는 Spring AOP(프록시 기반)와 AspectJ를 함께 포함했다.
이름 변경과 함께 **AspectJ가 실제로 필요한지 재검토**하라.

#### Spring AOP만 필요한 경우 (대부분의 경우)
```xml
<!-- AspectJ 없이 Spring AOP 사용 — 별도 의존성 불필요 -->
<!-- spring-context가 포함된 어떤 스타터든 Spring AOP를 포함함 -->
```

#### AspectJ 컴파일 타임 위빙이 필요한 경우
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
```

> 대부분의 `@Transactional`, `@Cacheable`, `@Async` 사용은 Spring AOP 프록시로 충분하다.
> AspectJ 컴파일 타임 위빙은 self-invocation 처리나 final 클래스 위빙이 필요할 때만 사용한다.

---

## Spring Authorization Server — Spring Security 통합

### 변경사항
Spring Authorization Server가 Spring Security 프로젝트로 통합됐다.

### 버전 오버라이드

```xml
<!-- pom.xml -->
<properties>
  <!-- Spring Security 전체 버전 오버라이드 (Authorization Server 포함) -->
  <spring-security.version>7.x.x</spring-security.version>
</properties>
```

```groovy
// build.gradle
ext['spring-security.version'] = '7.x.x'
```

### 의존성 (변경 없음)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>
```

---

## CycloneDX Gradle Plugin — 최소 버전 3.0.0

SBOM(Software Bill of Materials) 생성을 위해 CycloneDX Gradle Plugin을 사용하는 경우:

```groovy
// build.gradle — 3.0.0 미만이면 업그레이드 필요
plugins {
    id 'org.cyclonedx.bom' version '3.0.0'  // 최소 3.0.0
}
```

---

## Gradle — 버전 요구사항

Spring Boot 4.0 Gradle Plugin은 **Gradle 8.x** 이상을 요구한다.

```bash
# Gradle 버전 확인
./gradlew --version
```

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.x.x-bin.zip
```

---

## 빌드 플러그인 체크리스트

- [ ] Optional 의존성 uber jar 포함 여부 검토 (`includeOptional` 설정)
- [ ] `spring-boot-starter-aop` → `spring-boot-starter-aspectj` (AspectJ 실제 필요 여부 재검토)
- [ ] Spring Authorization Server 버전 오버라이드 프로퍼티 키 확인
- [ ] CycloneDX Gradle Plugin 3.0.0 이상으로 업그레이드
- [ ] Gradle 8.x 이상인지 확인 (`gradle-wrapper.properties`)
