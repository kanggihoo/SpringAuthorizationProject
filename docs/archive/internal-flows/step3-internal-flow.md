# Spring Security OAuth2 + JWT 통합 인증 내부 동작 흐름

본 문서는 `step3`에서 구축한 Google OAuth2 소셜 로그인이 Spring Security 프레임워크 내부에서
어떤 시퀀스로 동작하는지 상세히 분석한 설명입니다.
기존 `step2`의 JWT 무상태 인증 체계는 그대로 유지되며, OAuth2 인증 성공 후에도 동일한 서버 자체 JWT를 발급하여 두 방식을 완전히 통합합니다.

---

## 전체 흐름 요약

```
[브라우저]
    │
    ├─ 1. GET /oauth2/authorization/google          ← 인증 시작
    │       ↓
    │  [Spring Security: OAuth2AuthorizationRequestRedirectFilter]
    │       ↓
    │  CookieOAuth2AuthorizationRequestRepository   ← state 쿠키 저장
    │       ↓
    ├─ 2. Google 로그인 페이지로 리다이렉트 (302)
    │
    ├─ 3. 사용자 Google 계정 로그인 + 동의
    │
    ├─ 4. GET /login/oauth2/code/google?code=...&state=...   ← Google 콜백
    │       ↓
    │  [Spring Security: OAuth2LoginAuthenticationFilter]
    │       ↓
    │  (a) state 검증 (CSRF 방어)
    │  (b) code → Access Token 교환 (Google 서버와 통신)
    │  (c) Access Token → UserInfo 조회 (Google UserInfo API)
    │       ↓
    │  CustomOAuth2UserService.loadUser()
    │       ↓
    │  OAuth2AuthenticationSuccessHandler
    │       ↓
    ├─ 5. JWT(AT) + Refresh Token(RT) 발급
    │
    └─ 6. {redirect-uri}#accessToken=... 으로 프론트엔드 리다이렉트
```

---

## 1. 인증 시작 단계 — state 생성 및 쿠키 저장

### 1.1 클라이언트 요청

프론트엔드에서 사용자가 "Google로 로그인" 버튼을 클릭하면 아래 경로로 요청이 발생한다.

```
GET /oauth2/authorization/google
```

이 경로는 `SecurityConfig`에서 `permitAll()`로 열려 있어 JWT 없이 접근 가능하다.
또한 `JwtAuthenticationFilter.shouldNotFilter()`에 의해 JWT 검증을 건너뛴다.

### 1.2 OAuth2AuthorizationRequestRedirectFilter 처리

Spring Security 내장 필터인 `OAuth2AuthorizationRequestRedirectFilter`가 이 경로를 가로챈다.

1. `registrationId` = `"google"` 추출
2. **state 값 생성**: CSRF 공격 방어용 랜덤 문자열 생성
3. Google Authorization Endpoint URL 구성:
   ```
   https://accounts.google.com/o/oauth2/v2/auth
     ?client_id=...
     &redirect_uri=http://localhost:8080/login/oauth2/code/google
     &response_type=code
     &scope=profile+email
     &state=<random_state_value>
   ```
4. **`CookieOAuth2AuthorizationRequestRepository.saveAuthorizationRequest()` 호출**

### 1.3 CookieOAuth2AuthorizationRequestRepository — state 쿠키 저장 (CSRF 방어 핵심)

> **왜 쿠키에 저장하는가?**
> 이 앱은 `SessionCreationPolicy.STATELESS`를 사용하므로 HTTP 세션이 없다.
> Spring 기본 구현체(`HttpSessionOAuth2AuthorizationRequestRepository`)는 세션에 state를 저장하는데,
> 세션이 없으면 state를 보관할 곳이 없어 CSRF 방어가 무력화된다.
> 따라서 state를 **쿠키**에 저장하는 커스텀 구현체를 사용한다.

`saveAuthorizationRequest()` 내부 동작:

1. `OAuth2AuthorizationRequest` 객체를 **Java 직렬화 → Base64 인코딩** 하여 문자열로 변환
2. 쿠키 생성:
   - 이름: `oauth2_auth_request`
   - 값: Base64 직렬화 문자열
   - `HttpOnly = true` (JavaScript 접근 차단 — XSS 방어)
   - `Path = /` (콜백 경로 포함 전 경로에서 전송)
   - `MaxAge = 300` (5분 TTL — 짧은 유효 시간으로 탈취 위험 최소화)
3. 응답에 쿠키 추가 후 Google 로그인 URL로 **302 리다이렉트**

> **직렬화 방식으로 Jackson JSON이 아닌 Java 네이티브 직렬화를 사용하는 이유:**
> `OAuth2AuthorizationRequest`는 `Serializable`을 구현하지만 기본 생성자와 setter가 없어
> Jackson 역직렬화가 불안정하다. 안정성을 위해 Java 직렬화 방식을 채택했다.

---

## 2. Google 로그인 및 콜백 단계

### 2.1 사용자 Google 계정 로그인

브라우저가 Google 로그인 페이지로 이동하며, 사용자가 계정을 선택하고 동의하면
Google은 등록된 Redirect URI로 콜백 요청을 보낸다.

```
GET /login/oauth2/code/google?code=<authorization_code>&state=<state_value>
```

이 경로 역시 `JwtAuthenticationFilter.shouldNotFilter()`에 의해 JWT 검증을 건너뛰며,
`SecurityConfig`에서 `permitAll()`로 열려 있다.

### 2.2 OAuth2LoginAuthenticationFilter — state 검증 (CSRF 방어)

Spring Security 내장 필터 `OAuth2LoginAuthenticationFilter`가 콜백 요청을 처리한다.

**① state 검증 (CSRF 방어 실행)**

1. `CookieOAuth2AuthorizationRequestRepository.loadAuthorizationRequest()` 호출
2. 쿠키에서 `oauth2_auth_request` 값을 읽어 **Base64 디코딩 → Java 역직렬화**로 `OAuth2AuthorizationRequest` 복원
3. 콜백 URL의 `state` 파라미터 ↔ 쿠키에서 복원한 요청의 `state` 값 **일치 여부 자동 검증**
   - 불일치 시: CSRF 공격으로 간주하고 `OAuth2AuthenticationFailureHandler` 호출 후 종료
   - 일치 시: 다음 단계 진행

**② Authorization Code → Access Token 교환**

Spring Security가 Google Token Endpoint와 직접 통신한다.
```
POST https://oauth2.googleapis.com/token
  code=<authorization_code>
  client_id=...
  client_secret=...
  redirect_uri=...
  grant_type=authorization_code
```
응답으로 **Google Access Token**을 수신한다. (이것은 우리 서버의 JWT가 아니라 Google이 발급한 토큰이다.)

**③ Google UserInfo API 호출**

수신한 Google Access Token으로 사용자 정보를 조회한다.
```
GET https://www.googleapis.com/oauth2/v3/userinfo
Authorization: Bearer <google_access_token>
```
응답으로 `sub`, `email`, `name`, `picture` 등의 attribute 맵을 수신한다.

---

## 3. 사용자 정보 처리 단계 — CustomOAuth2UserService

`OAuth2LoginAuthenticationFilter`가 `CustomOAuth2UserService.loadUser()`를 호출한다.

### 3.1 부모 클래스 호출

```java
OAuth2User oauth2User = super.loadUser(userRequest);
```

`DefaultOAuth2UserService.loadUser()`가 내부적으로 Google UserInfo API를 호출하고
attribute 맵을 담은 `OAuth2User` 객체를 반환한다.

### 3.2 OAuth2UserInfoFactory — 제공자별 파싱 (OCP 설계)

```java
String registrationId = userRequest.getClientRegistration().getRegistrationId(); // "google"
OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, oauth2User.getAttributes());
```

`OAuth2UserInfoFactory`는 `registrationId`를 switch문으로 분기하여 해당 제공자 전용 파서를 반환한다.

| registrationId | 반환 구현체 | 파싱 방법 |
|---|---|---|
| `"google"` | `GoogleOAuth2UserInfo` | `sub` → providerId, `email`, `name` |
| `"github"` (예정) | `GithubOAuth2UserInfo` | `id` → providerId, `email`, `login` |
| `"kakao"` (예정) | `KakaoOAuth2UserInfo` | `id` → providerId, 중첩 속성 파싱 |

> **OCP 원칙 준수:** 새로운 제공자를 추가할 때 `CustomOAuth2UserService`는 전혀 수정하지 않아도 된다.
> `OAuth2UserInfoFactory`에 case 1줄과 구현체 파일 1개만 추가하면 된다.

`AuthProvider` Enum 변환:
```java
AuthProvider authProvider = AuthProvider.valueOf(registrationId.toUpperCase()); // "google" → GOOGLE
```

### 3.3 DB 조회 및 자동 회원가입

```java
User user = userRepository
    .findByProviderAndProviderId(authProvider, providerId)
    .orElseGet(() -> registerNewOAuth2User(authProvider, userInfo));
```

**기존 유저인 경우:** provider + providerId로 DB에서 조회하여 그대로 반환

> **이메일이 아닌 providerId(sub)로 조회하는 이유:**
> 이메일은 사용자가 변경할 수 있지만, Google의 `sub` 값은 불변이다.
> providerId 기준으로 조회해야 안정적인 유저 식별이 가능하다.

**신규 유저인 경우:** `registerNewOAuth2User()` 호출하여 자동 회원가입

1. `username` 생성: `"{PROVIDER}_{providerId}"` 형식 (예: `"GOOGLE_1234567890"`)
   - LOCAL 유저의 username과 충돌을 방지하는 네임스페이스 역할
2. `User.oauthBuilder()`로 엔티티 생성 (`password = null` — OAuth2 유저는 비밀번호 불필요)
3. `ROLE_USER` 권한 부여 (`RoleRepository`에서 조회, 없으면 자동 생성)
4. DB 저장 후 반환

### 3.4 CustomOAuth2User 래핑 반환

```java
return new CustomOAuth2User(oauth2User, user);
```

`CustomOAuth2User`는 두 가지를 함께 보관한다:
- `oauth2User`: Google로부터 받은 원본 attribute 맵 (`sub`, `email`, `name`, `picture` 등)
- `user`: 우리 DB의 User 엔티티 (id, username, roles 포함)

> **왜 래핑이 필요한가?**
> Spring Security는 `OAuth2User`만 알고 있어 우리 DB의 User 엔티티를 접근할 방법이 없다.
> `SuccessHandler`에서 JWT 발급 시 `user.getId()`, `user.getUsername()`, `user.getRoles()`에
> 접근하려면 두 객체를 함께 보관하는 래퍼가 반드시 필요하다.
>
> **권한(Authorities):** Google 응답이 아닌 우리 DB의 `Role` 기반으로 제공된다.

---

## 4. JWT 발급 및 응답 단계 — OAuth2AuthenticationSuccessHandler

`OAuth2LoginAuthenticationFilter`가 인증을 완료하면 `OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess()`를 호출한다.

### 4.1 User 엔티티 추출

```java
CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
User user = oAuth2User.getUser();
```

`authentication.getPrincipal()`이 반환하는 것은 3단계에서 래핑한 `CustomOAuth2User`이다.

### 4.2 JWT 발급 (기존 일반 로그인과 완전히 동일한 방식)

```java
List<String> roles = authentication.getAuthorities().stream()
    .map(GrantedAuthority::getAuthority)
    .collect(Collectors.toList());

String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), roles);
String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());
```

이 시점부터 클라이언트는 소셜 로그인 여부와 무관하게 **우리 서버가 발급한 자체 JWT**만으로 API를 이용한다.
Google Access Token은 이 이후 아무 역할도 하지 않는다.

### 4.3 Refresh Token DB 저장 (RTR 패턴)

```java
Optional<RefreshToken> existingToken = refreshTokenRepository.findByUserId(user.getId());
if (existingToken.isPresent()) {
    existingToken.get().updateToken(refreshToken, expiryDate); // Dirty Checking으로 자동 업데이트
} else {
    refreshTokenRepository.save(new RefreshToken(user.getId(), refreshToken, expiryDate));
}
```

기존 `AuthServiceImpl.login()`과 완전히 동일한 RTR 로직을 사용한다.
재로그인 시 새로운 RT로 갱신되어 이전 RT는 즉시 무효화된다.

### 4.4 Refresh Token 쿠키 설정

```java
ResponseCookie refreshCookie = ResponseCookie.from("Refresh-Token", refreshToken)
    .httpOnly(true)   // JavaScript 접근 차단 (XSS 방어)
    .secure(false)    // 운영 환경에서는 true (HTTPS 전용)
    .path("/")
    .maxAge(jwtTokenProvider.getRefreshTokenExpiration() / 1000)
    .sameSite("Lax")  // CSRF 방어: 동일 사이트 + 일부 cross-site GET 허용
    .build();
response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
```

### 4.5 state 쿠키 정리

```java
cookieAuthorizationRequestRepository.deleteCookie(
    request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
```

인증 흐름에서 사용된 `oauth2_auth_request` 쿠키를 `MaxAge=0`으로 만료시켜 삭제한다.
이 정리 작업은 OAuth2 state 검증에 사용된 쿠키의 잔류를 방지한다.

### 4.6 프론트엔드 리다이렉트 — Access Token 전달

```java
String targetUrl = redirectUri + "#accessToken=" + accessToken;
getRedirectStrategy().sendRedirect(request, response, targetUrl);
```

예시: `http://localhost:3000/oauth2/callback#accessToken=eyJhbGciOiJIUzI1NiJ9...`

> **Fragment(`#`)를 사용하는 이유:**
> Query Parameter(`?`)와 달리 Fragment는 HTTP 요청에 포함되지 않아
> 서버 로그, Referer 헤더, Nginx 액세스 로그에 Access Token이 기록되지 않는다.
> 이는 보안 관점에서 Query Param 방식 대비 개선된 접근이다.
>
> **알려진 한계:** Fragment는 브라우저 히스토리에 남을 수 있다.
> 완전한 해결은 일회성 단기 코드(TTL 30초) 교환 방식으로 개선 가능하다. (향후 개선 예정)

---

## 5. 인증 실패 단계 — OAuth2AuthenticationFailureHandler

아래 상황에서 `OAuth2AuthenticationFailureHandler.onAuthenticationFailure()`가 호출된다.

| 실패 원인 | 설명 |
|---|---|
| state 불일치 | CSRF 공격 탐지 — 콜백의 state와 쿠키의 state가 다름 |
| 사용자 취소 | Google 동의 화면에서 취소 버튼 클릭 |
| 미지원 제공자 | `OAuth2UserInfoFactory`에서 `IllegalArgumentException` 발생 |
| 네트워크 오류 | Google UserInfo API 통신 실패 |

처리 흐름:

1. `oauth2_auth_request` 쿠키 삭제 (잔여 쿠키 정리)
2. 에러 메시지 URL 인코딩
3. `{redirect-uri}?error=<encoded_message>` 로 리다이렉트

```
http://localhost:3000/oauth2/callback?error=OAuth2+%EC%9D%B8%EC%A6%9D+%EC%8B%A4%ED%8C%A8
```

> Access Token과 달리 에러 메시지는 민감 정보가 아니므로 Query Parameter를 사용한다.

---

## 6. 인증 완료 이후 — 기존 JWT 흐름과 통합

OAuth2 로그인이 완료된 후 클라이언트는 발급받은 Access Token으로 API를 호출한다.
이 흐름은 `step2`의 일반 로그인 이후 흐름과 **완전히 동일**하다.

```
요청: GET /user/profile
Header: Authorization: Bearer <accessToken>
```

1. `JwtAuthenticationFilter`가 Bearer 토큰 추출
2. `JwtTokenProvider.validateToken()`으로 서명 및 만료 검증
3. Claims에서 `username` 추출
4. `CustomUserDetailsService.loadUserByUsername()`으로 DB에서 최신 UserDetails 조회
5. `SecurityContextHolder`에 인증 정보 저장
6. 이후 필터 체인 통과 → Controller 도달

**토큰 재발급(Refresh)과 로그아웃 흐름도 동일하다.**
OAuth2로 가입한 유저와 일반 가입 유저를 구별할 필요 없이 동일한 `POST /refresh`, `POST /logout` API를 사용한다.

---

## 7. 컴포넌트 역할 요약

| 컴포넌트 | 역할 | 위치 |
|---|---|---|
| `OAuth2AuthorizationRequestRedirectFilter` | 인증 시작, state 생성, Google 리다이렉트 | Spring Security 내장 |
| `CookieOAuth2AuthorizationRequestRepository` | state를 쿠키에 직렬화 저장/복원 (CSRF 방어) | `security/oauth2/` |
| `OAuth2LoginAuthenticationFilter` | 콜백 수신, state 검증, code↔token 교환, UserInfo 조회 | Spring Security 내장 |
| `CustomOAuth2UserService` | UserInfo 파싱, DB 조회, 자동 회원가입 | `security/oauth2/` |
| `OAuth2UserInfoFactory` | registrationId → 파서 구현체 반환 (OCP) | `security/oauth2/` |
| `GoogleOAuth2UserInfo` | Google 전용 attribute 파싱 (`sub`, `email`, `name`) | `security/oauth2/` |
| `CustomOAuth2User` | Google OAuth2User + DB User 엔티티 래퍼 | `security/oauth2/` |
| `OAuth2AuthenticationSuccessHandler` | JWT 발급, RT 쿠키 설정, 프론트 리다이렉트 | `security/oauth2/` |
| `OAuth2AuthenticationFailureHandler` | 에러 메시지 포함 프론트 리다이렉트 | `security/oauth2/` |
| `JwtTokenProvider` | JWT 생성/검증 (OAuth2, 일반 로그인 공통) | `security/jwt/` |

---

## 8. 테스트 가이드

### 8.1 사전 준비

```bash
# Docker 컨테이너 시작
docker-compose up -d

# 환경변수 설정
export GOOGLE_CLIENT_ID=your-client-id
export GOOGLE_CLIENT_SECRET=your-client-secret

# 앱 실행
./gradlew bootRun
```

Google Cloud Console에서 Authorized Redirect URI 등록 필요:
```
http://localhost:8080/login/oauth2/code/google
```

프론트엔드 서버 실행 (React Vite):
```bash
cd frontend && npm run dev  # http://localhost:3000
```

### 8.2 Google OAuth2 로그인 테스트

1. 브라우저에서 `http://localhost:3000` 접속
2. "Google로 로그인" 버튼 클릭 (또는 직접 `http://localhost:8080/oauth2/authorization/google` 접속)
3. Google 계정 선택 및 동의
4. `http://localhost:3000/oauth2/callback#accessToken=...` 으로 리다이렉트 확인
5. URL Fragment에서 `accessToken` 추출

### 8.3 발급된 AT로 API 호출 테스트

```
GET http://localhost:8080/user/profile
Authorization: Bearer <추출한 accessToken>
```

기대 결과: `200 OK`

### 8.4 DB 확인

```sql
-- OAuth2 유저 생성 확인
SELECT id, username, email, provider, provider_id, account_non_locked
FROM users
WHERE provider = 'GOOGLE';

-- RT 저장 확인
SELECT * FROM refresh_tokens WHERE user_id = <위에서 확인한 id>;
```

### 8.5 재로그인 시 중복 생성 방지 확인

동일 Google 계정으로 재로그인 시 `users` 테이블에 레코드가 추가되지 않고 기존 유저가 재활용되는지 확인한다.
RT는 새 값으로 갱신(upsert)되어야 한다.

### 8.6 기존 일반 로그인 회귀 테스트

OAuth2 도입 후에도 `POST /login` (일반 로그인)이 정상 동작하는지 확인한다.

```
POST http://localhost:8080/login
Content-Type: application/json

{
  "username": "user1",
  "password": "password"
}
```

기대 결과: `200 OK`, accessToken + Refresh-Token 쿠키 반환

---

## 9. step2와의 차이점 요약

| 항목 | step2 (일반 JWT 로그인) | step3 (OAuth2 추가) |
|---|---|---|
| 인증 시작 | `POST /login` JSON 요청 | `GET /oauth2/authorization/google` 브라우저 리다이렉트 |
| 비밀번호 | 필수 (`BCrypt` 해시 검증) | 없음 (`password = null`) |
| 사용자 정보 출처 | 우리 DB | Google UserInfo API + 우리 DB |
| JWT 발급 주체 | `AuthServiceImpl.login()` | `OAuth2AuthenticationSuccessHandler` |
| JWT 발급 방식 | JSON Response Body | URL Fragment (`#accessToken=...`) |
| CSRF 방어 수단 | 해당 없음 (REST API) | state 파라미터 + Cookie 저장 |
| 회원가입 방식 | `POST /signup` 직접 호출 | 최초 로그인 시 자동 회원가입 |
| **인증 이후 흐름** | **JWT 기반 API 호출** | **완전히 동일** |
