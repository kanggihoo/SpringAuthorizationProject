# 단계별 마이그레이션 체크리스트

Spring Boot 3.x → 4.0 전체 업그레이드 순서. 위에서 아래로 순서대로 진행하라.

---

## Phase 1 — 사전 준비 (Spring Boot 3.x 상태에서)

### 1.1 최신 3.5.x로 업그레이드
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.x</version>  <!-- 최신 3.5.x -->
</parent>
```
- [ ] 빌드 성공 확인
- [ ] 모든 테스트 통과 확인

### 1.2 deprecated 경고 해소
- [ ] IDE의 deprecated 경고를 모두 검토하고 대체 API로 전환
- [ ] `@MockBean` → `@MockitoBean` (미리 전환 가능)
- [ ] `@SpyBean` → `@MockitoSpyBean` (미리 전환 가능)

### 1.3 Java / 런타임 환경 확인
- [ ] Java 17 이상 설치 및 빌드 툴 설정 확인 (`01-system-requirements.md`)
- [ ] Kotlin 사용 시 2.2 이상 확인
- [ ] GraalVM native-image 사용 시 버전 25 이상 확인

### 1.4 제거된 기능 사용 여부 확인
- [ ] Undertow 사용 여부 → Tomcat/Jetty로 전환 계획 수립 (`02-removed-features.md`)
- [ ] Pulsar Reactive 사용 여부
- [ ] Spock 테스트 사용 여부 → JUnit 5 전환 계획 수립
- [ ] Embedded launch scripts (`<executable>true</executable>`) 사용 여부

---

## Phase 2 — Spring Boot 4.0으로 버전 업

### 2.1 버전 변경
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.0.0</version>
</parent>
```

### 2.2 Properties Migrator 추가 (임시)
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-properties-migrator</artifactId>
  <scope>runtime</scope>
</dependency>
```

### 2.3 Classic Starters 추가 (선택 — 점진적 마이그레이션)
빌드가 너무 많이 깨진다면 임시로 추가:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-classic</artifactId>
</dependency>
```

---

## Phase 3 — 컴파일 오류 해소

빌드를 실행하고 컴파일 오류를 카테고리별로 수정한다.

### 3.1 Jackson 관련 (`04-jackson3-upgrade.md`)
- [ ] `com.fasterxml.jackson` → `tools.jackson` import 변경
- [ ] `@JsonComponent` → `@JacksonComponent`
- [ ] `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`

### 3.2 테스트 어노테이션 (`06-testing-annotations.md`)
- [ ] `@MockBean` → `@MockitoBean`
- [ ] `@SpyBean` → `@MockitoSpyBean`
- [ ] `@PropertyMapping` import 경로 변경
- [ ] `MockitoTestExecutionListener` 제거

### 3.3 웹 테스트 및 웹 기능 (`05-web-testing.md`)
- [ ] MockMvc 사용 테스트에 `@AutoConfigureMockMvc` 추가
- [ ] TestRestTemplate import 경로 변경
- [ ] TestRestTemplate 테스트에 `@AutoConfigureTestRestTemplate` 추가
- [ ] `spring.session.redis.*` → `spring.session.data.redis.*` 프로퍼티 변경
- [ ] `spring.session.mongodb.*` → `spring.session.data.mongodb.*` 프로퍼티 변경

### 3.4 핵심 패키지 이동 및 Actuator (`09-core-changes.md`)
- [ ] `BootstrapRegistry` import 경로 변경
- [ ] `EnvironmentPostProcessor` import 경로 변경
- [ ] Custom Actuator endpoint에서 `org.springframework.lang.Nullable` → `org.jspecify.annotations.Nullable` 전환
- [ ] Liveness/Readiness Probe 기본 활성화 영향 확인 (불필요 시 `management.endpoint.health.probes.enabled=false`)

### 3.5 메시징 (`08-messaging.md`)
- [ ] `StreamBuilderFactoryBeanCustomizer` → `StreamsBuilderFactoryBeanConfigurer`
- [ ] Kafka/AMQP retry import 경로 변경

### 3.6 데이터 레이어 (`07-data-layer.md`)
- [ ] Elasticsearch `RestClientBuilderCustomizer` → `Rest5ClientBuilderCustomizer`

---

## Phase 4 — 설정 프로퍼티 마이그레이션

### 4.1 Properties Migrator 로그 확인
애플리케이션을 시작하고 로그에서 deprecated 프로퍼티 경고 확인:
```
WARN - Property 'spring.data.mongodb.host' has been renamed to 'spring.mongodb.host'
```

### 4.2 주요 프로퍼티 변경 적용
- [ ] `spring.data.mongodb.*` → `spring.mongodb.*` (`07-data-layer.md`)
- [ ] `spring.jackson.read.*` → `spring.jackson.json.read.*` (`04-jackson3-upgrade.md`)
- [ ] DevTools LiveReload: `spring.devtools.livereload.enabled=true` (필요 시)

---

## Phase 5 — 런타임 검증

### 5.1 테스트 실행
- [ ] 단위 테스트 전체 통과
- [ ] 통합 테스트 전체 통과
- [ ] Spring Batch 사용 시 `spring-boot-starter-batch-jdbc` 추가 필요 여부 확인

### 5.2 Spring Batch 검토
- [ ] In-memory 배치 기본값으로 운영 가능한지 검토
- [ ] DB 기반 배치 필요 시 `spring-boot-starter-batch-jdbc` 추가 (`07-data-layer.md`)

### 5.3 MongoDB UUID/BigDecimal 데이터 호환성
- [ ] 기존 MongoDB 데이터의 UUID 표현 방식 확인
- [ ] `spring.mongodb.uuid-representation` 명시적 설정 (`07-data-layer.md`)

### 5.4 Jersey 사용 시 Jackson 호환성
- [ ] Jersey 사용 여부 확인 → `spring-boot-jackson2` 의존성 추가 (`05-web-testing.md`)

### 5.5 Tomcat WAR 배포 여부 확인
- [ ] WAR 배포 시 `spring-boot-starter-tomcat` → `spring-boot-starter-tomcat-runtime` 변경 (`05-web-testing.md`)

---

## Phase 6 — 정리

### 6.1 임시 의존성 제거
```xml
<!-- 반드시 제거 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-properties-migrator</artifactId>
</dependency>

<!-- 점진적 마이그레이션 완료 후 제거 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-classic</artifactId>
</dependency>
```

### 6.2 빌드 플러그인 검토 (`10-build-plugins.md`)
- [ ] Optional 의존성 uber jar 포함 여부 확인
- [ ] `spring-boot-starter-aop` → `spring-boot-starter-aspectj` 이름 변경
- [ ] CycloneDX Gradle Plugin 3.0.0 이상 확인

### 6.3 최종 검증
- [ ] 전체 테스트 재실행
- [ ] 스테이징 환경 배포 및 동작 확인

---

## 빠른 참조: 카테고리별 파일

| 파일 | 주요 내용 |
|---|---|
| `01-system-requirements.md` | Java 17+, Kotlin 2.2+, GraalVM 25+ |
| `02-removed-features.md` | Undertow, Pulsar Reactive, Spock, Launch Scripts |
| `03-modularization.md` | 명시적 스타터, classic starters, Properties Migrator |
| `04-jackson3-upgrade.md` | Group ID 변경, 클래스명 변경, 설정 프로퍼티 |
| `05-web-testing.md` | MockMvc, TestRestTemplate, Spring Session 프로퍼티, Jersey/Jackson2, Tomcat WAR 배포 |
| `06-testing-annotations.md` | @MockBean→@MockitoBean, @SpyBean→@MockitoSpyBean |
| `07-data-layer.md` | Elasticsearch Rest5Client, MongoDB 프로퍼티, Spring Batch |
| `08-messaging.md` | Kafka Streams, retry 패키지 이동 |
| `09-core-changes.md` | JSpecify, Logback charset, 패키지 이동, PropertyMapper, Actuator Probe |
| `10-build-plugins.md` | Maven optional, AOP 스타터 이름, CycloneDX |
