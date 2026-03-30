# 모듈화 아키텍처 변경

Spring Boot 4.0은 모놀리식 jar 구조에서 **focused 모듈 구조**로 재편됐다.
이전에 자동으로 포함되던 기술들을 이제 명시적으로 선언해야 한다.

---

## 명명 규칙

| 유형 | 규칙 | 예시 |
|---|---|---|
| 모듈 | `spring-boot-<technology>` | `spring-boot-flyway` |
| 패키지 | `org.springframework.boot.<technology>` | `org.springframework.boot.flyway` |
| 스타터 | `spring-boot-starter-<technology>` | `spring-boot-starter-flyway` |
| 테스트 모듈 | `spring-boot-<technology>-test` | `spring-boot-flyway-test` |

---

## 명시적 스타터 추가 필요

이전에 `spring-boot-starter-data-jpa` 등에 번들로 포함됐던 기술들이 분리됐다.

### DB 마이그레이션 도구

```xml
<!-- Flyway -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>

<!-- Liquibase -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-liquibase</artifactId>
</dependency>
```

### 기타 분리된 스타터들

프로젝트 빌드 시 `ClassNotFoundException`이나 `NoSuchBeanDefinitionException`이 발생하면,
해당 기술의 스타터가 명시적으로 추가됐는지 확인하라.

---

## 점진적 마이그레이션: Classic Starters

한 번에 모든 것을 바꾸기 어려울 때 **classic starters**를 사용하면
기존 3.x classpath와 유사한 환경에서 시작할 수 있다.

```xml
<!-- 기존 3.x와 유사한 classpath 제공 (단계적 마이그레이션용) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-classic</artifactId>
</dependency>

<!-- 테스트용 classic starter -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test-classic</artifactId>
  <scope>test</scope>
</dependency>
```

> **주의**: Classic starters는 마이그레이션 편의를 위한 임시 수단이다.
> 장기적으로는 필요한 스타터를 명시적으로 선언하는 구조로 전환해야 한다.

### 권장 마이그레이션 흐름

```
1단계: classic starter 추가 → Boot 4로 빌드 성공시키기
2단계: classic starter 없이도 동작하도록 개별 스타터 하나씩 추가
3단계: classic starter 제거
```

---

## Properties Migrator

설정 프로퍼티 이름이 대규모로 변경됐다. 마이그레이션 기간 동안 이 도구가
자동으로 deprecated 프로퍼티를 감지하고 임시 매핑을 제공한다.

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-properties-migrator</artifactId>
  <scope>runtime</scope>
</dependency>
```

실행 시 로그에서 변경 필요한 프로퍼티 목록을 확인할 수 있다:

```
WARN  - Property 'spring.data.mongodb.host' has been renamed to 'spring.mongodb.host'
```

> **반드시 마이그레이션 완료 후 제거**하라. 프로덕션 배포 시에는 포함하지 않는다.

---

## 패키지 구조 변경 확인 방법

빌드 후 컴파일 오류 또는 런타임 오류가 발생하면:

1. 오류 메시지의 클래스명으로 새 패키지 위치 검색
2. Spring Boot 4.0 Javadoc 또는 GitHub 소스에서 확인
3. `spring-boot-properties-migrator` 로그 확인 (설정 프로퍼티의 경우)

각 카테고리별 패키지 변경 상세:
- 핵심 패키지 이동 → `09-core-changes.md`
- 테스트 관련 패키지 → `06-testing-annotations.md`
- 데이터 레이어 → `07-data-layer.md`
