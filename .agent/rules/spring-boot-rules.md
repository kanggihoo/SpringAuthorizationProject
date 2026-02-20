---
trigger: manual
---

1. 기술 스택 상세 (build.gradle 기반)
   Framework: Spring Boot 4.0.2, Spring Security
   Java Version: Java 21
   Data: Spring Data JPA (PostgreSQL), Redis
   Library: Lombok (적극 활용), OAuth2 Client
   Build 도구 및 의존성 관리 : Gradle

2. 계층별 구현 규칙
   Controller: API 엔드포인트 전담,DTO로 요청 수신 및 응답 반환.
   Service: 비즈니스 로직 및 트랜잭션 관리, 인터페이스(Interface) 기반 설계 지향.
   Repository: 표준 JPA 활용, 복잡한 동적 쿼리는 Querydsl 스타일 고려.
   DTO 및 Entity 분리:

- Entity와 DTO는 반드시 분리하여 설계한다.
  Exception: 커스텀 예외(Business Exception)를 정의하고, GlobalExceptionHandler에서 통합 처리한다.

3. 코딩 스타일 및 제약 사항
   의존성 주입: 생성자 주입(Constructor Injection)만 사용한다. (@Autowired 사용 절대 금지)
   객체 지향 설계: Entity에 의미 없는 @Setter 사용을 금지하고, 객체 내부에서 상태를 변경하는 비즈니스 메서드를 정의한다.
