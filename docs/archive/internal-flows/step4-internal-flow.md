# 현재 로그인 동작 과정

핵심부터 말하면, 이 프로젝트에는 로그인 흐름이 2개 있습니다.

1. 일반 로그인: `POST /login`
2. OAuth2 로그인: `GET /oauth2/authorization/google`로 시작

그리고 이 둘은 시작 지점이 다릅니다.

- 일반 로그인은 `Controller`가 시작점입니다.
- OAuth2 로그인은 `Spring Security 필터`가 시작점입니다.

**가장 중요한 오해 정리**

`/login`은 Spring Security의 기본 form login이 아닙니다.  
[SecurityConfig.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:87) 에서 `.formLogin(AbstractHttpConfigurer::disable)` 되어 있어서, `UsernamePasswordAuthenticationFilter`가 `/login`을 처리하지 않습니다.

대신 [AuthController.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/controller/AuthController.java:48) 의 `@PostMapping("/login")`이 직접 받습니다.  
그 안에서 [AuthServiceImpl.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/service/AuthServiceImpl.java:39) 가 `AuthenticationManager.authenticate(...)`를 수동 호출해서 인증합니다.

**필터 체인에서 무슨 일이 먼저 일어나나**

현재 보안 설정상 순서는 대략 이렇습니다.

- `ExceptionHandlerFilter`
- `JwtAuthenticationFilter`
- Spring Security 기본 필터들
- OAuth2 관련 필터들도 여기에 포함

설정 위치는 [SecurityConfig.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:139) 입니다.

그런데 중요한 점은 [JwtAuthenticationFilter.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java:27) 의 `shouldNotFilter()` 때문에 아래 경로에서는 JWT 필터가 아예 동작하지 않는다는 겁니다.

- `/login`
- `/signup`
- `/refresh`
- `/oauth2/authorization/**`
- `/login/oauth2/**`

즉:
- `/login` 요청이 와도 JWT 필터는 스킵
- `/oauth2/authorization/google` 요청이 와도 JWT 필터는 스킵
- `/login/oauth2/code/google` 콜백이 와도 JWT 필터는 스킵

그래서 이 경로들에서는 JWT 인증이 아니라, 각자 자기 방식으로 진행됩니다.

**1. 일반 로그인 `/login` 흐름**

요청: `POST /login`

순서:
1. 요청이 필터 체인을 통과
2. `JwtAuthenticationFilter`는 `/login`이므로 스킵
3. [AuthController.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/controller/AuthController.java:48) 의 `login()` 실행
4. `authService.login(requestDto)` 호출
5. [AuthServiceImpl.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/service/AuthServiceImpl.java:41) 에서 `AuthenticationManager.authenticate(...)`
6. 이때 `CustomUserDetailsService.loadUserByUsername()`가 호출되어 PostgreSQL에서 사용자 조회
   - [CustomUserDetailsService.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/CustomUserDetailsService.java:23)
7. 비밀번호 검증까지 끝나면 인증 성공
8. `AuthServiceImpl`이 AT/RT 생성
9. Redis에 Refresh Token 저장
   - [AuthServiceImpl.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/service/AuthServiceImpl.java:56)
10. Controller가 RT를 쿠키로 내려줌
    - [AuthController.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/controller/AuthController.java:55)

정리:
- PostgreSQL: 로그인 시 사용자 조회
- Redis: 로그인 성공 후 RT 저장
- Controller `/login`: 이 흐름의 시작점

즉 일반 로그인에서는 "필터가 인증 완료 후 controller로 가는 구조"가 아니라, "controller가 service를 통해 인증을 수행하는 구조"입니다.

**2. OAuth2 로그인 흐름**

시작 요청: `GET /oauth2/authorization/google`

이 경로는 [SecurityConfig.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:117) 의 `.oauth2Login(...)` 때문에 Spring Security가 처리합니다.  
여기서는 Controller가 시작점이 아닙니다.

순서:
1. 브라우저가 `/oauth2/authorization/google` 요청
2. JWT 필터는 스킵
3. Spring Security의 OAuth2 시작 필터가 이 요청을 잡음
   - 개념적으로 `OAuth2AuthorizationRequestRedirectFilter`
4. Google authorization endpoint로 리다이렉트
5. 이때 OAuth2 state/authorization request를 쿠키에 저장
   - [SecurityConfig.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:120)
   - `CookieOAuth2AuthorizationRequestRepository`
6. 사용자가 Google 로그인 완료
7. Google이 `/login/oauth2/code/google` 로 callback
8. JWT 필터는 이 경로도 스킵
9. Spring Security의 OAuth2 로그인 처리 필터가 callback을 처리
   - 개념적으로 `OAuth2LoginAuthenticationFilter`
10. authorization code를 Google access token으로 교환
11. 그 access token으로 Google userinfo 조회
12. 그 시점에 [CustomOAuth2UserService.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java:51) 의 `loadUser()` 호출
13. 여기서 PostgreSQL 조회:
   - `provider + providerId` 기준 기존 사용자 조회
   - [CustomOAuth2UserService.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java:75)
14. 사용자가 처음이면 PostgreSQL에 새 `User` 저장
   - [CustomOAuth2UserService.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java:95)
   - [CustomOAuth2UserService.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java:115)
15. OAuth2 인증 성공 후 [OAuth2AuthenticationSuccessHandler.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/OAuth2AuthenticationSuccessHandler.java:32) 실행
16. 여기서 자체 JWT AT/RT 발급
17. Redis에 RT 저장
   - [OAuth2AuthenticationSuccessHandler.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/OAuth2AuthenticationSuccessHandler.java:50)
18. RT는 쿠키로 응답
19. AT는 프론트 redirect URI의 fragment로 전달
   - [OAuth2AuthenticationSuccessHandler.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/OAuth2AuthenticationSuccessHandler.java:67)

정리:
- PostgreSQL: OAuth2 user 조회, 최초 로그인 시 신규 회원 저장
- Redis: OAuth2 성공 후 RT 저장
- Controller `/login`: OAuth2 흐름에서는 아예 사용되지 않음

**3. 로그인 후 일반 API 호출 흐름**

로그인 이후 사용자가 `Authorization: Bearer <AT>`로 `/user/me` 같은 API를 호출하면 이때는 JWT 필터가 핵심입니다.

순서:
1. 요청 도착
2. `JwtAuthenticationFilter` 실행
3. Authorization 헤더에서 AT 추출
   - [JwtAuthenticationFilter.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java:40)
4. JWT 유효성 검사
5. Redis blacklist 확인
   - [JwtAuthenticationFilter.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java:43)
6. blacklist 아니면 username 추출
7. `CustomUserDetailsService.loadUserByUsername()`로 PostgreSQL에서 사용자 재조회
   - [JwtAuthenticationFilter.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java:50)
8. `SecurityContext`에 인증 주입
9. 그 다음에야 controller 진입

즉, 로그인 후 보호 API에서는:
- Redis: blacklist 확인
- PostgreSQL: 사용자 정보 재조회
- controller는 SecurityContext가 채워진 뒤 실행

**DB 2개를 언제 쓰는가**

PostgreSQL:
- 일반 로그인 시 사용자 조회
- OAuth2 로그인 시 기존 사용자 조회
- OAuth2 최초 로그인 시 신규 사용자 저장
- refresh 시 권한 재조회
- JWT 인증된 일반 API 요청 시 사용자 재조회

Redis:
- 일반 로그인 성공 시 RT 저장
- OAuth2 로그인 성공 시 RT 저장
- refresh 시 저장된 RT 조회 및 새 RT로 갱신
- logout 시 RT 삭제
- logout 시 AT blacklist 저장
- 이후 API 요청 시 blacklist 확인

**한 줄로 구조를 정리하면**

- `/login`은 controller가 인증을 시작한다.
- `/oauth2/authorization/google`은 Spring Security OAuth2 필터가 인증을 시작한다.
- PostgreSQL은 사용자 원본 저장소다.
- Redis는 토큰 저장소다.
- 로그인 후 일반 API에서는 JWT 필터가 Redis와 PostgreSQL을 모두 참조해 인증을 복원한다.



› Oauth2 로그인 관련해서 질문이 있어

  1. 5번 내용이 무슨 말이지 ? 이때 OAuth2 state/authorization request를 쿠키에 저장
     - [SecurityConfig.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/
  main/java/org/example/config/SecurityConfig.java:120)
     - `CookieOAuth2AuthorizationRequestRepository`

  2. 그리고 Oauth2 로그인 관련해서 SpringSecurity에서 자동으로 만들어버리는 endpoint는
  뭐지??

  1. 그리고 한 번 로그인 완료하고 , docker의 볼륨 다 삭제(redis , postgresql) 그리고
  front 측의 쿠키도 다 삭제했는데 왜 다시 로그인 하면 바로 또 재로그인을 안하게 되는거
  지??

  java_test=# select * from users;
   id | account_non_locked | email | enabled | nickname | password | provider |
  provider_id |           username
  ----+--------------------+-------+---------+----------+----------+----------+-------
  ------+------------------------------
    1 | t                  |       | t       | 강기호   |          | LOCAL    |
  | GOOGLE_113274200417408424821
  (1 row)


1. `state/authorization request를 쿠키에 저장`한다는 뜻

이건 "OAuth2 로그인 시작 시점의 임시 문맥"을 저장한다는 말입니다.

사용자가 `/oauth2/authorization/google`로 들어오면 Spring Security는 바로 Google로 보내기 전에 `OAuth2AuthorizationRequest` 객체를 하나 만듭니다. 여기에 보통 이런 정보가 들어 있습니다.

- 어느 provider로 가는지
- redirect URI가 무엇인지
- scope가 무엇인지
- `state` 값
- 필요하면 PKCE 관련 값

이 프로젝트는 세션을 안 쓰기 때문에([SecurityConfig.java:91](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:91)), 그 임시 정보를 세션 대신 쿠키에 저장합니다([SecurityConfig.java:120](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:120)).

실제 저장소 구현은 [CookieOAuth2AuthorizationRequestRepository.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CookieOAuth2AuthorizationRequestRepository.java:67) 입니다.

흐름은 이렇습니다.

1. `/oauth2/authorization/google` 요청
2. Spring Security가 `OAuth2AuthorizationRequest` 생성
3. 그 안의 `state` 등을 `oauth2_auth_request` 쿠키에 저장
4. Google 로그인 후 `/login/oauth2/code/google?code=...&state=...` 로 돌아옴
5. Spring Security가 쿠키에서 옛 요청을 꺼냄
6. 콜백의 `state`와 쿠키의 `state`를 비교
7. 같으면 정상 진행, 다르면 거부

즉 이 쿠키는 "이 callback이 내가 방금 시작한 로그인 흐름의 응답이 맞는지" 확인하려고 씁니다.  
로그인 완료 후에는 성공/실패 핸들러에서 지웁니다([OAuth2AuthenticationSuccessHandler.java:62](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/OAuth2AuthenticationSuccessHandler.java:62), [OAuth2AuthenticationFailureHandler.java:49](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/OAuth2AuthenticationFailureHandler.java:49)).

2. Spring Security가 자동으로 처리하는 OAuth2 endpoint

이 프로젝트에서 사실상 자동으로 잡히는 endpoint는 2개입니다.

- `/oauth2/authorization/{registrationId}`
- `/login/oauth2/code/{registrationId}`

여기서는 Google만 있으니 실제로는:

- `/oauth2/authorization/google`
- `/login/oauth2/code/google`

첫 번째는 "OAuth2 로그인 시작" endpoint입니다.  
두 번째는 "Google이 authorization code를 돌려주는 callback" endpoint입니다.

이건 controller에 `@GetMapping`으로 만든 게 아니라, `.oauth2Login(...)` 설정 때문에 Spring Security 필터가 처리합니다([SecurityConfig.java:117](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:117), [SecurityConfig.java:124](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:124)).

반대로 `/login`은 자동 endpoint가 아닙니다. 이건 직접 만든 controller endpoint입니다([AuthController.java:48](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/controller/AuthController.java:48)).


4. Redis/PostgreSQL/프론트 쿠키를 다 지웠는데 왜 다시 로그인 입력을 안 하느냐

그건 대부분 Google 쪽 세션이 살아 있어서 그렇습니다.

지운 것:
- Redis: 우리 서비스의 RT 저장소
- PostgreSQL: 우리 서비스의 사용자 정보
- 프론트 쿠키: 우리 서비스 도메인의 쿠키

안 지운 것:
- `accounts.google.com` 같은 Google 도메인의 로그인 세션 쿠키

OAuth2 로그인은 "우리 서비스 로그인" 이전에 "Google에 이미 로그인돼 있느냐"가 먼저입니다.  
브라우저에 Google 세션이 살아 있으면, `/oauth2/authorization/google`로 갔을 때 Google이 다시 비밀번호를 안 물어보고 바로 인증 성공시킬 수 있습니다. 그러면 우리 서버는 callback을 받고, DB가 비어 있으면 새 user를 다시 자동 생성합니다([CustomOAuth2UserService.java:75](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java:75)).

즉 "다 지웠는데도 다시 바로 들어간다"는 건 보통 버그가 아니라 SSO 동작입니다.

테스트할 때 완전히 다시 로그인 화면을 보고 싶으면:

- 시크릿 창에서 테스트
- 브라우저에서 Google 로그아웃 후 테스트
- 또는 Google 계정 선택/재인증을 강제하는 파라미터를 추가

지금 프로젝트에는 `prompt=login`이나 `prompt=select_account` 같은 강제 재인증 설정은 없습니다. 그래서 Google 세션이 살아 있으면 자동으로 넘어갈 수 있습니다.

짧게 정리하면:

- `state` 쿠키는 OAuth2 로그인 시작 문맥을 저장하는 임시 보관소입니다.
- 자동 endpoint는 `/oauth2/authorization/google`, `/login/oauth2/code/google` 입니다.
- `provider=LOCAL` row는 현재 OAuth2 자동 가입 코드와 안 맞아서, 오래된 데이터거나 LOCAL 가입 경로 데이터일 가능성이 높습니다.
- 다시 비밀번호 안 치는 건 우리 서비스가 아니라 Google 세션이 살아 있어서입니다.
