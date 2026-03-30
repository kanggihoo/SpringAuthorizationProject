---
name: spring-boot-4-migration
description: >
  Spring Boot 4.0 마이그레이션 전문 가이드. Spring Boot 3.x에서 4.0으로 업그레이드하거나
  Spring Boot 4 기반으로 새 프로젝트를 시작할 때 반드시 사용하라.
  Java 17+/Jakarta EE 11 요구사항, 제거된 기능(Undertow 등), Jackson 3 업그레이드,
  @MockBean→@MockitoBean 변경, 패키지 이동, 데이터/메시지 레이어 변경,
  Actuator Probe 기본활성화, Jersey+Jackson2 호환성, Tomcat WAR 배포 변경,
  Spring Session 프로퍼티 리네이밍 등 Breaking Change 전반을 카테고리별 references로 안내한다.
  "Spring Boot 4", "4.0 업그레이드", "Boot 4 마이그레이션", "@MockBean deprecated",
  "Jackson 3", "Jakarta EE 11", "Undertow 제거", "Rest5Client", "spring.mongodb",
  "Liveness", "Readiness", "Probe", "Jersey Jackson", "Tomcat WAR", "spring.session" 언급 시
  반드시 이 스킬을 활용하라.
---

# Spring Boot 4.0 Migration Skill

> 원문: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
> 대상: Spring Boot 3.x → 4.0 업그레이드 또는 신규 4.0 프로젝트 시작

Spring Boot 4.0은 Jakarta EE 11, Java 17+, Spring Framework 7.0 기반의 메이저 릴리즈로
다수의 Breaking Change가 포함된다. 아래 인덱스에서 관련 카테고리를 찾아 references 파일을 읽어라.

---

## 빠른 진단 — 어떤 파일을 읽을지 결정하기

| 사용자 질문 / 증상 | 읽을 파일 |
|---|---|
| "어떤 버전이 필요한가요?" / Java, Kotlin, GraalVM 버전 | `01-system-requirements.md` |
| Undertow, Pulsar Reactive, Spock, 임베디드 launch script 관련 | `02-removed-features.md` |
| 스타터 의존성 누락, 모듈 구조, classic starters | `03-modularization.md` |
| Jackson, `@JsonComponent`, `ObjectMapper`, `JsonMapperBuilderCustomizer` | `04-jackson3-upgrade.md` |
| MockMvc, TestRestTemplate, `@AutoConfigureMockMvc`, Spring Session, Jersey, Tomcat WAR | `05-web-testing.md` |
| `@MockBean`, `@SpyBean`, `@PropertyMapping`, MockitoExtension | `06-testing-annotations.md` |
| Elasticsearch `RestClient`, MongoDB `spring.data.mongodb`, Spring Batch | `07-data-layer.md` |
| Kafka Streams, `StreamBuilderFactoryBeanCustomizer`, AMQP retry | `08-messaging.md` |
| JSpecify, Logback charset, `BootstrapRegistry`, `PropertyMapper`, DevTools, Actuator Probe, Liveness, Readiness | `09-core-changes.md` |
| Maven optional 의존성, AOP 스타터 이름, CycloneDX, Authorization Server | `10-build-plugins.md` |
| 전체 업그레이드 순서, `spring-boot-properties-migrator` | `11-migration-checklist.md` |

---

## References 목차

```
references/
├── 01-system-requirements.md     Java / Kotlin / GraalVM / Jakarta EE 버전 요구사항
├── 02-removed-features.md        완전히 제거된 기능 및 대체재
├── 03-modularization.md          모듈화 아키텍처 변경 및 classic starters 전략
├── 04-jackson3-upgrade.md        Jackson 3 업그레이드 (Group ID, 클래스명, 설정)
├── 05-web-testing.md             MockMvc / TestRestTemplate 변경사항
├── 06-testing-annotations.md     @MockBean → @MockitoBean 등 테스트 어노테이션
├── 07-data-layer.md              Elasticsearch / MongoDB / Spring Batch 변경
├── 08-messaging.md               Kafka Streams / AMQP retry 변경
├── 09-core-changes.md            JSpecify / Logback / 패키지 이동 / PropertyMapper
├── 10-build-plugins.md           Maven/Gradle 빌드 플러그인 변경
└── 11-migration-checklist.md     단계별 마이그레이션 체크리스트
```

---

## 사용 지침

1. **단일 주제 질문**: 위 진단표에서 파일 하나만 읽어 답하라.
2. **전체 업그레이드 계획 수립**: `11-migration-checklist.md` 먼저 읽고, 사용자 스택에 해당하는 파일들을 추가로 읽어라.
3. **코드 예시 요청**: 해당 카테고리 파일에 Before/After 코드 예시가 포함되어 있다.
4. **여러 카테고리 걸친 질문**: 관련 파일을 모두 읽어 종합 답변하라.
