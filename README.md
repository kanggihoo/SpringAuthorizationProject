# Spring Security Auth Security Lab

Spring Boot 4와 Spring Security 기반으로 JWT/OAuth2 인증 시스템을 구현하고,
인증 보안 실패 시나리오를 테스트로 검증하는 프로젝트입니다.

이 프로젝트의 목표는 "로그인 기능 구현"이 아니라 다음 질문에 답하는 것입니다.

- 로그아웃된 Access Token은 즉시 차단되는가?
- Refresh Token이 탈취되어 재사용되면 탐지할 수 있는가?
- 계정 잠금, 권한 검증, OAuth2 토큰 전달 방식은 안전하게 동작하는가?
- Redis가 인증 상태 저장소가 되었을 때 장애 정책은 명확한가?
- 핵심 인증/인가 로직을 테스트와 커버리지로 증명할 수 있는가?

## Tech Stack

| Area | Stack |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4, Spring MVC |
| Security | Spring Security, OAuth2 Client, JWT |
| Token Store | Redis |
| Database | PostgreSQL, Spring Data JPA |
| Test | JUnit, Spring Security Test, Testcontainers, AssertJ |
| Quality | Checkstyle, JaCoCo |

## Project Direction

인증 프로젝트는 DB 성능 프로젝트처럼 p95 응답시간 개선을 핵심 증거로 삼기 어렵습니다.
대신 이 프로젝트는 보안 불변식을 테스트로 검증합니다.

```text
보안 실패 시나리오
  -> 방어 정책
  -> 자동화 테스트
  -> 커버리지와 evidence 기록
```

커버리지는 Phase별 과거 코드 스냅샷으로 측정하지 않습니다.
현재 코드베이스 전체를 기준으로 JaCoCo line coverage 80% 이상을 목표로 합니다.

## Phase Roadmap

Phase는 포트폴리오와 evidence 문서에서 사용하는 분류입니다.
기존 `docs/plans/step*.md` 문서는 구현 이력으로 유지합니다.

### Core Roadmap

| Phase | Name | Goal |
| --- | --- | --- |
| Phase 1 | JWT Dual Token 인증 기반 | Access Token과 Refresh Token을 분리하고 보호 API 접근을 검증한다. |
| Phase 2 | Redis Token Store + Logout Blacklist | Refresh Token을 Redis에 저장하고 로그아웃된 Access Token을 차단한다. |
| Phase 3 | Refresh Token Rotation & Reuse Detection | 이전 Refresh Token 재사용을 탈취 의심으로 탐지하고 차단한다. |
| Phase 4 | Account Lock & Admin Recovery | 로그인 실패 누적으로 계정을 잠그고 관리자가 해제할 수 있게 한다. |
| Phase 5 | Security Audit Event | 로그인 실패, 계정 잠금, 토큰 재사용 등 보안 이벤트를 추적한다. |
| Phase 6 | OAuth2 자체 JWT 발급 | OAuth2 로그인 성공 후 서비스 자체 Access/Refresh Token을 발급한다. |
| Phase 7 | OAuth2 One-time Code Exchange | OAuth2 성공 후 Access Token을 URL에 직접 노출하지 않고 code 교환 방식으로 처리한다. |
| Phase 8 | RBAC & Method Security | URL 권한과 서비스 메서드 권한을 함께 검증한다. |
| Phase 9 | Redis Failure Policy | Redis 장애 시 인증 기능별 fail-open/fail-closed 정책을 정한다. |
| Phase 10 | Test Coverage 80% & Evidence | 현재 코드베이스 기준 JaCoCo 80%와 보안 시나리오 매트릭스를 관리한다. |

### Extension Roadmap

Core Roadmap을 완료한 뒤 인증 플랫폼 확장 주제로 다룹니다.

| Phase | Name | Goal |
| --- | --- | --- |
| Phase 11 | Redis 기반 Login Session Policy | HttpSession 없이 Redis session state와 `sid` claim으로 세션 폐기를 제어한다. |
| Phase 12 | Multi Device Session Management | 기기별 Refresh Token Session을 관리하고 특정 기기/전체 로그아웃을 지원한다. |
| Phase 13 | SSO / OIDC Authorization Server Exploration | 현재 OAuth2 Client 구조와 SSO/OIDC Authorization Server 구조의 차이를 검토한다. |

## Security Scenarios

핵심 증거는 "된다"는 설명이 아니라 재현 가능한 테스트입니다.
각 시나리오는 테스트 클래스와 실행 결과로 추적합니다.

| Scenario | Expected |
| --- | --- |
| 로그인 성공 | Access Token 응답, Refresh Token 쿠키 발급 |
| Access Token 없이 보호 API 접근 | 401 Unauthorized |
| 변조된 Access Token 사용 | 401 Unauthorized |
| 로그아웃 후 기존 Access Token 사용 | 401 Unauthorized |
| 이전 Refresh Token 재사용 | 401 Unauthorized, 재사용 탐지 이벤트 |
| 잘못된 비밀번호 5회 입력 | 계정 잠금, 423 Locked |
| 잠긴 계정의 기존 Access Token 사용 | 401 Unauthorized |
| USER가 ADMIN API 접근 | 403 Forbidden |
| OAuth2 one-time code 재사용 | 400 또는 401 |
| Redis 장애 중 refresh 요청 | 정책에 따른 차단 또는 장애 응답 |

## Test And Evidence Policy

테스트와 evidence는 최소 문서 구조로 관리합니다.

```text
README.md          # 프로젝트 방향과 Phase Roadmap
docs/evidence.md   # 커버리지 목표, 테스트 실행 결과, 보안 시나리오 매트릭스
docs/plans/        # 구현 계획과 작업 이력
docs/archive/      # 완료된 과거 문서
```

원칙:

- Phase별 evidence 파일은 만들지 않습니다.
- Phase별 과거 브랜치로 돌아가 커버리지를 측정하지 않습니다.
- JaCoCo는 현재 코드베이스 전체 기준 80% 이상을 목표로 합니다.
- Phase별 증거는 `docs/evidence.md`의 보안 시나리오 매트릭스에서 테스트와 상태로 관리합니다.