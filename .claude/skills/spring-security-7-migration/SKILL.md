---
name: spring-security-7-migration
description: >
  Spring Security 6.x → 7.0 마이그레이션 전문 가이드 (Java 전용).
  Spring Security를 7로 업그레이드하거나 기존 프로젝트에서 7 기반으로 전환할 때 반드시 사용하라.
  Maven/Gradle 의존성 변경, Jackson 3 마이그레이션, Authorization API 변경,
  OAuth2/JWT 설정 변경, SAML2 변경, Reactive 앱 마이그레이션 등 모든 Breaking Change를
  카테고리별로 안내한다. "Spring Security 업그레이드", "스프링 시큐리티 7", "security migration",
  "JWT 설정 변경", "OAuth2 마이그레이션" 등의 맥락에서 이 스킬을 사용하라.
---

# Spring Security 7 Migration Guide (Java)

> **공식 문서**: https://docs.spring.io/spring-security/reference/migration/index.html
> **적용 대상**: Spring Security 6.x → 7.0, Java (Kotlin 제외)

---

## 마이그레이션 전략 요약

Spring Security 7로 직접 업그레이드하기 전에 **Spring Security 6.5를 거치는 것을 권장**한다.
6.5에는 7.0의 변경사항을 미리 opt-in 방식으로 적용해 볼 수 있는 준비 단계가 포함되어 있다.

```
현재 버전  →  Spring Security 6.5 (준비 단계)  →  Spring Security 7.0
```

---

## 전체 마이그레이션 체크리스트

아래 순서대로 진행하고, 각 항목에 해당하는 reference 파일을 참조하라.

### Step 1: 사전 조건 확인 및 의존성 업데이트
→ [`references/01-overview-prerequisites.md`](references/01-overview-prerequisites.md) 참조

- [ ] Spring Boot 4.0 최신 패치 버전으로 업그레이드
- [ ] Spring Security 7 최신 패치 버전으로 업그레이드
- [ ] Maven 또는 Gradle 의존성 설정 확인

### Step 2: Jackson 3 마이그레이션 (필수 Breaking Change)
→ [`references/02-jackson3-migration.md`](references/02-jackson3-migration.md) 참조

- [ ] `SecurityJackson2Modules` → `SecurityJacksonModules` 교체
- [ ] `ObjectMapper` → `JsonMapper` 전환
- [ ] OAuth2 Authorization Server Jackson 3 호환성 확인

### Step 3: Authorization API 변경 대응
→ [`references/03-authorization-migration.md`](references/03-authorization-migration.md) 참조

- [ ] `AccessDecisionManager` / `AccessDecisionVoter` 사용 여부 확인
- [ ] 레거시 Access API 사용 시 `spring-security-access` 모듈 추가

### Step 4: OAuth2 / JWT 설정 변경 (OAuth2 사용 시)
→ [`references/04-oauth2-jwt-migration.md`](references/04-oauth2-jwt-migration.md) 참조

- [ ] `validateTypes(false)` 제거
- [ ] `JwtValidators.createDefaultWithValidators()` → `createDefaultWithIssuer()` 교체
- [ ] `BearerTokenAuthenticationFilter` 직접 설정 시 `BearerTokenAuthenticationConverter`로 이전

### Step 5: SAML2 변경 대응 (SAML2 사용 시)
→ [`references/05-saml2-migration.md`](references/05-saml2-migration.md) 참조

- [ ] LogoutRequest 실패 시 응답 방식 변경 확인
- [ ] `Saml2AuthenticatedPrincipal` → `Saml2ResponseAssertionAccessor` 전환
- [ ] GET 요청 SAML Response 처리 설정 검토

### Step 6: Reactive 애플리케이션 변경 대응 (WebFlux 사용 시)
→ [`references/06-reactive-migration.md`](references/06-reactive-migration.md) 참조

- [ ] `NimbusReactiveJwtDecoder` 설정에서 `validateTypes(false)` 제거
- [ ] `JwtValidators.createDefaultWithIssuer()` 적용

---

## Reference 파일 인덱스

| 파일 | 내용 | 읽어야 할 때 |
|------|------|-------------|
| [01-overview-prerequisites.md](references/01-overview-prerequisites.md) | 사전 조건, Spring Boot 버전 요구사항, Maven/Gradle 의존성 설정 | 마이그레이션 시작 시 항상 먼저 읽기 |
| [02-jackson3-migration.md](references/02-jackson3-migration.md) | Jackson 2 → 3 마이그레이션, Maven/Gradle 설정 | Jackson 관련 오류 또는 `SecurityJackson2Modules` 사용 중일 때 |
| [03-authorization-migration.md](references/03-authorization-migration.md) | Authorization API 변경, `spring-security-access` 모듈 | `AccessDecisionManager`, `AccessDecisionVoter` 사용 중일 때 |
| [04-oauth2-jwt-migration.md](references/04-oauth2-jwt-migration.md) | JWT type 검증, BearerToken 설정 변경 | OAuth2 Resource Server, JWT 디코더 사용 중일 때 |
| [05-saml2-migration.md](references/05-saml2-migration.md) | SAML2 Logout, Principal 변경, GET 요청 처리 | SAML2 인증 사용 중일 때 |
| [06-reactive-migration.md](references/06-reactive-migration.md) | Reactive JWT 디코더 설정 변경 | Spring WebFlux + JWT 사용 중일 때 |
