# 프로젝트 컨텍스트 (Project Context)

> 이 파일은 프로젝트의 고정된 기술 스펙과 설계 원칙을 담습니다.
> 기술 스택이나 아키텍처 원칙이 변경될 때만 수정합니다.

## 프로젝트 개요

Spring Boot 기반 인증/인가 학습 프로젝트.
세션 → JWT → OAuth2 순서로 인증 방식을 단계적으로 발전시키며
Spring Security의 내부 동작을 이해하는 것이 핵심 목표.

## 기술 스택

| 분류       | 기술                           | 비고                           |
| ---------- | ------------------------------ | ------------------------------ |
| Language   | Java 21                        |                                |
| Framework  | Spring Boot 4.0.2              | Spring MVC (WebMVC)            |
| Security   | Spring Security, OAuth2 Client |                                |
| Data       | Spring Data JPA, PostgreSQL    | Redis는 현재 주석 처리         |
| JWT        | jjwt 0.13.0                    | api / impl / jackson           |
| Validation | Spring Boot Validation         |                                |
| Docs       | springdoc-openapi 3.0.1        | Swagger UI                     |
| Utility    | Lombok                         |                                |
| Build      | Gradle                         | Checkstyle (Google Style) 적용 |
| Test       | JUnit 5, Spring Security Test  |                                |

## 디렉토리 구조

```
src/main/java/org/example/
├── config/          # 설정 클래스
├── controller/      # API 엔드포인트
├── domain/entity/   # JPA 엔티티
├── dto/             # 요청/응답 DTO
├── exception/       # 예외 처리
├── repository/      # JPA Repository
├── security/        # Spring Security 관련
└── service/         # 비즈니스 로직
```

## 코딩 컨벤션

- **의존성 주입**: 생성자 주입만 사용 (`@Autowired` 절대 금지)
- **Entity/DTO 분리**: 반드시 분리. Entity를 직접 응답으로 사용 금지
- **Entity Setter 금지**: 의미 없는 `@Setter` 사용 금지, 비즈니스 메서드로 상태 변경
- **Service 설계**: Interface 기반 설계, 트랜잭션 관리 책임
- **Exception**: 커스텀 예외 정의, `GlobalExceptionHandler`에서 통합 처리
- **Checkstyle**: Google Style 기준 적용 (`config/checkstyle/`)

## 관련 문서 경로

| 문서               | 경로                                                 |
| ------------------ | ---------------------------------------------------- |
| 1단계 설계안       | `docs/archive/completed-plans/step1.md`              |
| 1단계 내부 흐름    | `docs/archive/internal-flows/step1-internal-flow.md` |
| 2단계 설계안       | `docs/archive/completed-plans/step2.md`              |
| 2단계 내부 흐름    | `docs/archive/internal-flows/step2-internal-flow.md` |
| API 명세           | `docs/specs/api/`                                    |
| 에이전트 스킬 목록 | `docs/available_skills.md`                           |
