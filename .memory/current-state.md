# Current State

프로젝트의 현재 위치와 최신 아키텍처 상태를 나타냅니다. 세션 시작 시 가장 먼저 확인하는 문서입니다.

## 📍 현재 프로젝트 위치
- **진행 단계**: **Step 4-A (Redis 마이그레이션 + AT Blacklist)** 의 중간 지점.

## ⚙️ 주요 기술 스택 및 모듈 버전
- **Language**: Java 21
- **Framework**: Spring Boot 4.0.2
- **Security**: Spring Security 7
- **Database**: PostgreSQL 17-alpine (Docker) / Spring Data JPA
- **Cache**: Redis (Docker) / Spring Data Redis
- **JWT**: jjwt 0.13.0
- **Docs**: springdoc-openapi 3.0.1

## 📂 주요 디렉토리 구조
```text
src/main/java/org/example/
├── config/          # 설정 클래스 (Security, Redis, Async 등)
├── controller/      # API 엔드포인트
├── domain/entity/   # JPA 엔티티 및 도메인 로직
├── domain/event/    # 비동기 보안 이벤트 레코드
├── dto/             # 요청/응답 DTO (Record 적극 활용)
├── exception/       # 커스텀 예외 및 GlobalExceptionHandler
├── repository/      # JPA 및 Redis Repository
├── security/        # JWT, OAuth2, Filter 등 Spring Security 커스텀 로직
└── service/         # 비즈니스 로직
```

## 📜 스텝별 완료된 마일스톤 히스토리
과거부터 현재까지의 진행 흐름과 주요 구현 사항입니다.

- **Step 1: 세션 기반 기본 인증**
  - Spring Security 기본 Form Login 설정.
  - 회원가입 로직 및 비밀번호 암호화(BCrypt) 적용.
- **Step 2: JWT 무상태 인증 도입**
  - 세션 완전히 제거 (`SessionCreationPolicy.STATELESS`).
  - Access Token(Body 반환) / Refresh Token(Cookie 반환) 분리 설계.
  - Refresh Token Rotation (RTR) 패턴 적용.
  - JWT 발급 및 검증 필터(`JwtAuthenticationFilter`) 구축.
- **Step 3: Google OAuth2 연동 및 Docker 인프라**
  - `OAuth2UserInfo` 추상화 팩토리 패턴 적용(OCP 준수).
  - CSRF 방어용 상태 검증 쿠키 저장소(`CookieOAuth2AuthorizationRequestRepository`) 구현.
  - 성공/실패 핸들러 커스텀 구현 및 자체 JWT 발급 연동.
  - Docker Compose를 통한 PostgreSQL 및 Redis 인프라 구성.
- **Step 4-A: Redis 마이그레이션 (진행 중)**
  - TDD 사이클 1~5 완료. (가입 시 Role 권한 부여 취약점 수정, TokenRedisRepository 구현, JwtTokenProvider TTL 계산 로직, AuthService/Controller 로직 Redis 전환)

## 🚨 직면한 핵심 과제 (Context)
- `AuthService`에서 로그아웃 시 Redis Blacklist(`BL:{accessToken}`)에 AT를 잘 등록하고 있으나, **실제 API 요청이 들어왔을 때 `JwtAuthenticationFilter`에서 이를 검증하여 차단하는 로직이 아직 없습니다.**
- OAuth2 로그인 성공 핸들러(`OAuth2AuthenticationSuccessHandler`)는 여전히 이전의 JPA `RefreshTokenRepository`를 의존하고 있어 통합이 안 된 상태입니다.

## ➡️ 즉시 재개할 진입점
- `tasks.md`의 **[NOW]** 섹션을 확인하여 `JwtAuthenticationFilter` Blacklist 체크 로직 TDD 구현을 바로 시작합니다.
