# OAuth2 로그인 인증 흐름 상세 정리

이 문서는 현재 프로젝트의 OAuth2 로그인 인증 흐름을 설명한다. 기존 메모에 흩어져 있던 내용을 줄이는 것이 아니라, **같은 내용을 읽는 순서만 다시 잡는 것**을 목표로 한다.

핵심 구조는 다음 한 문장으로 정리할 수 있다.

> Google 인증은 “외부 Provider에서 사용자를 확인하는 단계”이고, OAuth2 성공 이후의 서비스 API 인증은 우리 서비스가 직접 발급한 Access Token/Refresh Token으로 처리한다.

즉, Google Access Token을 우리 서비스의 Protected API 인증 수단으로 쓰지 않는다. OAuth2 Callback이 성공하면 `OAuth2AuthenticationSuccessHandler`가 실행되고, 여기서 Local login과 같은 `TokenLifecycleService` 정책으로 서비스 Access Token과 Refresh Token을 발급한다.

## 1. 먼저 구분해야 할 경로

OAuth2 로그인 흐름에서 가장 헷갈리는 지점은 callback이라는 말을 여러 곳에서 쓰는 것이다. 이 프로젝트에서는 다음 두 경로를 분리해서 봐야 한다.

| 구분 | 경로 | 누가 호출하는가 | 누가 처리하는가 | 역할 |
| --- | --- | --- | --- | --- |
| OAuth2 시작 경로 | `/oauth2/authorization/google` | 프론트엔드 또는 브라우저 | `OAuth2AuthorizationRequestRedirectFilter` | Google 로그인 화면으로 보내는 redirect 생성 |
| 백엔드 OAuth2 Callback | `/login/oauth2/code/google?code=...&state=...` | Google | `OAuth2LoginAuthenticationFilter` | authorization code와 state를 받아 OAuth2 인증 완료 |
| 프론트엔드 완료 페이지 | `http://localhost:3000/oauth2/callback` | 백엔드 성공/실패 핸들러 | React 라우트 | 백엔드 OAuth2 처리 완료 후 사용자가 돌아갈 화면 |

`/login/oauth2/code/google`은 프론트엔드 callback이 아니다. Google이 백엔드로 돌려주는 OAuth2 Callback이다. 프론트엔드 callback은 현재 설정상 `app.oauth2.redirect-uri`인 `http://localhost:3000/oauth2/callback`이다.

## 2. 전체 흐름 한 번에 보기

먼저 전체 흐름을 세부 구현 없이 연결하면 다음과 같다.

```text
1. 브라우저가 /oauth2/authorization/google 요청
2. Spring Security가 Google authorization endpoint로 보낼 OAuth2AuthorizationRequest 생성
3. CookieOAuth2AuthorizationRequestRepository가 request/state를 oauth2_auth_request cookie에 저장
4. 브라우저를 Google 로그인/동의 화면으로 redirect
5. Google 인증 성공
6. Google이 /login/oauth2/code/google?code=...&state=... 로 백엔드 callback 호출
7. OAuth2LoginAuthenticationFilter가 cookie에서 기존 OAuth2AuthorizationRequest 복원
8. callback의 state와 저장된 state 비교
9. authorization code를 Google token endpoint에 보내 Google provider access token 획득
10. Google UserInfo API로 사용자 정보 조회
11. CustomOAuth2UserService가 providerId 기준으로 서비스 User 연결 또는 자동 가입
12. CustomOAuth2User를 Spring Security Authentication principal로 반환
13. OAuth2AuthenticationSuccessHandler가 서비스 Access Token/Refresh Token 발급
14. Refresh Token은 HttpOnly cookie로 내려가고, 현재 구현에서는 Access Token이 URL fragment로 전달됨
15. 이후 Protected API는 Authorization: Bearer {serviceAccessToken} 으로 호출
16. JwtAuthenticationFilter가 서비스 Access Token을 검증하고 SecurityContext를 구성
```

이 흐름에서 토큰이 두 종류 등장한다.

- Google provider access token
  - authorization code를 Google token endpoint에 보내 얻는 토큰이다.
  - Google UserInfo API 호출이나 Google API 재호출에 쓰일 수 있다.
  - 우리 서비스 Protected API 인증에는 사용하지 않는다.
- 서비스 Access Token/Refresh Token
  - `TokenLifecycleService.issue(...)`가 발급한다.
  - Protected API 인증, `/refresh`, `/logout`, Refresh Token Rotation, Logout Blacklist 정책에 참여한다.

## 3. 프로젝트 설정에서 OAuth2 흐름이 연결되는 지점

OAuth2 설정은 `SecurityConfig`의 `filterChain(HttpSecurity http)`에서 연결된다.

```java
.oauth2Login(oauth2 -> oauth2
    .authorizationEndpoint(endpoint -> endpoint
        .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository))
    .redirectionEndpoint(endpoint ->
        endpoint.baseUri("/login/oauth2/code/*"))
    .userInfoEndpoint(endpoint ->
        endpoint.userService(customOAuth2UserService))
    .successHandler(oAuth2AuthenticationSuccessHandler)
    .failureHandler(oAuth2AuthenticationFailureHandler)
)
```

각 설정의 의미는 다음과 같다.

- `authorizationEndpoint(...)`
  - `/oauth2/authorization/google` 같은 OAuth2 시작 요청과 관련된다.
  - 이 프로젝트는 기본 세션 저장소 대신 `CookieOAuth2AuthorizationRequestRepository`를 주입한다.
  - 그래서 OAuth2 `state`와 authorization request를 `HttpSession`이 아니라 cookie에 저장한다.
- `redirectionEndpoint(...)`
  - Google이 인증 성공 후 백엔드로 돌려보낼 callback 경로를 지정한다.
  - 현재 값은 `/login/oauth2/code/*`다.
- `userInfoEndpoint(...)`
  - Google provider access token으로 UserInfo API를 호출한 뒤, 사용자 정보를 서비스 User와 연결할 때 `CustomOAuth2UserService`를 사용한다.
- `successHandler(...)`
  - OAuth2 인증이 성공하면 `OAuth2AuthenticationSuccessHandler`가 호출된다.
  - 여기서 서비스 JWT를 발급한다.
- `failureHandler(...)`
  - OAuth2 인증이 실패하면 `OAuth2AuthenticationFailureHandler`가 호출된다.
  - state cookie를 정리하고 프론트엔드로 에러를 전달한다.

또한 다음 경로들은 인증 없이 접근 가능하도록 열려 있다.

```java
.requestMatchers(
    "/", "/signup", "/login", "/refresh", "/error",
    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
    "/oauth2/**",
    "/login/oauth2/**"
).permitAll()
```

`/oauth2/**`와 `/login/oauth2/**`가 `permitAll`인 이유는 아직 서비스 Access Token이 없는 사용자가 OAuth2 로그인을 시작하거나 callback을 받을 수 있어야 하기 때문이다.

## 4. `/oauth2/authorization/google`: OAuth2 로그인 시작 요청

사용자가 Google 로그인 버튼을 누르면 브라우저가 다음 경로로 이동한다.

```text
GET /oauth2/authorization/google
```

이 요청은 컨트롤러로 가는 요청이 아니다. Spring Security Filter Chain 안에서 `OAuth2AuthorizationRequestRedirectFilter`가 처리한다.

### 4.1 FilterChainProxy가 먼저 전체 필터 체인을 고른다

Spring Security 요청은 먼저 `FilterChainProxy` 안으로 들어간다. 여기서 현재 요청에 맞는 `SecurityFilterChain`을 찾고, 그 체인에 등록된 필터들을 순서대로 실행한다.

개념적으로는 다음과 같다.

```java
List<Filter> filters = getFilters(firewallRequest);
this.filterChainDecorator.decorate(reset, filters)
    .doFilter(firewallRequest, firewallResponse);
```

현재 프로젝트는 `SecurityConfig`에서 `SecurityFilterChain`을 하나만 만들고 있으므로, 대부분 요청은 이 체인 안의 필터들을 순서대로 탄다.

중요한 점은 Spring Security가 처음부터 `/oauth2/authorization/google`을 보고 해당 필터로 “점프”하는 라우터처럼 동작하지 않는다는 것이다. 필터들을 순서대로 호출하고, 각 필터가 “이 요청은 내가 처리할 요청인가?”를 판단한다.

### 4.2 실제 경로 매칭은 DefaultOAuth2AuthorizationRequestResolver가 한다

`/oauth2/authorization/google`을 OAuth2 시작 요청으로 인식하는 핵심 패턴은 다음이다.

```text
/oauth2/authorization/{registrationId}
```

Spring Security 내부의 `DefaultOAuth2AuthorizationRequestResolver`가 이 패턴을 사용한다. 요청이 `/oauth2/authorization/google`이면 `{registrationId}` 값으로 `google`이 추출된다.

개념적으로는 다음과 같다.

```java
if (authorizationRequestMatcher.matches(request)) {
    return matcher(request)
        .getVariables()
        .get("registrationId");
}
```

여기서 추출된 `registrationId = "google"`을 기준으로 `ClientRegistration`을 찾는다. `ClientRegistration`에는 Google OAuth2 client 설정이 들어 있다.

```text
clientId
clientSecret
authorizationUri
tokenUri
userInfoUri
redirectUri
scope
```

### 4.3 OAuth2AuthorizationRequest가 만들어진다

`OAuth2AuthorizationRequestRedirectFilter`는 resolver를 통해 `OAuth2AuthorizationRequest`를 만든다.

이 객체에는 Google authorization endpoint로 보낼 정보가 들어 있다.

```text
client_id
redirect_uri
scope
response_type=code
state
registrationId
additionalParameters
attributes
```

특히 `state`가 중요하다. `state`는 OAuth2 Callback이 실제로 이 로그인 시작 요청에서 이어진 것인지 확인하기 위한 값이다. 나중에 Google callback으로 돌아온 `state`와, 로그인 시작 시 저장해 둔 `state`를 비교한다.

### 4.4 OAuth2AuthorizationRequest를 cookie에 저장한다

Spring Security 기본 구현은 OAuth2 authorization request를 `HttpSession`에 저장한다. 하지만 이 프로젝트는 다음 정책을 갖는다.

```java
session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
```

세션을 만들지 않는 구조이므로, 기본 `HttpSessionOAuth2AuthorizationRequestRepository`를 쓰면 state를 보관하기 어렵다. 그래서 `CookieOAuth2AuthorizationRequestRepository`를 사용한다.

현재 저장 흐름은 다음과 같다.

```text
OAuth2AuthorizationRequest 객체
-> Java serialization으로 byte[] 변환
-> URL-safe Base64 문자열로 인코딩
-> oauth2_auth_request HttpOnly cookie에 저장
```

코드 기준으로는 `saveAuthorizationRequest(...)`가 실행된다.

```java
String serialized = serialize(authorizationRequest);
Cookie cookie = new Cookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialized);
cookie.setPath("/");
cookie.setHttpOnly(true);
cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);
response.addCookie(cookie);
```

cookie 이름은 다음이다.

```text
oauth2_auth_request
```

유효 시간은 현재 코드 기준 300초다.

```java
private static final int COOKIE_EXPIRE_SECONDS = 300;
```

### 4.5 Google로 redirect하고 필터 체인을 끊는다

authorization request가 만들어지면 `OAuth2AuthorizationRequestRedirectFilter`는 Google 로그인 페이지로 보내는 redirect 응답을 만든다.

개념적으로는 다음과 같다.

```java
OAuth2AuthorizationRequest authorizationRequest =
    this.authorizationRequestResolver.resolve(request);

if (authorizationRequest != null) {
    this.sendRedirectForAuthorization(request, response, authorizationRequest);
    return;
}
```

응답은 대략 이렇게 된다.

```http
HTTP/1.1 302 Found
Location: https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&state=...
Set-Cookie: oauth2_auth_request=...
```

여기서 `return`은 단순한 최적화가 아니다. 필터 체인을 여기서 끝내기 위한 단락이다.

만약 `sendRedirect(...)` 후에도 `filterChain.doFilter(...)`를 계속 호출하면 다음 문제가 생길 수 있다.

```text
1. 이미 response에 302 redirect를 씀
2. 그런데 다음 필터로 계속 진행
3. AuthorizationFilter, DispatcherServlet, Controller 등이 추가 처리 시도
4. 응답이 이미 commit됐거나 redirect 상태인데 또 다른 응답을 쓰려 함
5. 중복 처리, 예외, 응답 오염 가능
```

`sendRedirect`는 “어떤 응답을 보낼지” 정하는 일이고, `return`은 “현재 요청 처리를 여기서 끝낸다”는 보장이다.

반대로 현재 요청이 `/oauth2/authorization/{registrationId}`가 아니면 `authorizationRequest == null`이고, 이 필터는 다음 필터로 요청을 넘긴다.

```java
filterChain.doFilter(request, response);
```

## 5. Cookie-backed OAuth2 State를 자세히 보기

OAuth2 state 저장소를 cookie로 옮긴 이유와 한계를 분리해서 봐야 한다.

### 5.1 왜 state 문자열만 저장하지 않는가

단순히 state 비교만 생각하면 “state 문자열만 저장하면 되는 것 아닌가?”라고 볼 수 있다. 하지만 Spring Security callback 처리에는 state 하나만 필요한 것이 아니다.

callback에서 `OAuth2LoginAuthenticationFilter`는 저장된 `OAuth2AuthorizationRequest`를 꺼낸다.

```java
OAuth2AuthorizationRequest authorizationRequest =
    this.authorizationRequestRepository.removeAuthorizationRequest(request, response);
```

그리고 이 객체와 Google callback response를 묶어서 `OAuth2AuthorizationExchange`를 만든다.

```java
new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse)
```

`OAuth2AuthorizationRequest` 안에는 보통 다음 정보가 들어 있다.

```text
registrationId
clientId
authorizationUri
redirectUri
scopes
state
additionalParameters
attributes
PKCE code_verifier 가능
OIDC nonce 가능
```

state만 저장하려면 callback 시점에 나머지 정보를 정확히 재구성해야 한다. 가능은 하지만 Spring Security 기본 저장소 계약을 직접 다시 구현하는 일이 된다. 그래서 현재 구조는 `OAuth2AuthorizationRequest` 전체를 cookie에 저장한다.

### 5.2 Base64는 보안 기능이 아니다

`CookieOAuth2AuthorizationRequestRepository`는 authorization request 객체를 Java serialization으로 byte 배열로 만들고, 그 byte 배열을 URL-safe Base64 문자열로 바꾼다.

```java
oos.writeObject(request);
return Base64.getUrlEncoder().encodeToString(baos.toByteArray());
```

이유는 단순하다.

```text
OAuth2AuthorizationRequest 객체
-> Java 직렬화 결과는 binary byte[]
-> cookie value에는 그대로 넣기 부적합
-> cookie-safe 문자열로 만들기 위해 Base64 인코딩
```

Base64는 암호화가 아니고 서명도 아니다. 누구나 디코딩할 수 있다. 따라서 “state가 변하지 않았음을 암호학적으로 증명”하지 않는다. 그냥 cookie에 넣기 쉬운 문자열 표현으로 바꾸는 것이다.

### 5.3 Base64 값은 callback에서 다시 객체로 복원된다

callback 요청이 오면 `loadAuthorizationRequest(...)`가 cookie 값을 읽는다.

```java
return getCookieValue(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
    .map(this::deserialize)
    .orElse(null);
```

그리고 `deserialize(...)`에서 Base64 디코딩 후 Java 역직렬화를 수행한다.

```java
return (OAuth2AuthorizationRequest) ois.readObject();
```

복원된 객체는 `OAuth2LoginAuthenticationFilter`의 callback 처리 과정에서 사용된다.

### 5.4 현재 구현의 방어 요소와 한계

현재 구현의 방어 요소는 다음이다.

```text
HttpOnly
짧은 TTL 300초
callback 후 삭제
저장된 request의 state와 callback query parameter의 state 비교
```

하지만 cookie 내용이 절대 변조되지 않았음을 암호학적으로 보장하는 설계는 아니다. `HttpOnly`는 JavaScript 접근을 막지만, cookie 자체에 HMAC 서명이 붙어 있는 것은 아니다.

더 엄격한 설계가 필요하면 다음 방식 중 하나를 고려할 수 있다.

```text
1. cookie 값에 HMAC 서명 추가
2. 암호화/서명된 cookie 사용
3. Redis 같은 서버 저장소에 state -> OAuth2AuthorizationRequest 저장
4. cookie에는 state key만 저장
```

현재 ADR 0010의 핵심은 “세션을 다시 도입하지 않고 OAuth2 state를 보관한다”는 것이다. cookie 저장 방식은 그 결정을 구현한 현재 방식이다.

## 6. `/login/oauth2/code/google`: Google 인증 후 백엔드 Callback

Google 인증이 성공하면 Google은 백엔드로 다음 요청을 보낸다.

```text
GET /login/oauth2/code/google?code=...&state=...
```

이 요청도 컨트롤러가 아니라 Spring Security 필터가 처리한다. 담당 필터는 `OAuth2LoginAuthenticationFilter`다.

### 6.1 SecurityConfig의 redirectionEndpoint와 연결된다

`SecurityConfig`에는 다음 설정이 있다.

```java
.redirectionEndpoint(endpoint ->
    endpoint.baseUri("/login/oauth2/code/*"))
```

이 설정은 “Google이 우리 서버로 다시 보낸 authorization response를 `/login/oauth2/code/*`에서 받겠다”는 뜻이다.

Spring Security 내부에서는 이 값이 `OAuth2LoginAuthenticationFilter`의 request matcher로 연결된다.

개념적으로는 다음과 같다.

```java
authenticationFilter.setRequiresAuthenticationRequestMatcher(
    matcher("/login/oauth2/code/*")
);
```

그래서 실제 callback 흐름은 다음처럼 이어진다.

```text
Google 로그인 완료
-> GET /login/oauth2/code/google?code=...&state=...
-> SecurityFilterChain 진입
-> OAuth2LoginAuthenticationFilter가 경로 매칭
-> attemptAuthentication(...) 실행
```

### 6.2 OAuth2LoginAuthenticationFilter는 doFilter를 직접 구현하지 않는다

`OAuth2LoginAuthenticationFilter`도 필터다. 다만 `doFilter(...)`를 직접 구현하지 않고, 부모 클래스인 `AbstractAuthenticationProcessingFilter`가 구현한 `doFilter(...)`를 상속해서 쓴다.

구조는 다음과 같다.

```java
public class OAuth2LoginAuthenticationFilter
    extends AbstractAuthenticationProcessingFilter
```

부모 클래스의 흐름은 개념적으로 다음과 같다.

```java
if (!requiresAuthentication(request, response)) {
    chain.doFilter(request, response);
    return;
}

Authentication authenticationResult =
    attemptAuthentication(request, response);

successfulAuthentication(request, response, chain, authenticationResult);
```

즉 `/login/oauth2/code/*` 요청이면 `requiresAuthentication(...)`이 true가 되고, 그때 `OAuth2LoginAuthenticationFilter.attemptAuthentication(...)`가 호출된다.

### 6.3 attemptAuthentication이 하는 일

`OAuth2LoginAuthenticationFilter`의 핵심은 `attemptAuthentication(...)`이다. 이 메서드는 Google callback 요청을 Spring Security 인증 요청으로 바꾼다.

흐름을 순서대로 보면 다음과 같다.

1. callback query parameter를 읽는다.

```java
MultiValueMap<String, String> params =
    OAuth2AuthorizationResponseUtils.toMultiMap(request.getParameterMap());
```

Google이 보낸 요청에는 보통 다음 값이 있다.

```text
code=...
state=...
```

2. 이 요청이 OAuth2 authorization response인지 검사한다.

```java
OAuth2AuthorizationResponseUtils.isAuthorizationResponse(params)
```

`code` 또는 OAuth2 error parameter가 없으면 잘못된 callback으로 본다.

3. 이전에 저장해 둔 `OAuth2AuthorizationRequest`를 꺼낸다.

```java
OAuth2AuthorizationRequest authorizationRequest =
    this.authorizationRequestRepository.removeAuthorizationRequest(request, response);
```

현재 프로젝트에서는 이 repository가 `CookieOAuth2AuthorizationRequestRepository`다. 따라서 여기서 `oauth2_auth_request` cookie를 읽고, Base64 디코딩/역직렬화로 원래 request 객체를 복원한다.

4. 저장된 요청이 없으면 실패한다.

보통 다음 상황에서 발생할 수 있다.

```text
OAuth2 시작 요청 없이 callback만 직접 호출
state cookie 만료
cookie 삭제
다른 브라우저나 탭에서 잘못 이어진 흐름
```

5. `registrationId`를 꺼낸다.

```java
String registrationId =
    authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
```

예시는 다음이다.

```text
google
```

6. `ClientRegistration`을 찾는다.

```java
ClientRegistration clientRegistration =
    this.clientRegistrationRepository.findByRegistrationId(registrationId);
```

7. 현재 callback URL을 계산한다.

```java
String redirectUri =
    UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
        .replaceQuery(null)
        .build()
        .toUriString();
```

예시는 다음이다.

```text
http://localhost:8080/login/oauth2/code/google
```

`?code=...&state=...` 부분은 제거된다.

8. callback parameter를 `OAuth2AuthorizationResponse`로 변환한다.

```java
OAuth2AuthorizationResponse authorizationResponse =
    OAuth2AuthorizationResponseUtils.convert(params, redirectUri);
```

여기에 Google이 돌려준 `code`, `state`가 들어간다.

9. 이전 요청과 현재 응답을 묶는다.

```java
new OAuth2AuthorizationExchange(
    authorizationRequest,
    authorizationResponse
)
```

이 객체는 다음 두 값을 함께 들고 있다.

```text
authorizationRequest = 우리가 Google로 보냈던 요청 정보
authorizationResponse = Google이 callback으로 돌려준 code/state
```

10. 인증 요청 객체를 만든다.

```java
OAuth2LoginAuthenticationToken authenticationRequest =
    new OAuth2LoginAuthenticationToken(
        clientRegistration,
        new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse)
    );
```

이 토큰은 아직 로그인 완료된 사용자가 아니다. `AuthenticationManager`에게 넘길 인증 요청이다.

11. `AuthenticationManager`에게 인증을 위임한다.

```java
OAuth2LoginAuthenticationToken authenticationResult =
    (OAuth2LoginAuthenticationToken) this.getAuthenticationManager()
        .authenticate(authenticationRequest);
```

여기서 실제 provider가 동작한다.

12. provider는 state를 검증하고 authorization code를 provider access token으로 교환한다.

핵심 작업은 다음이다.

```text
authorizationRequest.state 와 authorizationResponse.state 비교
code를 Google token endpoint로 전송
Google provider access token 획득
provider access token으로 Google UserInfo 조회
CustomOAuth2UserService.loadUser(...) 호출
```

13. 인증 결과를 `OAuth2AuthenticationToken`으로 변환한다.

```java
OAuth2AuthenticationToken oauth2Authentication =
    this.authenticationResultConverter.convert(authenticationResult);
```

이 객체가 Spring Security가 이해하는 OAuth2 로그인 완료 Authentication이다.

14. `OAuth2AuthorizedClient`를 저장할 수 있다.

```java
OAuth2AuthorizedClient authorizedClient =
    new OAuth2AuthorizedClient(
        authenticationResult.getClientRegistration(),
        oauth2Authentication.getName(),
        authenticationResult.getAccessToken(),
        authenticationResult.getRefreshToken()
    );
```

여기서 저장되는 access token은 서비스 JWT가 아니라 Google provider access token이다.

15. 최종 Authentication을 반환한다.

반환값은 부모 클래스 `AbstractAuthenticationProcessingFilter`로 돌아가고, 성공 처리 과정에서 프로젝트가 등록한 `OAuth2AuthenticationSuccessHandler`가 실행된다.

전체를 한 줄 흐름으로 보면 다음과 같다.

```text
GET /login/oauth2/code/google?code=...&state=...
-> AbstractAuthenticationProcessingFilter.doFilter()
-> requiresAuthentication() == true
-> OAuth2LoginAuthenticationFilter.attemptAuthentication()
-> OAuth2LoginAuthenticationProvider.authenticate()
-> CustomOAuth2UserService.loadUser()
-> OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess()
-> TokenLifecycleService.issue()
-> 서비스 JWT 발급
```

## 7. state 검증은 언제 일어나는가

로그인 시작 시 저장한 `state`와 Google callback에 붙어 돌아온 `state`를 비교한다.

전체 흐름은 다음이다.

```text
1. /oauth2/authorization/google 요청
2. Spring Security가 OAuth2AuthorizationRequest 생성
   - state = 랜덤 문자열
   - redirectUri
   - clientId
   - scope
   - registrationId
   - 기타 attribute
3. CookieOAuth2AuthorizationRequestRepository가 이 객체를 Base64 문자열로 cookie에 저장
4. Google로 redirect
5. Google 인증 성공
6. /login/oauth2/code/google?code=...&state=... 로 callback
7. cookie에서 기존 OAuth2AuthorizationRequest 복원
8. 복원된 객체 안의 state와 callback query parameter의 state 비교
```

더 단순하게 표현하면 다음이다.

```text
cookie value = Base64(직렬화된 OAuth2AuthorizationRequest(state 포함))
callback query = ?code=...&state=...
Spring Security = cookie 안 state == query string state 비교
```

값이 다르면 OAuth2 인증은 실패한다. 이 경우 보통 `OAuth2AuthenticationFailureHandler`로 넘어간다.

여기서 다시 강조할 점은 Base64가 state 비교 자체를 위한 보안 장치가 아니라는 것이다. Base64는 `OAuth2AuthorizationRequest` 객체를 cookie value로 넣기 위한 문자열화 방식이다. 실제 비교 대상은 cookie에서 복원한 request 객체 안의 `state`와 callback query parameter의 `state`다.

## 8. Google 사용자 정보를 서비스 User로 연결하는 과정

authorization code 교환과 UserInfo 조회가 끝나면 `CustomOAuth2UserService`가 실행된다.

```java
public OAuth2User loadUser(OAuth2UserRequest userRequest)
```

이 클래스는 `DefaultOAuth2UserService`를 상속한다. 먼저 부모 클래스의 `loadUser(...)`를 호출해 Google UserInfo API 결과를 가져온다.

```java
OAuth2User oauth2User = super.loadUser(userRequest);
```

그 다음 `registrationId`를 확인한다.

```java
String registrationId =
    userRequest.getClientRegistration().getRegistrationId();
```

현재 Google이면 값은 다음과 같다.

```text
google
```

이 값으로 provider별 parser를 고른다.

```java
OAuth2UserInfo userInfo =
    OAuth2UserInfoFactory.getOAuth2UserInfo(
        registrationId,
        oauth2User.getAttributes()
    );
```

Google에서는 `GoogleOAuth2UserInfo`가 다음 값을 읽는다.

```text
sub
email
name
```

서비스 User 조회 기준은 email이 아니라 `provider + providerId`다.

```java
userRepository.findByProviderAndProviderId(authProvider, providerId)
```

email은 변경될 수 있지만, Google의 `sub`는 provider 내부 사용자 식별자로 더 안정적이기 때문이다.

기존 User가 없으면 자동 가입한다.

```java
String username = provider.name() + "_" + userInfo.getProviderId();
```

예시는 다음이다.

```text
GOOGLE_1234567890
```

새 OAuth2 User는 password가 필요 없으므로 OAuth2 전용 builder를 통해 생성한다. 기본 Role은 `ROLE_USER`다.

결과적으로 이 단계의 목표는 다음이다.

```text
Google OAuth2 user attributes
-> provider/providerId 기준 서비스 User 조회
-> 없으면 OAuth2 User 자동 가입
-> 서비스 Role 부여
-> CustomOAuth2User로 래핑
```

## 9. CustomOAuth2User가 필요한 이유

Spring Security가 기본으로 다루는 `OAuth2User`는 Google에서 받은 attribute 중심의 객체다. 하지만 OAuth2 성공 후 서비스 JWT를 발급하려면 우리 DB의 User 정보가 필요하다.

예를 들어 성공 핸들러는 다음 값이 필요하다.

```text
서비스 User id
JWT Subject로 쓸 username
서비스 Role 목록
enabled 여부
Account Lock 여부
```

그래서 프로젝트는 `CustomOAuth2User`를 사용한다.

```java
public class CustomOAuth2User implements OAuth2User, AuthenticatedUser
```

이 객체는 두 역할을 동시에 한다.

- Spring Security OAuth2 흐름에서는 `OAuth2User`로 보인다.
- 서비스 인증 도메인에서는 `AuthenticatedUser`로 보인다.

`getJwtSubject()`는 서비스 JWT Subject로 사용할 username을 반환한다.

```java
@Override
public String getJwtSubject() {
    return user.getUsername();
}
```

권한도 Google이 준 권한이 아니라 서비스 DB User의 Role을 기준으로 만든다.

```java
return user.getRoles().stream()
    .map(role -> (GrantedAuthority) role::getName)
    .toList();
```

따라서 OAuth2 User도 서비스 관점에서는 Local User와 같은 `Authenticated User` 모델로 이어진다.

## 10. OAuth2 성공 후 서비스 JWT 발급

OAuth2 인증이 성공하면 `OAuth2AuthenticationSuccessHandler`가 실행된다.

```java
public void onAuthenticationSuccess(
    HttpServletRequest request,
    HttpServletResponse response,
    Authentication authentication
)
```

여기서 `authentication.getPrincipal()`은 `CustomOAuth2User`이고, `AuthenticatedUser`로 캐스팅할 수 있다.

```java
AuthenticatedUser authenticatedUser =
    (AuthenticatedUser) authentication.getPrincipal();
```

그 다음 Spring Security Authentication에 들어 있는 권한 목록을 모은다.

```java
List<String> roles = authentication.getAuthorities().stream()
    .map(GrantedAuthority::getAuthority)
    .collect(Collectors.toList());
```

그리고 서비스 JWT를 발급한다.

```java
TokenResponseDto tokenResponse = tokenLifecycleService.issue(
    authenticatedUser.getJwtSubject(), roles);
```

여기서 중요한 점은 `jwtSubject`가 provider id가 아니라 서비스 username이라는 것이다. 이 프로젝트의 ADR 0003은 JWT Subject와 Redis token owner key를 username으로 결정했다.

`TokenLifecycleServiceImpl.issue(...)` 내부에서는 다음 일이 일어난다.

```java
String accessToken =
    jwtTokenProvider.generateAccessToken(jwtSubject, roles);

String refreshToken =
    jwtTokenProvider.generateRefreshToken(jwtSubject);

tokenRedisRepository.saveRefreshToken(
    jwtSubject,
    refreshToken,
    getRefreshTokenTtlSeconds()
);
```

즉 OAuth2 login도 Local login과 같은 Token Store 정책을 사용한다.

```text
OAuth2 login success
-> TokenLifecycleService.issue(username, roles)
-> Access Token 발급
-> Refresh Token 발급
-> Redis Token Store에 active Refresh Token 저장
```

이후 Refresh Token은 HttpOnly cookie로 내려간다.

```java
tokenDeliveryService.addRefreshTokenCookie(
    response,
    tokenResponse.getRefreshToken()
);
```

그리고 OAuth2 state cookie도 정리한다.

```java
cookieAuthorizationRequestRepository.deleteCookie(
    request,
    response,
    CookieOAuth2AuthorizationRequestRepository
        .OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME
);
```

마지막으로 현재 구현은 Access Token을 URL fragment에 붙여 프론트엔드 callback URI로 redirect한다.

```java
String targetUrl =
    redirectUri + "#accessToken=" + tokenResponse.getAccessToken();

getRedirectStrategy().sendRedirect(request, response, targetUrl);
```

현재 설정값 기준 최종 redirect는 다음 형태다.

```text
http://localhost:3000/oauth2/callback#accessToken=...
```

## 11. OAuth2 실패 처리

OAuth2 인증이 실패하면 `OAuth2AuthenticationFailureHandler`가 실행된다.

대표 실패 상황은 다음이다.

```text
사용자가 Google 동의 화면에서 취소
callback의 state가 저장된 state와 다름
oauth2_auth_request cookie가 없음
oauth2_auth_request cookie가 만료됨
지원하지 않는 registrationId/provider
Google UserInfo 조회 실패
서비스 User 연결 또는 자동 가입 중 예외
```

실패 핸들러는 남아 있는 OAuth2 state cookie를 삭제한다.

```java
cookieAuthorizationRequestRepository.deleteCookie(
    request,
    response,
    CookieOAuth2AuthorizationRequestRepository
        .OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME
);
```

그리고 프론트엔드 callback URI에 error query parameter를 붙여 redirect한다.

```text
http://localhost:3000/oauth2/callback?error=...
```

이 흐름에서는 서비스 Access Token과 Refresh Token이 발급되지 않는다.

## 12. OAuth2AuthorizedClient에 저장되는 토큰은 무엇인가

OAuth2 Login 내부에서 `OAuth2AuthorizedClient`가 등장할 수 있다. 여기서 헷갈리기 쉬운 점은 이 객체에 들어가는 access token이 서비스 JWT가 아니라는 것이다.

```java
OAuth2AuthorizedClient authorizedClient =
    new OAuth2AuthorizedClient(
        authenticationResult.getClientRegistration(),
        oauth2Authentication.getName(),
        authenticationResult.getAccessToken(),
        authenticationResult.getRefreshToken()
    );
```

`authenticationResult.getAccessToken()`은 Google provider access token이다.

의미는 다음에 가깝다.

```text
"이 principal은 google client에 대해 authorized 된 상태이고,
Google API를 호출할 수 있는 provider access token은 이것이다."
```

나중에 서버가 Google API를 다시 호출해야 한다면 `OAuth2AuthorizedClientManager`, `@RegisteredOAuth2AuthorizedClient`, OAuth2 WebClient 같은 Spring Security OAuth2 Client 기능에서 이 값을 활용할 수 있다.

하지만 현재 프로젝트의 핵심 API 인증 흐름에서는 이 값을 거의 쓰지 않는다. Protected API는 Google token이 아니라 서비스 Access Token을 본다.

## 13. OAuth2 이후 Protected API 인증 흐름

OAuth2 로그인이 끝난 뒤 Protected API를 호출할 때는 Spring Security OAuth2 principal을 계속 쓰지 않는다.

프론트엔드는 서비스 Access Token을 Authorization header에 넣어 요청한다.

```http
Authorization: Bearer {serviceAccessToken}
```

이 요청은 `JwtAuthenticationFilter`가 처리한다.

### 13.1 OAuth2 경로는 JwtAuthenticationFilter가 건너뛴다

`JwtAuthenticationFilter.shouldNotFilter(...)`는 다음 경로를 스킵한다.

```java
return path.startsWith("/login")
    || path.startsWith("/signup")
    || path.startsWith("/refresh")
    || path.startsWith("/v3/api-docs")
    || path.startsWith("/swagger-ui")
    || path.startsWith("/oauth2/authorization")
    || path.startsWith("/login/oauth2");
```

따라서 OAuth2 시작 요청과 OAuth2 Callback 요청은 JWT 검증을 받지 않는다.

```text
/oauth2/authorization/google
-> JWT 검증 스킵
-> OAuth2AuthorizationRequestRedirectFilter가 처리

/login/oauth2/code/google
-> JWT 검증 스킵
-> OAuth2LoginAuthenticationFilter가 처리
```

반대로 OAuth2 로그인이 끝난 뒤의 Protected API 요청은 `JwtAuthenticationFilter`가 서비스 Access Token을 검증한다.

### 13.2 Protected API에서 JwtAuthenticationFilter가 하는 일

Protected API 요청에서 `JwtAuthenticationFilter`는 다음 순서로 동작한다.

```text
1. Authorization header에서 Bearer Access Token 추출
2. JWT 서명과 만료 검증
3. TokenLifecycleService.isAccessTokenAllowed(...)로 Logout Blacklist 확인
4. JWT claims에서 subject(username) 추출
5. AuthenticatedUserService.findActiveUserByJwtSubject(username) 호출
6. enabled/accountNonLocked 상태 확인
7. UsernamePasswordAuthenticationToken 생성
8. SecurityContextHolder에 Authentication 저장
9. 다음 필터로 진행
```

코드 흐름은 다음과 같다.

```java
String token =
    tokenDeliveryService.resolveBearerAccessToken(request)
        .orElse(null);

if (token != null && jwtTokenProvider.validateToken(token)) {
    if (!tokenLifecycleService.isAccessTokenAllowed(token)) {
        filterChain.doFilter(request, response);
        return;
    }

    Claims claims = jwtTokenProvider.parseClaims(token);
    String username = claims.getSubject();

    AuthenticatedUser authenticatedUser =
        authenticatedUserService.findActiveUserByJwtSubject(username)
            .orElse(null);

    if (authenticatedUser == null) {
        filterChain.doFilter(request, response);
        return;
    }

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            authenticatedUser,
            null,
            authenticatedUser.getAuthorities()
        );

    SecurityContextHolder.getContext().setAuthentication(authentication);
}
```

여기서 사용자 상태 확인이 중요하다. Access Token의 서명과 만료가 유효해도 User가 disabled이거나 Account Lock 상태라면 인증 객체를 만들지 않는다.

이 정책 때문에 OAuth2 User도 Protected API에서는 Local User와 같은 `Authenticated User` 상태 검사를 받는다.

## 14. 필터 순서를 읽는 방법

정확한 필터 전체 순서는 Spring Security 설정과 버전에 따라 달라질 수 있다. 하지만 이 흐름을 이해할 때 중요한 상대적 위치는 다음이다.

```text
SecurityContextHolderFilter
HeaderWriterFilter
LogoutFilter
OAuth2AuthorizationRequestRedirectFilter
OAuth2LoginAuthenticationFilter
ExceptionHandlerFilter          // 프로젝트 커스텀
JwtAuthenticationFilter         // 프로젝트 커스텀
UsernamePasswordAuthenticationFilter
AnonymousAuthenticationFilter
ExceptionTranslationFilter
AuthorizationFilter
```

핵심은 다음이다.

```text
/oauth2/authorization/google
-> OAuth2AuthorizationRequestRedirectFilter가 처리
-> Google로 redirect
-> return으로 종료

/login/oauth2/code/google?code=...&state=...
-> OAuth2AuthorizationRequestRedirectFilter는 매칭 안 됨
-> OAuth2LoginAuthenticationFilter가 처리
-> 성공하면 OAuth2AuthenticationSuccessHandler 실행
-> 서비스 JWT 발급
-> frontend redirect

Protected API
-> OAuth2 필터의 시작/callback 경로가 아님
-> JwtAuthenticationFilter가 Bearer Access Token 검증
-> SecurityContext 구성
-> AuthorizationFilter가 Role 기반 인가 판단
```

또 하나 중요한 점은 `ExceptionHandlerFilter`와 `JwtAuthenticationFilter`가 프로젝트에서 직접 추가한 필터라는 것이다.

```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(exceptionHandlerFilter, JwtAuthenticationFilter.class)
```

이 필터들은 OAuth2 시작/콜백 경로에서는 `shouldNotFilter(...)` 때문에 핵심 처리를 하지 않는다. OAuth2가 끝난 뒤 Protected API에서 서비스 JWT 인증을 담당한다.

## 15. 현재 Token Delivery 방식과 한계

현재 OAuth2 성공 핸들러는 다음 두 가지 전달을 동시에 수행한다.

```text
Refresh Token
-> HttpOnly cookie

Access Token
-> frontend redirect URL fragment
```

현재 redirect 형태는 다음이다.

```text
http://localhost:3000/oauth2/callback#accessToken=...
```

URL fragment(`#...`)는 브라우저가 서버로 HTTP 요청을 보낼 때 전송하지 않는다. 그래서 query parameter보다 서버 로그에 남을 가능성은 낮다.

하지만 fragment도 안전한 최종 구조는 아니다.

노출 가능 표면은 다음과 같다.

```text
브라우저 주소창
브라우저 히스토리
프론트엔드 코드
XSS
브라우저 확장 프로그램
사용자의 URL 복사/공유
클라이언트 측 로깅
```

따라서 ADR 0004와 ADR 0005는 fragment 기반 Access Token redirect를 임시 방식으로 본다.

## 16. 개선 방향: Refresh Bootstrap Delivery

현재 ADR 기준의 목표 구조는 OAuth2 성공 후 Access Token을 URL에 싣지 않는 것이다.

더 정확히는 Access Token, Refresh Token, 임시 교환 코드 중 어느 것도 URL에 싣지 않는 방향이다.

흐름은 다음과 같다.

```text
1. OAuth2 성공
2. 백엔드가 서비스 Refresh Token을 HttpOnly cookie로 설정
3. 백엔드가 프론트엔드 root 또는 callback 화면으로 redirect
   - URL에는 Access Token 없음
   - URL에는 Refresh Token 없음
   - URL에는 temporary code 없음
4. React 앱 초기화
5. AuthProvider/AppBootstrap이 POST /refresh 호출
6. 브라우저가 Refresh Token cookie를 자동 전송
7. 백엔드가 Refresh Token 검증
8. Refresh Token Rotation 수행
9. 새 Access Token을 response body로 반환
10. 프론트엔드는 Access Token을 memory에만 저장
```

성공 핸들러는 개념적으로 다음 형태가 된다.

```java
TokenResponseDto tokenResponse = tokenLifecycleService.issue(
    authenticatedUser.getJwtSubject(),
    roles
);

tokenDeliveryService.addRefreshTokenCookie(
    response,
    tokenResponse.getRefreshToken()
);

getRedirectStrategy().sendRedirect(
    request,
    response,
    "http://localhost:3000/"
);
```

프론트엔드는 앱 시작 시 다음 요청을 보낸다.

```text
POST /refresh
credentials: include
```

이 방식의 장점은 다음이다.

```text
URL에 Access Token이 노출되지 않음
Refresh Token은 JavaScript가 읽을 수 없는 HttpOnly cookie에 남음
새로고침 후에도 /refresh로 Access Token을 복구 가능
Access Token은 브라우저 영속 저장소가 아니라 memory에만 저장 가능
```

주의할 점도 있다.

```text
OAuth2 성공 직후 /refresh를 한 번 더 호출하므로 Refresh Token Rotation이 한 번 더 발생할 수 있음
cookie 기반 요청이므로 CSRF 정책을 함께 점검해야 함
SameSite, Secure 같은 cookie 속성을 운영 환경에 맞게 조정해야 함
```

## 17. One-time Code Exchange와의 관계

기존 메모에는 One-time Code Exchange도 개선 방향으로 언급되어 있다.

흐름은 다음과 같다.

```text
1. OAuth2 성공
2. backend가 짧은 수명의 one-time code 생성
3. Refresh Token은 HttpOnly cookie로 설정
4. frontend로 redirect
   http://localhost:3000/oauth2/callback?code=...
5. frontend가 backend에 code 교환 요청
6. backend가 code를 1회 소비하고 Access Token을 response body로 반환
7. frontend는 Access Token을 memory에만 저장
```

이 방식도 Access Token을 URL에 직접 싣지 않는다는 장점이 있다. 대신 one-time code 저장소, TTL, consume-and-delete 구현이 필요하다.

현재 ADR 0004/0005의 목표는 Refresh Bootstrap Delivery다. 즉 OAuth2 성공 후 Refresh Token cookie만 설정하고, 프론트엔드가 `/refresh`로 Access Token을 복구하는 방향이다.

정리하면 다음과 같다.

```text
현재 코드
-> Refresh Token cookie + Access Token URL fragment

ADR 기준 목표
-> Refresh Token cookie + frontend bootstrap POST /refresh
-> URL에 Access Token, Refresh Token, temporary code 없음

가능한 대안
-> One-time Code Exchange
-> URL에 Access Token 대신 1회용 code만 노출
```

보안상 핵심은 Access Token을 URL에 싣지 않는 것이다.

## 18. 실패나 오해가 자주 생기는 지점

### 18.1 `/oauth2/authorization/google`은 API 요청이 아니다

이 요청은 우리 서버가 JSON 응답을 반환하는 API 요청이 아니다. Google 로그인 페이지로 보내는 redirect 응답을 만드는 요청이다.

그래서 `OAuth2AuthorizationRequestRedirectFilter`가 처리한 뒤 `return`으로 끝난다.

### 18.2 `/login/oauth2/code/google`은 프론트엔드 callback이 아니다

이 경로는 Google이 백엔드로 보내는 OAuth2 Callback이다. React 라우트가 처리하는 화면이 아니다.

프론트엔드 완료 페이지는 현재 설정 기준 다음이다.

```text
http://localhost:3000/oauth2/callback
```

### 18.3 Google Access Token과 서비스 Access Token을 섞으면 안 된다

Google provider access token은 Google API용이다. 우리 서비스 Protected API의 인증 수단은 서비스 Access Token이다.

```text
Google provider access token
-> Google UserInfo API 조회용
-> OAuth2AuthorizedClient에 저장 가능
-> 우리 API 인증에는 사용하지 않음

서비스 Access Token
-> TokenLifecycleService.issue(...)가 발급
-> Authorization: Bearer 로 Protected API 인증
-> JwtAuthenticationFilter가 검증
```

### 18.4 Base64는 state 보안 자체가 아니다

Base64는 cookie-safe 문자열로 바꾸는 인코딩이다. 무결성 보장은 별도 서명이나 서버 저장소가 필요하다.

### 18.5 OAuth2 User도 Protected API에서는 서비스 User 상태 검사를 받는다

OAuth2 로그인 순간에는 `OAuth2User` principal이지만, 서비스 도메인에서는 `Authenticated User`로 연결된다. Protected API에서는 Bearer Access Token 기반으로 다시 인증되고, enabled와 Account Lock 상태를 확인한다.

## 19. 컴포넌트별 책임 정리

| 컴포넌트 | 책임 |
| --- | --- |
| `SecurityConfig` | OAuth2 Login, stateless session, 공개 경로, 인가 규칙, 커스텀 JWT 필터 등록 |
| `OAuth2AuthorizationRequestRedirectFilter` | `/oauth2/authorization/google` 요청을 Google authorization endpoint로 redirect |
| `DefaultOAuth2AuthorizationRequestResolver` | `/oauth2/authorization/{registrationId}` 패턴에서 `registrationId` 추출 |
| `CookieOAuth2AuthorizationRequestRepository` | OAuth2 authorization request와 `state`를 cookie에 저장하고 callback 때 복원 |
| `OAuth2LoginAuthenticationFilter` | `/login/oauth2/code/*` callback의 `code`, `state`를 인증 요청으로 변환 |
| `OAuth2LoginAuthenticationProvider` 계열 | authorization code를 provider access token으로 교환하고 UserInfo 조회 흐름 수행 |
| `CustomOAuth2UserService` | Google 사용자 정보를 서비스 User로 연결하거나 자동 가입 |
| `OAuth2UserInfoFactory`, `GoogleOAuth2UserInfo` | provider별 attribute 파싱 |
| `CustomOAuth2User` | Spring Security `OAuth2User`와 서비스 `AuthenticatedUser`를 연결하는 adapter |
| `OAuth2AuthenticationSuccessHandler` | OAuth2 성공 후 서비스 JWT 발급, Refresh Token cookie 설정, 프론트엔드 redirect |
| `OAuth2AuthenticationFailureHandler` | OAuth2 실패 후 state cookie 정리, error redirect |
| `TokenLifecycleServiceImpl` | Access/Refresh Token 발급, Redis Refresh Token 저장, Refresh Token Rotation, Logout Blacklist |
| `TokenDeliveryServiceImpl` | Refresh Token cookie 생성/만료, Bearer Access Token 추출 |
| `JwtAuthenticationFilter` | OAuth2 이후 Protected API 요청에서 서비스 Access Token 검증과 `SecurityContext` 구성 |
| `AuthenticatedUserServiceImpl` | JWT Subject로 활성 User 조회, enabled/account lock 상태 확인 |

## 20. 관련 파일

| 파일 | 확인할 내용 |
| --- | --- |
| `src/main/java/org/example/config/SecurityConfig.java` | OAuth2 Login 설정, 공개 경로, redirection endpoint, success/failure handler, JWT 필터 등록 |
| `src/main/java/org/example/security/oauth2/CookieOAuth2AuthorizationRequestRepository.java` | state cookie 저장/복원/삭제, Base64 직렬화 |
| `src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java` | Google 사용자 정보 조회 후 서비스 User 연결/자동 가입 |
| `src/main/java/org/example/security/oauth2/CustomOAuth2User.java` | `OAuth2User`와 `AuthenticatedUser` adapter |
| `src/main/java/org/example/security/oauth2/OAuth2AuthenticationSuccessHandler.java` | OAuth2 성공 후 `TokenLifecycleService.issue(...)`, Refresh Token cookie, frontend redirect |
| `src/main/java/org/example/security/oauth2/OAuth2AuthenticationFailureHandler.java` | OAuth2 실패 후 cookie 정리와 error redirect |
| `src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java` | OAuth2 경로 skip, Protected API Bearer Access Token 인증 |
| `src/main/java/org/example/security/token/TokenLifecycleServiceImpl.java` | 서비스 Access Token/Refresh Token 발급과 Redis Token Store 정책 |
| `src/main/java/org/example/security/token/delivery/TokenDeliveryServiceImpl.java` | Refresh Token cookie와 Authorization header 처리 |
| `src/main/resources/application.yml` | Google OAuth2 registration, frontend redirect URI |
| `docs/adr/0002-redis-token-store-for-refresh-and-logout-state.md` | Redis Token Store 결정 |
| `docs/adr/0003-username-as-jwt-subject-and-token-owner.md` | username을 JWT Subject와 Redis token owner로 쓰는 결정 |
| `docs/adr/0004-service-owned-jwt-after-oauth2-login.md` | OAuth2 성공 후 서비스 소유 JWT를 발급한다는 결정 |
| `docs/adr/0005-token-delivery-policy.md` | Access Token/Refresh Token 전달 정책 |
| `docs/adr/0010-cookie-backed-oauth2-state.md` | OAuth2 state를 session 대신 cookie에 저장하는 결정 |
