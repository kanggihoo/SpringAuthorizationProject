

# [SecurityConfig.java](src/main/java/org/example/config/SecurityConfig.java)

## Q : 2개의 JWT , 예외 처리 관련한 필터의 위치가 적합한 위치인지?? 
현재 위치는 의도 자체는 적합해.

```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(exceptionHandlerFilter, JwtAuthenticationFilter.class);
```

실제 순서는:

```text
ExceptionHandlerFilter
-> JwtAuthenticationFilter
-> UsernamePasswordAuthenticationFilter 위치 근처의 Spring Security 기본 필터들
-> AuthorizationFilter 쪽에서 권한 판단
```

JWT 인증 필터는 `AuthorizationFilter`보다 먼저 실행되어야 해. 그래야 `/admin/**`, `/user/**` 권한 판단 시점에 이미 `SecurityContext`에 인증 객체가 들어가 있으니까. `UsernamePasswordAuthenticationFilter` 앞에 두는 건 JWT 커스텀 필터에서 흔히 쓰는 위치고, form login을 꺼도 “필터 순서 기준점”으로는 사용할 수 있어.

`ExceptionHandlerFilter`를 JWT 필터 앞에 둔 것도 맞아. JWT 검증 중 `JwtException`, Redis 장애 예외, 기타 인증 처리 예외가 터지면 이 필터가 잡아서 전역 예외 처리기로 넘길 수 있기 때문이야.

다만 한 가지 주의점은 있어. `authorizeHttpRequests().permitAll()`은 “인가 단계에서 허용”한다는 뜻이지, 커스텀 JWT 필터 실행 자체를 자동으로 막지는 않아. 그래서 [JwtAuthenticationFilter.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java:31)의 `shouldNotFilter`가 별도로 공개 경로를 스킵하고 있어. 현재는 `/`, `/error`가 `permitAll`에는 있지만 `shouldNotFilter`에는 없어서, 그 경로에 잘못된 Bearer 토큰을 붙이면 JWT 필터가 반응할 수 있다. 큰 구조는 맞지만, 공개 경로 목록을 두 곳에서 관리하는 점은 나중에 개선 포인트로 볼 수 있어.

[JwtAuthenticationFilter](src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java) 이 `shouldNotFilter`는 **JwtAuthenticationFilter 자체를 실행하지 않을 경로를 정하는 장치**야.

`SecurityConfig`의 `permitAll()`은 “이 URL은 최종 인가 단계에서 인증 없이 허용한다”는 뜻이고, 커스텀 필터인 `JwtAuthenticationFilter`의 실행을 자동으로 막아주지는 않아. 그래서 JWT 필터 내부에서 공개 경로는 직접 제외하고 있는 거야.

예를 들면:

```text
POST /login
-> shouldNotFilter() == true
-> JwtAuthenticationFilter 실행 안 함
-> AuthController.login()에서 username/password 인증 처리
```

반대로:

```text
GET /user/me
-> shouldNotFilter() == false
-> JwtAuthenticationFilter 실행
-> Bearer Access Token 검증
-> SecurityContext에 Authentication 저장
-> /user/** 권한 규칙 검사
```

즉 이 코드는 “로그인, 회원가입, refresh, Swagger, OAuth2 시작/콜백 같은 공개 또는 별도 인증 흐름 경로에서는 JWT 검사하지 말자”는 의미야.

> 다만 현재 기준으로는 `SecurityConfig`의 `permitAll()` 목록과 `shouldNotFilter()` 목록이 완전히 같지는 않아. 예를 들어 > `"/"`와 `"/error"`는 `permitAll()`에는 있지만 `shouldNotFilter()`에는 없어. 그래서 나중에 구조를 다듬는다면 공개 경로 > 매칭 기준을 한 곳에서 관리하거나, 최소한 두 목록을 맞추는 게 좋다.


# [AuthController](src/main/java/org/example/controller/AuthController.java)

## 로그아웃 흐름 

로그아웃은 네 말대로 “로그인 상태여야 한다”가 맞아. 하지만 중요한 점은 **프론트에서 버튼이 보이는지 여부는 보안 조건이 아니다**라는 거야. 실제 보안 조건은 서버의 `SecurityConfig`다.

현재 `/logout`은 `permitAll()`에 없으므로:

```text
POST /logout
-> JwtAuthenticationFilter 실행
-> Authorization: Bearer <Access Token> 검증
-> SecurityContext에 Authentication 저장
-> anyRequest().authenticated() 통과
-> AuthController.logout() 실행
```

그래서 정상 운영 흐름에서는 `/logout`에 도달했다는 것 자체가 “이미 인증됨”에 가깝다.

`@AuthenticationPrincipal AuthenticatedUser userDetails`는 `SecurityContext` 안의 `Authentication.getPrincipal()`을 컨트롤러 파라미터로 꺼내주는 기능이야. 현재 JWT 필터에서는 여기서 principal을 넣고 있어.

```java
UsernamePasswordAuthenticationToken authentication =
    new UsernamePasswordAuthenticationToken(
        authenticatedUser,
        null,
        authenticatedUser.getAuthorities());

SecurityContextHolder.getContext().setAuthentication(authentication);
```

그럼 컨트롤러에서는 이걸 받는다.

```java
@AuthenticationPrincipal AuthenticatedUser userDetails
```

`userDetails`가 null이 될 수 있냐는 질문에는, **현재 SecurityConfig 기준의 정상 요청에서는 거의 null이 되면 안 된다**가 답이야. 토큰이 없거나 유효하지 않으면 `/logout`은 `anyRequest().authenticated()`에서 막혀서 컨트롤러까지 오지 못해야 해.

그래도 코드에 `if (userDetails != null)`이 있는 이유는 방어적 코드에 가깝다. 예를 들어 테스트 환경, 보안 설정 변경, `/logout`이 나중에 `permitAll()`로 바뀌는 경우, 혹은 principal 타입이 `AuthenticatedUser`가 아닌 경우에는 null이 될 수 있어.

그리고 마지막 부분은 살짝 보정이 필요해.

```java
String accessToken = tokenDeliveryService.resolveBearerAccessToken(request).orElse(null);
authService.logout(userDetails.getJwtSubject(), accessToken);
```

여기서 컨트롤러는 **토큰을 검증하는 게 아니라 Header에서 추출만 해**. 검증은 이미 앞단의 `JwtAuthenticationFilter`에서 끝났다고 보는 게 맞아. 이후 `authService.logout()`은 그 Access Token을 Redis blacklist에 등록하고, 사용자 subject 기준으로 Refresh Token을 삭제하는 로그아웃 정책을 수행한다.

로그아웃 핵심 흐름은 이렇게 보면 돼.

```text
유효한 Access Token으로 /logout 요청
-> JWT 필터가 인증 처리
-> 컨트롤러가 현재 사용자와 Access Token 추출
-> Redis에서 RT 삭제
-> AT를 blacklist에 등록
-> finally에서 Refresh Token cookie 만료
```

`finally`에 쿠키 만료가 있는 것도 중요해. Redis 쪽 로그아웃 처리 중 예외가 나더라도 브라우저의 Refresh Token 쿠키는 제거하려는 의도야.


# AuthServiceImpl
## login 메서드 
네 이해 방향이 맞아. 다만 핵심 보정은 이거야.

```java
new UsernamePasswordAuthenticationToken(username, password)
```

이건 “인증이 끝난 객체”가 아니라 **인증 요청 객체**야. 아직 사용자가 맞는지 모른다. 이 객체를 `AuthenticationManager`에게 넘기면, Spring Security가 실제 인증을 수행하고 **인증 완료된 `Authentication`**을 반환한다.

흐름은 이렇게 보면 돼.

```text
AuthController.login()
-> AuthServiceImpl.login()
-> username/password로 UsernamePasswordAuthenticationToken 생성
-> authenticationManager.authenticate(...)
-> CustomUserDetailsService가 DB에서 User 조회
-> PasswordEncoder로 비밀번호 검증
-> enabled / accountNonLocked 등 UserDetails 상태 검사
-> 인증 성공 시 Authentication 반환
-> principal에서 AuthenticatedUser 추출
-> authorities에서 ROLE 목록 추출
-> TokenLifecycleService.issue()로 JWT 발급
```

**질문 1: 왜 AuthenticationManager를 주입받나?**

`AuthenticationManager`는 Spring Security 인증의 진입점이야. `AuthServiceImpl`이 직접 DB 조회, 비밀번호 비교, 계정 잠금 확인을 다 하지 않고, Spring Security의 표준 인증 파이프라인에 맡기는 구조다.

`AuthenticationManager`는 인터페이스가 맞아. 하지만 실제 런타임에는 Spring이 구현체를 빈으로 만들어서 주입해준다. 보통 내부 구현체는 `ProviderManager`이고, 이 객체가 여러 `AuthenticationProvider` 중에서 현재 인증 요청을 처리할 수 있는 provider를 찾는다.

여기서는 요청 타입이 `UsernamePasswordAuthenticationToken`이므로 대략 이런 provider가 처리한다.

```text
AuthenticationManager
-> ProviderManager
-> DaoAuthenticationProvider
-> CustomUserDetailsService.loadUserByUsername(username)
-> PasswordEncoder.matches(rawPassword, encodedPassword)
```

이 연결이 가능한 이유는 프로젝트에 다음 두 가지가 있기 때문이야.

- [CustomUserDetailsService.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/CustomUserDetailsService.java) 가 `UserDetailsService` 구현체임
- [SecurityConfig.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:57) 에 `PasswordEncoder` 빈이 있음

그리고 [SecurityConfig.java](C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java:65) 에서 이걸 꺼내서 빈으로 노출하고 있어.

```java
@Bean
public AuthenticationManager authenticationManager(
    AuthenticationConfiguration configuration) throws Exception {
  return configuration.getAuthenticationManager();
}
```

즉 “구현체 없이 인터페이스 메서드를 호출”하는 게 아니라, Spring DI가 실제 구현체를 주입해준 상태에서 인터페이스 타입으로 사용하는 거야.

**추가 질문1-1 : 우리는이 AuthenticationManager 구현체인 ProviderManager나 DaoAuthenticationProvider 클래스를 정의하지 않은거 같은데 그냥 스프링이 자동으로 뭔가 알아서 처리하는건가? 근데 그러면 이때 이 메서드 authenticate 호출시 입력으로 전달되는 Token 타입에 따라서 적절한 구현체 클래스인(AuthenticationManager의 구현체)가 동작되어서 관련 있는 Provider한테 전달하는구조인가?**

맞아. 현재 프로젝트에서는 `ProviderManager`나 `DaoAuthenticationProvider`를 직접 `new` 하거나 `@Bean`으로 등록하지 않았지만, Spring Security 설정 인프라가 구성해준다.

현재 연결은 대략 이렇게 잡혀 있어.

```text
SecurityConfig.authenticationManager(...)
-> AuthenticationConfiguration.getAuthenticationManager()
-> Spring Security가 구성한 AuthenticationManager 반환
-> 보통 내부 구현체는 ProviderManager
-> ProviderManager 안에 username/password 인증용 Provider가 들어감
-> 그 Provider가 CustomUserDetailsService + PasswordEncoder 사용
```

공식 문서 기준으로도 `AuthenticationManager`의 가장 흔한 구현체가 `ProviderManager`이고, `ProviderManager`는 여러 `AuthenticationProvider`에게 인증 처리를 위임한다. 또 `DaoAuthenticationProvider`는 `UserDetailsService`와 `PasswordEncoder`를 사용해서 username/password를 인증한다. 참고: [Spring Security Authentication Architecture](https://docs.enterprise.spring.io/spring-security/reference/servlet/authentication/architecture.html), [DaoAuthenticationProvider](https://docs.enterprise.spring.io/spring-security/reference/5.8/servlet/authentication/passwords/dao-authentication-provider.html)

네가 말한 “입력으로 전달되는 Token 타입에 따라 적절한 provider가 동작한다”는 이해도 맞아. 조금 더 정확히 말하면:

```text
authenticationManager.authenticate(authentication)
-> ProviderManager가 내부 AuthenticationProvider 목록을 순회
-> provider.supports(authentication.getClass()) 확인
-> 처리 가능한 provider가 authenticate(authentication) 수행
-> 성공하면 인증 완료 Authentication 반환
-> 실패하면 AuthenticationException
-> 처리 가능한 provider가 없으면 ProviderNotFoundException
```

현재 `AuthServiceImpl`에서 넘기는 타입은:

```java
new UsernamePasswordAuthenticationToken(username, password)
```

그래서 username/password 인증을 처리할 수 있는 `DaoAuthenticationProvider` 계열 provider가 처리하게 된다.

이 프로젝트에서 그 provider가 사용할 재료는 이미 등록돼 있어.

```text
CustomUserDetailsService
-> username으로 DB에서 User 조회
-> CustomUserDetails 반환

PasswordEncoder
-> BCryptPasswordEncoder
-> 입력 password와 DB의 encoded password 비교
```

즉 우리는 `DaoAuthenticationProvider`를 직접 작성하지 않았지만, Spring Security가 “`UserDetailsService` 빈이 있고 `PasswordEncoder` 빈이 있네. username/password 인증 구성이 가능하네”라고 보고 인증 관리자 쪽에 연결해주는 구조로 보면 된다.

단, 중요한 구분이 있어.

`AuthenticationManager` 구현체가 “토큰 타입에 따라 바뀐다”기보다는, 보통은 **하나의 `ProviderManager`가 여러 provider를 들고 있고, provider들이 자신이 처리 가능한 `Authentication` 타입인지 판단한다**에 가까워.

예시로 보면:

```text
UsernamePasswordAuthenticationToken
-> DaoAuthenticationProvider가 처리

JwtAuthenticationToken
-> JwtAuthenticationProvider가 처리할 수 있음

OAuth2LoginAuthenticationToken
-> OAuth2LoginAuthenticationProvider가 처리할 수 있음
```

현재 `AuthServiceImpl.login()`은 `UsernamePasswordAuthenticationToken`만 만들기 때문에, 이 서비스 메서드는 LOCAL username/password 로그인 경로만 담당한다고 보면 돼. OAuth2는 이 메서드로 들어오지 않고 Spring Security OAuth2 필터/핸들러 쪽 흐름을 탄다.

---

**질문 2: 로그인 시 SecurityContext에는 언제 들어가나?**

현재 `AuthServiceImpl.login()`에서는 **SecurityContext에 넣지 않아.**

이 프로젝트의 일반 로그인은 form login이 아니라 REST API 로그인이다. 그래서 로그인 요청 자체에서는 인증 성공 후 JWT만 발급하고 끝난다.

```text
POST /login
-> username/password 검증
-> JWT 발급
-> 응답으로 Access Token + Refresh Token 전달
-> SecurityContext에 저장하지 않음
```

그다음 요청부터는 프론트가 Access Token을 들고 온다.

```text
GET /user/...
Authorization: Bearer <access-token>
-> JwtAuthenticationFilter가 토큰 검증
-> AuthenticatedUser 조회
-> SecurityContextHolder에 Authentication 저장
-> /user/** 권한 검사 통과
```

즉 이 프로젝트에서 `SecurityContext`에 들어가는 시점은 일반 로그인 성공 시점이 아니라, **Access Token을 들고 보호 API에 접근할 때 `JwtAuthenticationFilter` 안에서**야.

정리하면:

```text
/login 단계:
username/password -> AuthenticationManager -> 인증 성공 -> JWT 발급

보호 API 단계:
/user/** 요청 -> JWT 필터 -> JWT 검증 -> SecurityContext 저장 -> 인가 검사
```

Spring Security 기본 form login을 썼다면 `UsernamePasswordAuthenticationFilter`가 로그인 성공 후 `SecurityContext`에 넣고 세션에도 저장할 수 있어. 하지만 이 프로젝트는 form login과 session을 끄고 JWT stateless 구조를 쓰기 때문에, 로그인 성공 결과를 서버 세션에 저장하지 않는 게 현재 설계와 맞아.

### SpringSecurity에서의 동작 flow

맞아. 헷갈리는 지점이 딱 그 “중간다리”야. 결론부터 말하면:

```text
AuthServiceImpl
-> AuthenticationManager.authenticate(...)
-> ProviderManager
-> DaoAuthenticationProvider
-> CustomUserDetailsService.loadUserByUsername(...)
```

여기서 `DaoAuthenticationProvider`가 우리의 `CustomUserDetailsService`를 호출하는 직접 지점은 보통 이런 메서드야.

```java
protected final UserDetails retrieveUser(
    String username,
    UsernamePasswordAuthenticationToken authentication
) throws AuthenticationException {
    UserDetails loadedUser = this.getUserDetailsService().loadUserByUsername(username);
    return loadedUser;
}
```

즉 `DaoAuthenticationProvider`는 username/password 인증을 할 때 내부적으로:

```java
userDetailsService.loadUserByUsername(username)
```

을 호출한다. 그래서 우리가 구현한 이 코드가 실행되는 거야.

```java
@Override
@Transactional(readOnly = true)
public UserDetails loadUserByUsername(String username) {
  User user = userRepository.findByUsername(username)
      .orElseThrow(() -> new UsernameNotFoundException(...));

  return new CustomUserDetails(user);
}
```

그 다음 `DaoAuthenticationProvider`는 반환받은 `UserDetails`를 가지고 비밀번호를 비교한다.

```text
요청에서 받은 raw password
vs
UserDetails.getPassword()의 encoded password
```

비교는 `PasswordEncoder.matches(rawPassword, encodedPassword)`로 한다.

전체 흐름을 조금 더 내부 구조에 가깝게 쓰면:

```text
1. AuthServiceImpl이 UsernamePasswordAuthenticationToken 생성
   principal = username
   credentials = raw password

2. AuthenticationManager.authenticate(token) 호출

3. 실제 구현체 ProviderManager가 provider 목록 순회

4. DaoAuthenticationProvider가
   supports(UsernamePasswordAuthenticationToken.class) == true
   라서 이 요청을 처리

5. DaoAuthenticationProvider가 username 추출

6. userDetailsService.loadUserByUsername(username) 호출
   -> 여기서 CustomUserDetailsService 실행
   -> DB에서 User 조회
   -> CustomUserDetails 반환

7. DaoAuthenticationProvider가 UserDetails 상태 검사
   -> enabled
   -> accountNonLocked
   -> accountNonExpired
   -> credentialsNonExpired

8. PasswordEncoder.matches(rawPassword, userDetails.getPassword()) 실행

9. 성공하면 인증 완료된 Authentication 반환
   principal = CustomUserDetails
   credentials = 보통 제거됨
   authorities = ROLE_USER, ROLE_ADMIN 등
```

그래서 `UserDetailsService`와 `DaoAuthenticationProvider`의 관계는 이렇게 보면 돼.

```text
DaoAuthenticationProvider:
  "username/password 인증 절차를 아는 Spring Security 기본 Provider"

UserDetailsService:
  "username으로 우리 서비스의 사용자를 찾아오는 어댑터"

CustomUserDetails:
  "우리 User 엔티티를 Spring Security가 이해하는 형태로 감싼 객체"
```

Spring Security 문서도 `DaoAuthenticationProvider`가 `UserDetailsService`와 `PasswordEncoder`를 사용해 username/password를 인증한다고 설명해. 참고: [DaoAuthenticationProvider](https://docs.enterprise.spring.io/spring-security/reference/5.8/servlet/authentication/passwords/dao-authentication-provider.html), [Authentication Architecture](https://docs.enterprise.spring.io/spring-security/reference/servlet/authentication/architecture.html)

그리고 “어떻게 연결되냐”는 부분은 Spring 설정 인프라가 해준다. 이 프로젝트에는 `CustomUserDetailsService`가 `@Service`로 등록되어 있고, `BCryptPasswordEncoder`가 `PasswordEncoder` 빈으로 등록되어 있어. Spring Security는 이 두 빈을 보고 username/password 인증용 provider 구성에 사용할 수 있다.

직접 비유하면:

```text
DaoAuthenticationProvider는 엔진
UserDetailsService는 사용자 조회 플러그
PasswordEncoder는 비밀번호 비교 플러그
AuthenticationManager는 엔진들을 호출하는 입구
```

우리는 엔진을 새로 만든 게 아니라, 엔진이 사용할 “사용자 조회 방식”과 “비밀번호 비교 방식”을 제공한 거야.


### 어떻게 DaoAuthenticationProvider와 userDetils.Service가 연결이 되는가?
**원본 코드 안에 호출이 있다.** 여기야.

```java
@Override
protected final UserDetails retrieveUser(
    String username,
    UsernamePasswordAuthenticationToken authentication
) throws AuthenticationException {
    prepareTimingAttackProtection();
    try {
        UserDetails loadedUser = this.getUserDetailsService().loadUserByUsername(username);
        ...
        return loadedUser;
    }
    ...
}
```

이 줄:

```java
this.getUserDetailsService().loadUserByUsername(username);
```

여기서 우리의 `CustomUserDetailsService.loadUserByUsername(username)`가 호출돼.

다만 헷갈릴 수 있는 이유는 `DaoAuthenticationProvider.authenticate(...)` 메서드가 이 클래스에 직접 보이지 않기 때문이야. `DaoAuthenticationProvider`는 `AbstractUserDetailsAuthenticationProvider`를 상속하고 있고, 실제 전체 인증 흐름의 뼈대는 부모 클래스에 있어.

구조는 이렇게 돼.

```text
ProviderManager.authenticate(authentication)
-> DaoAuthenticationProvider.authenticate(authentication)
   이 메서드는 부모 AbstractUserDetailsAuthenticationProvider에 있음
-> 부모 authenticate() 내부에서 retrieveUser(username, authentication) 호출
-> retrieveUser()는 DaoAuthenticationProvider가 override한 메서드
-> DaoAuthenticationProvider.retrieveUser()
-> getUserDetailsService().loadUserByUsername(username)
-> CustomUserDetailsService.loadUserByUsername(username)
```

즉 Template Method 패턴이야.

부모 클래스가 전체 순서를 갖고 있고:

```text
supports 타입 확인
username 추출
retrieveUser 호출
pre checks
additionalAuthenticationChecks 호출
post checks
성공 Authentication 생성
```

자식인 `DaoAuthenticationProvider`가 구체 단계만 구현해.

네가 붙여준 코드 기준으로 자식이 담당하는 핵심은 두 개야.

```java
retrieveUser(...)
```

여기서 `UserDetailsService` 호출.

```java
additionalAuthenticationChecks(...)
```

여기서 비밀번호 비교.

```java
this.passwordEncoder.get().matches(presentedPassword, userDetails.getPassword())
```

그래서 흐름은 정확히 이렇게 보면 돼.

```text
AuthServiceImpl.login()
-> authenticationManager.authenticate(UsernamePasswordAuthenticationToken)

ProviderManager
-> DaoAuthenticationProvider 선택

AbstractUserDetailsAuthenticationProvider.authenticate()
-> DaoAuthenticationProvider.retrieveUser()
   -> CustomUserDetailsService.loadUserByUsername()

-> DaoAuthenticationProvider.additionalAuthenticationChecks()
   -> PasswordEncoder.matches()

-> DaoAuthenticationProvider.createSuccessAuthentication()
-> 인증 완료 Authentication 반환
```

너가 “DaoAuthenticationProvider 안에 userDetailsService 호출이 없는 것 같다”고 느낀 건 아마 `authenticate()` 메서드를 찾고 있었기 때문일 거야. 호출은 직접 `authenticate()` 안에 있는 게 아니라, 부모의 `authenticate()`가 자식의 `retrieveUser()`를 호출하고, 그 `retrieveUser()` 안에서 `UserDetailsService`를 호출하는 구조야.


## TokenLifecycleServiceImpl 
Token Lifecycle 정책의 중심 서비스야.

issue()
-> AT/RT 발급
-> RT를 Redis Token Store에 저장
-> 컨트롤러가 사용할 TokenResponseDto 반환

rotate()
-> RT 파싱
-> Redis에 저장된 현재 RT와 비교
-> 다르면 재사용/탈취 의심으로 거절
-> 같으면 새 AT/RT 발급
-> 새 RT로 Redis 값 교체

logout()
-> Redis의 현재 RT 삭제
-> 현재 AT를 남은 만료 시간만큼 blacklist 등록

isAccessTokenAllowed()
-> JWT 필터가 blacklist 여부 확인할 때 사용


**질문 1: @JsonIgnore 의미와 RT 반환**
@JsonIgnore는 Jackson이 객체를 JSON으로 직렬화할 때 해당 필드를 빼라는 의미야.
현재 TokenResponseDto.java (line 25)에:

```java
@JsonIgnore
private String refreshToken;
```

이 있으니까 Java 코드 내부에서는 getRefreshToken()으로 접근 가능하지만, HTTP response body JSON에는 안 나간다.
즉 현재 설계는 이거야.

```text
TokenLifecycleService.issue()
-> TokenResponseDto 안에 AT, RT 둘 다 담음

AuthController.login()
-> tokenResponse.getRefreshToken()으로 RT를 꺼내 쿠키에 저장
-> ResponseEntity.ok(tokenResponse)

Jackson JSON 변환
-> @JsonIgnore 때문에 refreshToken은 body에서 제외
-> body에는 accessToken, tokenType만 나감
```

그래서 “RT를 최종 응답 body에 같이 담는 것 아닌가?”라는 의심은 좋은데, 현재는 @JsonIgnore 덕분에 body에는 안 나가는 구조야.


# [JwtAuthenticationFilter](src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java)

전체 이해는 좋고, 한 군데는 꼭 보정해야 해.

`JwtAuthenticationFilter`에서 만든 `UsernamePasswordAuthenticationToken`은 **AuthenticationManager에게 다시 전달하려는 객체가 아니야.** 이 시점에는 이미 JWT 검증이 끝났고, 필터가 직접 “이 요청은 인증된 사용자다”라고 `SecurityContext`에 넣는 거야.

즉 여기서는:

```text
UsernamePasswordAuthenticationToken
-> Provider 선택용 인증 요청 객체가 아니라
-> SecurityContext에 저장할 인증 완료 객체
```

로 쓰인다.

```java
new UsernamePasswordAuthenticationToken(
    authenticatedUser,
    null,
    authenticatedUser.getAuthorities()
)
```

이 생성자는 authorities가 들어가므로 Spring Security에서 “인증 완료된 Authentication”으로 취급돼. 이후 `/admin/**`, `/user/**` 인가 판단은 이 객체의 `getAuthorities()`를 보고 결정한다.

**질문 1: TokenDeliveryService 책임**

`TokenDeliveryService`는 토큰이 HTTP에서 오고 가는 방식을 담당하는 어댑터야.

주요 책임은 이런 것들이다.

```text
Authorization 헤더에서 Bearer Access Token 추출
Refresh Token 쿠키 읽기
Refresh Token 쿠키 추가
Refresh Token 쿠키 만료
```

즉 JWT를 생성하거나 검증하는 서비스가 아니야. 그것은 `JwtTokenProvider` 책임이고, RT 저장/회전/블랙리스트 정책은 `TokenLifecycleService` 책임이야.

`TokenDeliveryService`는 “토큰을 HTTP 요청/응답 어디에서 꺼내고 어디에 실을 것인가”를 담당한다.

```text
Access Token
-> Authorization: Bearer xxx

Refresh Token
-> HttpOnly Cookie
```

**질문 2: token이 null이면 왜 그냥 doFilter 하나?**

토큰이 없다는 것만으로 이 필터가 바로 401을 응답하지 않는 이유는, 최종 접근 허용/거부는 뒤쪽 Spring Security 인가 단계가 결정하게 두기 위해서야.

예를 들어:

```text
GET /public 같은 허용 경로
-> 토큰 없어도 통과 가능해야 함

GET /user/me
-> 토큰 없음
-> JwtAuthenticationFilter는 인증 객체를 만들지 않음
-> 뒤쪽 AuthorizationFilter에서 authenticated() 조건 실패
-> 401
```

즉 JWT 필터의 책임은 “토큰이 있으면 인증 시도”이지, “모든 인증 필요 여부를 판단”하는 게 아니야. URL별 접근 정책은 `SecurityConfig.authorizeHttpRequests()`가 담당한다.

**질문 3: doFilter 후 return 의미**

이 코드지.

```java
if (!tokenLifecycleService.isAccessTokenAllowed(token)) {
  filterChain.doFilter(request, response);
  return;
}
```

의미는:

```text
토큰은 문법적으로 유효하지만 blacklist에 있음
-> SecurityContext에 인증 객체를 넣지 않음
-> 다음 필터로 넘김
-> 그 뒤 인가 단계에서 인증 없음으로 판단되어 401
-> 현재 JWT 필터는 여기서 더 진행하지 않음
```

`return`이 없으면 아래 코드가 계속 실행돼.

```java
Claims claims = jwtTokenProvider.parseClaims(token);
...
SecurityContextHolder.getContext().setAuthentication(authentication);
```

그러면 blacklist 토큰인데도 인증 객체가 들어갈 수 있어. 그래서 `return`은 “이 요청은 더 이상 인증 처리하지 말고 끝낸다”는 의미로 필요해.

같은 이유로 `authenticatedUser == null`일 때도:

```java
filterChain.doFilter(request, response);
return;
```

을 한다. 유저가 disabled/locked거나 없으면 SecurityContext에 넣으면 안 되니까.

**질문 4: findActiveUserByJwtSubject는 뭐 하고, 매번 DB 조회하나?**

맞아. 현재 구조에서는 보호 API 요청마다 DB 조회가 발생한다.

흐름은 이거야.

```text
JWT에서 subject(username) 추출
-> AuthenticatedUserService.findActiveUserByJwtSubject(username)
-> CustomUserDetailsService.loadUserByUsername(username)
-> UserRepository.findByUsername(username)
-> CustomUserDetails 생성
-> enabled/accountNonLocked 확인
-> 활성 사용자면 SecurityContext에 넣음
```

왜 DB를 조회하냐면, JWT 안의 정보만 믿으면 “토큰 발급 이후 바뀐 사용자 상태”를 반영할 수 없기 때문이야.

예를 들어 사용자가 로그인해서 유효한 Access Token을 받았다고 하자.

```text
10:00 로그인, Access Token 발급
10:05 관리자가 계정 잠금 처리
10:06 사용자가 기존 Access Token으로 /user/me 요청
```

JWT만 검증하면 이 토큰은 아직 만료 전이라 통과할 수 있어. 그런데 이 프로젝트의 도메인 정책은 “Account Lock은 기존 Access Token도 막아야 한다”야. 그래서 매 요청마다 현재 User 상태를 다시 확인한다.

이건 `CONTEXT.md`의 핵심 정책과도 맞아.

```text
Account Lock must invalidate Protected API access even when the caller still has an unexpired Access Token.
An unexpired Access Token must not bypass the Authenticated User state check.
```

그래서 DB 조회는 비용은 있지만 보안 정책을 만족시키기 위한 의도적인 선택이야.

요약하면:

```text
JWT 검증
-> 토큰이 서버가 발급한 유효한 토큰인지 확인

Redis blacklist 확인
-> 로그아웃된 Access Token인지 확인

DB 사용자 상태 확인
-> User가 여전히 enabled이고 accountNonLocked인지 확인

SecurityContext 저장
-> 이후 Spring Security 인가 규칙이 사용할 인증 객체 제공
```

이 필터의 핵심 역할은 “JWT만 맞으면 통과”가 아니라, **토큰 상태 + 사용자 현재 상태까지 확인해서 이번 요청의 인증 객체를 만드는 것**이야.

## JwtAuthenticationFilter 동작 방식 

토큰이 없거나, blacklist거나, User가 locked/disabled라서 JwtAuthenticationFilter가 Authentication을 만들지 않고 doFilter를 호출하는경우에는 SecurityContext 비어 있음
-> 다음 필터들 계속 진행
-> AuthorizationFilter 쪽에서 현재 요청의 인가 규칙 확인
-> /user/** 또는 anyRequest().authenticated()인데 인증 없음
-> 401 Unauthorized

맞아. 정확히 이해하고 있어.

토큰이 없거나, blacklist거나, User가 locked/disabled라서 `JwtAuthenticationFilter`가 `Authentication`을 만들지 않으면:

```text
SecurityContext 비어 있음
-> 다음 필터들 계속 진행
-> AuthorizationFilter 쪽에서 현재 요청의 인가 규칙 확인
-> /user/** 또는 anyRequest().authenticated()인데 인증 없음
-> 401 Unauthorized
```

즉 `JwtAuthenticationFilter`가 직접 “401”을 응답하지 않고, **인증 객체를 만들지 않은 상태로 뒤로 넘기는 방식**이야. 그러면 Spring Security의 인가 단계가 최종적으로 막는다.

다만 “다른 필터에서도 이걸 처리 못한다”는 표현은 현재 구조에서는 맞아. form login, http basic을 꺼놨고, 보호 API 요청에 대해 JWT 외에 다른 인증 수단을 쓰지 않으니까, JWT 필터가 `SecurityContext`를 채우지 않으면 결국 인증 없음으로 끝난다.

JWT 검증 중 에러가 나는 경우도 네 설명이 맞아.

현재 필터 순서는:

```text
ExceptionHandlerFilter
-> JwtAuthenticationFilter
-> ...
```

그래서 `JwtAuthenticationFilter` 안에서:

```java
jwtTokenProvider.validateToken(token)
```

이 호출되고, 여기서 만료/변조/잘못된 JWT 때문에 `JwtException` 계열 예외가 터지면:

```text
JwtAuthenticationFilter에서 예외 발생
-> filterChain.doFilter(...) 흐름이 중단됨
-> 예외가 호출 스택을 타고 앞쪽으로 전파됨
-> 앞에 있던 ExceptionHandlerFilter의 catch(Exception e)에 잡힘
-> HandlerExceptionResolver로 위임
-> GlobalExceptionHandler.handleJwtExceptions(...)
-> 401 응답 생성
```

흐름으로 쓰면:

```text
요청
-> ExceptionHandlerFilter.doFilterInternal()
   try {
     filterChain.doFilter(...)
       -> JwtAuthenticationFilter.doFilterInternal()
          -> jwtTokenProvider.validateToken()
          -> JwtException 발생
   } catch (Exception e) {
     resolver.resolveException(...)
   }
-> GlobalExceptionHandler
-> 401 JSON 응답
```

이 경우에는 **다음 필터로 넘어가지 않아.** 예외가 발생했으니까 `JwtAuthenticationFilter`의 아래 로직도 실행되지 않고, 뒤쪽 필터들도 실행되지 않는다.

정리하면 두 갈래야.

```text
토큰 없음 / blacklist / locked user
-> 예외 아님
-> SecurityContext 안 채움
-> 다음 필터로 넘김
-> AuthorizationFilter가 401/403 결정
```

```text
토큰 변조 / 만료 / 파싱 실패
-> 예외 발생
-> 다음 필터로 안 감
-> ExceptionHandlerFilter가 잡음
-> GlobalExceptionHandler가 401 응답
```

이 설계는 “인증 실패의 종류”를 다르게 취급하는 구조야.  
토큰이 없는 건 그냥 unauthenticated 상태로 넘기고, 토큰이 있는데 잘못된 건 예외로 보고 즉시 401 응답을 만든다.

## doFilter 메서드 이후에 return문? 

맞아. `filterChain.doFilter(request, response)`는 **다음 필터로 넘기는 호출**이야. 그런데 중요한 점은 “다음 필터로 넘긴다”가 “현재 메서드가 즉시 종료된다”는 뜻은 아니야.

자바 메서드 호출이라서 이렇게 동작해.

```java
System.out.println("before");
filterChain.doFilter(request, response);
System.out.println("after");
```

흐름은:

```text
before 출력
-> 다음 필터들 실행
-> 컨트롤러까지 갔다가 응답 처리
-> 다시 현재 필터로 돌아옴
-> after 출력
```

그래서 `doFilter()` 아래 코드도 실행될 수 있어. 다만 현재 코드에서는 이런 경우에 `return`을 붙였지.

```java
if (!tokenLifecycleService.isAccessTokenAllowed(token)) {
  filterChain.doFilter(request, response);
  return;
}
```

여기서 `return`이 없으면 다음 필터들이 끝난 뒤 다시 돌아와서 아래 JWT 인증 코드가 계속 실행될 수 있어. 그래서 blacklist 토큰이나 inactive user인 경우에는 `doFilter()` 후 바로 종료시키는 거야.

반대로 메서드 마지막의:

```java
filterChain.doFilter(request, response);
```

는 뒤에 실행할 코드가 없으니까 `return`이 없어도 실질적으로 끝난다.

## filter 내에서의 DB 접근 ?? 
DB 접근 질문은 좋은 의문이야. 결론부터 말하면 **필터에서 DB 접근을 하는 것은 가능하지만, 항상 좋은 설계는 아니다.** 다만 이 프로젝트에서는 의도된 보안 정책 때문에 하고 있어.

일반적인 JWT stateless 구조에서는 보통 이렇게 한다.

```text
JWT 서명 검증
-> 만료 확인
-> claims에서 username/roles 추출
-> DB 조회 없이 SecurityContext 생성
```

이 방식은 빠르다. 대신 단점이 있어.

```text
토큰 발급 후 사용자가 disabled/locked 되어도
Access Token이 만료될 때까지 계속 접근 가능
```

이 프로젝트는 그걸 허용하지 않으려는 설계야. `CONTEXT.md`에도 이런 정책이 있어.

```text
Account Lock must invalidate Protected API access even when the caller still has an unexpired Access Token.
An unexpired Access Token must not bypass the Authenticated User state check.
```

그래서 매 요청마다 DB에서 현재 사용자 상태를 확인한다.

```text
JWT는 유효함
-> 그래도 DB에서 User 현재 상태 확인
-> enabled=false 또는 accountNonLocked=false면 인증 처리하지 않음
```

블랙리스트와 비슷한 성격이긴 해. 둘 다 “JWT 자체는 유효하지만 현재 서버 상태상 허용하면 안 되는지”를 확인하는 서버 상태 조회야.

```text
Redis blacklist
-> 로그아웃된 Access Token인지 확인

DB User 조회
-> 계정이 현재 enabled/accountNonLocked 상태인지 확인
```

차이는 저장소와 의미야.

```text
Redis
-> 토큰 상태
-> 빠르고 TTL 기반
-> logout blacklist, refresh token store

DB
-> 사용자 상태
-> 계정 잠금, 비활성화, 권한 변경 같은 도메인 상태
```

그래서 현재 구조는 보안 정책 측면에서는 타당해. 다만 비용이 있다.

```text
보호 API 요청마다 Redis 조회 1번
보호 API 요청마다 DB 조회 1번
```

성능을 더 중요하게 보면 나중에 선택지가 있어.

```text
1. 짧은 Access Token TTL로 DB 조회 생략
2. User 상태를 Redis/cache에 저장
3. accountVersion/tokenVersion claim을 두고 변경 시 무효화
4. 중요한 API만 DB 재검증
```

하지만 지금 프로젝트의 목표가 “Account Lock이 기존 Access Token도 막는다는 evidence”라면, 필터에서 현재 User 상태를 확인하는 설계는 이해 가능한 선택이야.

```text
JWT
-> 세션 저장소 없이 사용자를 식별하고 토큰 서명/만료를 검증

Redis
-> Refresh Token 상태
-> 로그아웃된 Access Token blacklist 확인

DB
-> User가 현재 enabled/accountNonLocked인지 확인
-> 현재 Role 확인 일부 흐름에서 사용
```

맞아. 현재 구현은 “순수 stateless JWT로 DB 조회를 피한다”는 방향은 아니야.

현재 구조는 정확히 말하면:

```text
JWT
-> 세션 저장소 없이 사용자를 식별하고 토큰 서명/만료를 검증

Redis
-> Refresh Token 상태
-> 로그아웃된 Access Token blacklist 확인

DB
-> User가 현재 enabled/accountNonLocked인지 확인
-> 현재 Role 확인 일부 흐름에서 사용
```

그래서 보호 API 요청마다 대략:

```text
1. Authorization Header에서 AT 추출
2. JWT 서명/만료 검증
3. Redis에서 blacklist 확인
4. DB에서 현재 User 상태 확인
5. SecurityContext 생성
```

이렇게 돼. 네 말대로 JWT의 장점 중 하나인 “매 요청 DB 조회 회피”는 상당 부분 포기한 구조야.

다만 “어쩔 수 없다”보다는 **보안 정책 때문에 의도적으로 trade-off를 선택한 것**에 가까워.

이 프로젝트의 요구가:

```text
로그아웃한 Access Token은 즉시 차단
계정 잠금/비활성화는 기존 Access Token도 즉시 차단
Redis 장애 시 보안상 fail-closed
```

이면 서버 상태 조회가 필요해져. JWT 자체는 한 번 발급되면 만료 전까지 독립적으로 유효하니까, “발급 이후 바뀐 상태”를 반영하려면 어딘가를 조회해야 해.

정리하면 선택지는 이렇게 갈려.

```text
순수 stateless JWT
-> 빠름
-> DB/Redis 조회 거의 없음
-> 로그아웃/계정잠금 즉시 반영 어려움
-> 짧은 AT 만료시간으로 완화

현재 프로젝트 방식
-> 매 요청 Redis/DB 조회
-> 로그아웃/계정잠금 즉시 반영 가능
-> 보안 evidence 만들기 좋음
-> 성능 비용 있음
```

그래서 현재 코드는 “JWT로 서버 세션을 없앤다”는 목적은 달성하지만, “모든 서버 상태 조회를 없앤다”는 목적은 달성하지 않아.

더 정확히 표현하면:

```text
세션 기반 인증은 아니다.
하지만 완전한 stateless 인증도 아니다.
Redis/DB 상태를 섞은 stateful JWT 인증이다.
```

이 프로젝트가 보안 시나리오 증명용이면 타당한 선택이고, 대규모 트래픽 최적화가 목표면 나중에 구조를 바꿀 여지가 있어.


JwtTokenProvider
-> JWT 문자열 생성/검증/파싱
-> secret key, expiration, claims 처리

TokenLifecycleService
-> 언제 AT/RT를 발급할지
-> RT를 Redis에 저장할지
-> rotate/reuse/logout 정책

JwtAuthenticationFilter
-> HTTP 요청에서 Access Token을 받아 인증 객체 생성
-> SecurityContext에 Authentication 저장

TokenDeliveryService
-> Authorization header, Refresh Token cookie 처리

# RedisFailurePolicy 

`RedisFailurePolicy` 이해는 맞아. 이 클래스는 **Redis 작업 중 발생한 저장소 예외를 인증 도메인의 실패 코드로 바꾸는 역할**이야.

왜 이렇게 했냐면, `TokenLifecycleServiceImpl` 곳곳에서 Redis 장애 처리를 반복하지 않기 위해서야.

예를 들어 그냥 작성하면 매번 이렇게 해야 해.

```java
try {
  tokenRedisRepository.saveRefreshToken(...);
} catch (DataAccessException e) {
  throw new AuthFailureException(
      AuthFailureCode.TOKEN_STORE_UNAVAILABLE,
      "Token Store를 사용할 수 없습니다.",
      e);
}
```

이게 `save`, `find`, `delete`, `blacklist check`마다 반복되니까, 공통 정책으로 뺀 거야.

현재 코드는 Redis 작업을 함수처럼 넘겨받는다.

```java
redisFailurePolicy.requireAvailable(() ->
    tokenRedisRepository.saveRefreshToken(jwtSubject, refreshToken, ttl));
```

여기서 `() -> tokenRedisRepository.saveRefreshToken(...)` 이 부분이 `Runnable operation`이야.

```java
public void requireAvailable(Runnable operation) {
  try {
    operation.run();
  } catch (DataAccessException e) {
    throw tokenStoreUnavailable(e);
  }
}
```

의미는:

```text
Redis 작업 실행
-> 성공하면 끝
-> DataAccessException 발생하면 TOKEN_STORE_UNAVAILABLE 예외로 변환해서 던짐
```

`Runnable` 버전은 반환값이 없는 작업에 쓴다.

```text
saveRefreshToken()
deleteRefreshToken()
addToBlacklist()
```

반면 `Supplier<T>` 버전은 반환값이 있는 Redis 작업에 쓴다.

```java
public <T> T requireAvailable(Supplier<T> operation) {
  try {
    return operation.get();
  } catch (DataAccessException e) {
    throw tokenStoreUnavailable(e);
  }
}
```

예를 들면:

```java
String storedToken = redisFailurePolicy.requireAvailable(
    () -> tokenRedisRepository.findRefreshToken(jwtSubject)
)
```

여기서는 `findRefreshToken()`이 `Optional<String>`을 반환하니까 `Supplier<T>`가 필요해.

정리하면:

```text
Runnable
-> 반환값 없는 작업 감싸기

Supplier<T>
-> 반환값 있는 작업 감싸기
```

`DataAccessException`은 Spring의 데이터 접근 계층 예외야. Redis 연결 실패, timeout, command 실패 같은 게 Spring Data Redis에서 이 계열 예외로 올라올 수 있어.

예:

```text
Redis 서버 다운
Redis 연결 거부
Redis timeout
Redis command 실행 실패
```

그때 `TokenRedisRepository`에서 예외가 터지고, `requireAvailable()`이 잡는다.

마지막 질문:

```java
throw tokenStoreUnavailable(e);
```

이건 아래 메서드가 `AuthFailureException` 객체를 만들어서 반환하기 때문에 가능해.

```java
private AuthFailureException tokenStoreUnavailable(DataAccessException cause) {
  return new AuthFailureException(...);
}
```

즉 실제로는 이 코드와 같아.

```java
AuthFailureException exception = tokenStoreUnavailable(e);
throw exception;
```

`tokenStoreUnavailable(e)`가 예외를 “던지는” 게 아니라, **던질 예외 객체를 만들어서 반환**하고, 앞의 `throw`가 그 객체를 던지는 거야.

왜 `AuthFailureException`으로 바꾸냐면, 컨트롤러/전역 예외 처리에서 안정적인 응답 코드를 만들기 위해서야.

```text
DataAccessException
-> 기술 예외, HTTP 응답 정책 없음

AuthFailureException(TOKEN_STORE_UNAVAILABLE)
-> 인증 도메인 예외
-> GlobalExceptionHandler가 503으로 응답 가능
```

결과적으로 Redis가 죽으면 클라이언트에는 대략 이런 의미로 응답할 수 있어.

```text
503 Service Unavailable
code = TOKEN_STORE_UNAVAILABLE
message = Token Store를 사용할 수 없습니다...
```

이 프로젝트에서는 Redis가 인증 상태 저장소라서, Redis 장애를 그냥 무시하고 통과시키지 않고 **fail-closed**, 즉 안전하게 실패시키는 방향으로 잡은 거야.


맞아. 현재 `RedisFailurePolicy`를 거치는 Redis 저장소 장애는 대부분 `503 Service Unavailable`로 처리하겠다는 뜻이야.

```text
Redis 연결 실패
Redis timeout
Redis down
Redis command 실패
-> DataAccessException
-> AuthFailureException(TOKEN_STORE_UNAVAILABLE)
-> 503
```

그리고 네 말대로 **이 정책 클래스에서는 401을 만들지 않아.**

`RedisFailurePolicy`의 책임은 Redis 장애를:

```java
TOKEN_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE)
```

로 바꾸는 것뿐이야.

401은 다른 상황에서 발생해. 예를 들면 `TokenLifecycleServiceImpl.rotate()`에서 Redis 조회 자체는 성공했지만 저장된 RT가 없는 경우:

```java
.orElseThrow(() -> new AuthFailureException(
    AuthFailureCode.REFRESH_TOKEN_INVALID,
    "유효하지 않은 Refresh Token입니다. (만료 또는 미존재)"));
```

이건:

```text
Redis는 정상 동작함
하지만 RT가 없음
-> REFRESH_TOKEN_INVALID
-> 401 Unauthorized
```

또 저장된 RT와 요청 RT가 다르면:

```java
AuthFailureCode.REFRESH_TOKEN_REUSED
```

이것도 401이야.

그래서 구분은 이렇게 보면 돼.

```text
Redis 자체를 사용할 수 없음
-> TOKEN_STORE_UNAVAILABLE
-> 503

Redis는 정상인데 토큰 상태가 유효하지 않음
-> REFRESH_TOKEN_INVALID / REFRESH_TOKEN_REUSED
-> 401

Access Token이 변조/만료됨
-> JwtException
-> 401

Access Token이 없어서 보호 API 인증 실패
-> AuthenticationEntryPoint
-> 401
```

즉 RedisFailurePolicy는 “사용자 인증 실패”가 아니라 “서버의 Token Store 장애”를 표현하는 클래스라서 503이 맞아.