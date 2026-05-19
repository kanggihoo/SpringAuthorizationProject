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
