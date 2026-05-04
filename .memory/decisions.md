# Decisions

## JWT 토큰 전달 방식

결정:
- Access Token은 JSON 응답 body로 전달한다.
- Refresh Token은 HttpOnly, Secure, SameSite=Lax cookie로 전달한다.

근거:
- `docs/archive/completed-plans/step2.md`
- AT는 클라이언트가 API Authorization header에 사용한다.
- RT는 JavaScript 접근을 막고 쿠키 기반으로 보호한다.

## Refresh Token 저장소

결정:
- Step 4-A부터 Refresh Token 저장소를 PostgreSQL JPA에서 Redis로 전환한다.
- Redis key는 `RT:{username}`을 사용한다.
- Access Token blacklist key는 `BL:{accessToken}`을 사용한다.

근거:
- `docs/plans/step4-A-handover.md`
- JWT subject가 username이므로 userId 조회 없이 Redis에서 처리한다.
- 로그아웃된 AT는 남은 만료 시간만큼 blacklist에 저장한다.

주의:
- 오래된 로드맵 일부에는 `RT:{userId}`가 나온다.
- Step 4-A handover의 최신 결정은 `RT:{username}`이다.

## Refresh Token Rotation

결정:
- `/refresh`는 기존 RT를 검증한 뒤 새 AT/RT 세트를 발급한다.
- 저장된 RT와 요청 RT가 다르면 탈취 또는 재사용 시도로 보고 실패 처리한다.

근거:
- `docs/archive/completed-plans/step2.md`

## OAuth2 구조

결정:
- OAuth2 로그인 성공 후에도 서버 자체 JWT AT/RT를 발급한다.
- provider별 attribute 차이는 `OAuth2UserInfo` 인터페이스와 factory로 격리한다.
- `CustomOAuth2User`는 OAuth2 principal과 DB `User`를 연결한다.

근거:
- `docs/archive/completed-plans/step3(Oauth2).md`

## OAuth2 State 저장

결정:
- 앱은 stateless 정책이므로 OAuth2 authorization request/state를 session에 저장하지 않는다.
- `CookieOAuth2AuthorizationRequestRepository`로 HttpOnly cookie에 저장한다.

근거:
- `SessionCreationPolicy.STATELESS`에서는 기본 `HttpSessionOAuth2AuthorizationRequestRepository`가 맞지 않는다.
- OAuth2 state 검증은 CSRF 방어 핵심이다.

## OAuth2 Access Token Redirect

현재 결정:
- OAuth2 SuccessHandler는 `{redirect-uri}#accessToken={AT}` fragment 리다이렉트를 사용한다.

향후 개선:
- fragment 방식은 브라우저 히스토리 노출 문제가 남는다.
- Redis 기반 일회성 단기 code 교환 방식이 개선안이다.

근거:
- `docs/archive/completed-plans/step3(Oauth2).md`

## 회원가입 Role

결정:
- 클라이언트가 role을 지정하지 못하게 한다.
- 회원가입 시 서버가 항상 `ROLE_USER`를 부여한다.

근거:
- Step 4-A 보안 취약점 수정.

## 테스트 전략

결정:
- Service는 순수 Mockito 단위 테스트를 우선한다.
- Controller는 `@WebMvcTest` + `MockMvcTester` + `@MockitoBean`을 선호한다.
- JWT 필터 자체 검증은 controller slice가 아니라 별도 security/integration 테스트로 분리한다.
- Redis는 Testcontainers 기반 검증을 사용한다.

근거:
- `docs/plans/step4-A-implementation.md`
- `docs/review/testCodeReview.md`
- `docs/review/retrospective-test.md`

## Session Log

결정:
- 상세 session log는 유지하지 않는다.
- 완료 기록은 git history와 `docs/archive`를 신뢰한다.
- `.memory/`는 현재 상태와 다음 행동만 압축한다.
