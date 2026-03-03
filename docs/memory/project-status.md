# 프로젝트 진행 상태 (Project Status)

> 이 파일은 현재 개발 진행 상황을 추적합니다.
> 각 단계가 완료되거나 새 작업이 시작될 때 업데이트합니다.
> 최종 업데이트: 2026-03-02

## 전체 로드맵

| 단계   | 주제                                        | 상태       |
| ------ | ------------------------------------------- | ---------- |
| Step 1 | 세션 기반 Form Login (JPA + Security 기초)  | ✅ 완료    |
| Step 2 | JWT 무상태 인증 (Access/Refresh Token, RTR) | 🔄 진행 중 |
| Step 3 | OAuth2 소셜 로그인 연동                     | ⏳ 예정    |
| Step 4 | Redis 도입 (Token 블랙리스트 / 세션 캐싱)   | ⏳ 예정    |

## 현재 단계: Step 2

### 완료된 작업

- [x] JWT 무상태 정책으로 전환 (SessionCreationPolicy.STATELESS)
- [x] JwtTokenProvider 구현 (HS256, Claims 구성, 예외 세분화)
- [x] JwtAuthenticationFilter 구현 (shouldNotFilter 포함)
- [x] ExceptionHandlerFilter 구현 (필터 예외 → MVC 위임)
- [x] AuthController 직접 구현 (/login, /logout, /refresh, /signup)
- [x] CustomAuthenticationEntryPoint (401 JSON 응답)
- [x] CustomAccessDeniedHandler (403 JSON 응답)
- [x] RTR 적용 (Refresh Token Rotation)
- [x] DTO 계층 리팩토링

### 진행 중 / 미완료

- [x] API 문서 연동 (springdoc-openapi) 완료
- [ ] PostgreSQL 클라우드 환경 설정 완료 후 RefreshToken DB 연동 검증
- [ ] Refresh Token 관련 통합 테스트

### 알려진 이슈

- Redis 의존성 현재 주석 처리됨 (`build.gradle` 참고)
- PostgreSQL 클라우드 환경 미설정으로 RefreshToken 관련 기능 로컬 완전 검증 필요

## 브랜치 전략

- `main`: 완료된 단계만 병합
- `step2`: 현재 작업 브랜치
