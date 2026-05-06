# Decisions

## JWT 토큰 전달 방식

결정:
- Access Token 은 JSON response body 로 전달한다.
- Refresh Token 은 HttpOnly, Secure, SameSite=Lax cookie 로 전달한다.

근거:
- AT 는 클라이언트 Authorization header 에 사용한다.
- RT 는 JavaScript 접근을 막고 쿠키 기반으로 보호한다.

## Refresh Token 저장소

결정:
- Step 4-A 부터 Refresh Token 저장소는 Redis 를 사용한다.
- Redis key 는 `RT:{username}` 을 사용한다.
- Access Token blacklist key 는 `BL:{accessToken}` 을 사용한다.

근거:
- JWT subject 가 username 이므로 별도 userId 조회 없이 처리 가능하다.
- 로그아웃 시 남은 AT 만료시간만큼 blacklist TTL 을 줄 수 있다.

후속 상태:
- JPA `RefreshToken` 엔티티와 `RefreshTokenRepository` 는 제거 완료.

## Refresh Token Rotation

결정:
- `/refresh` 는 저장된 RT 와 요청 RT 가 일치할 때만 새 AT/RT 를 발급한다.
- 저장 RT 와 요청 RT 가 다르면 탈취 또는 재사용 시도로 보고 실패 처리한다.

근거:
- Step 2 RTR 규칙 유지.

## OAuth2 구조

결정:
- OAuth2 로그인 성공 후에도 서버 자체 JWT AT/RT 를 발급한다.
- OAuth2 성공 핸들러도 일반 로그인과 동일하게 Redis 에 RT 를 저장한다.
- provider 별 attribute 차이는 `OAuth2UserInfo` 계층으로 분리한다.

근거:
- 일반 로그인과 OAuth2 로그인 간 RT 저장 전략을 통일한다.

## OAuth2 State 저장

결정:
- OAuth2 authorization request/state 는 session 이 아니라 cookie repository 로 저장한다.

근거:
- `SessionCreationPolicy.STATELESS` 와 맞지 않는 기본 session 저장소를 대체한다.

## OAuth2 Access Token Redirect

현재 결정:
- OAuth2 success handler 는 `{redirect-uri}#accessToken={AT}` fragment redirect 를 사용한다.

향후 개선:
- fragment 방식 대신 일회용 코드 교환 방식으로 개선 여지가 있다.

## 회원가입 Role

결정:
- 클라이언트가 role 을 지정하지 못하게 한다.
- 회원가입 시 서버가 항상 `ROLE_USER` 를 부여한다.

## 테스트 전략

결정:
- Service 는 Mockito 기반 순수 단위 테스트를 우선한다.
- Controller 는 `@WebMvcTest` + `@MockitoBean` 기반 slice 테스트를 사용한다.
- JWT 필터 검증은 controller slice 와 분리된 security 테스트에서 수행한다.
- Redis 저장소는 Testcontainers 기반 검증을 사용한다.

Step 4-A 후속 메모:
- `JwtAuthenticationFilter` 생성자에 `TokenRedisRepository` 가 추가되었으므로 테스트 전용 security config 에도 mock wiring 이 필요하다.

## Session Log

결정:
- 상세 session log 는 남기지 않는다.
- 완료 기록은 source code, git history, `docs/`, `.memory/` 에 반영한다.
