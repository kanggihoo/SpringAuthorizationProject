# Step 3: Google OAuth2 + Docker Compose 도입 계획

## Context

Step 2의 JWT 무상태 인증에 Google OAuth2 로그인을 추가한다.
OAuth2 성공 후에도 기존과 동일하게 우리 서버의 JWT(AT/RT)를 발급하여 두 방식을 완전히 호환시킨다.
다중 Provider 확장성을 위해 OCP를 준수하는 추상화 레이어를 설계한다.
기존 Supabase DB를 Docker Compose(PostgreSQL 17-alpine + Redis)로 교체한다.

---

## 1. Docker Compose 설정

**생성: `docker-compose.yml` (프로젝트 루트)**

- `postgres:17-alpine`: port 5432, DB명 `java_test`, named volume
- `redis:alpine`: port 6379, named volume

---

## 2. application.yml 변경

- **datasource**: Supabase → `localhost:5432/java_test`
- **Google OAuth2 등록**:
  ```yaml
  spring.security.oauth2.client.registration.google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    scope: profile, email
  ```
- **프론트 리다이렉트 URI** 추가:
  ```yaml
  app.oauth2.redirect-uri: http://localhost:3000/oauth2/callback
  ```

---

## 3. AuthProvider Enum 신규 생성

**`src/main/java/org/example/domain/entity/AuthProvider.java`**

```java
public enum AuthProvider {
    LOCAL, GOOGLE
    // 향후 추가: GITHUB, KAKAO, NAVER
}
```
---

## 4. User 엔티티 확장

**`src/main/java/org/example/domain/entity/User.java`**

추가 필드:

- `provider` (`@Enumerated(EnumType.STRING)`, default `AuthProvider.LOCAL`)
- `providerId` (String, nullable) — Google `sub` claim
- `email` (String, nullable) — 향후 계정 연동용
- `password` → `@Column(nullable = true)` 변경 (OAuth2 유저는 password 없음)

OAuth2 유저의 username 규칙: `"GOOGLE_" + providerId` (LOCAL 유저와 충돌 방지)

신규 Builder 생성:

```java
@Builder(builderMethodName = "oauthBuilder")
public User(String username, String nickname, String email, AuthProvider provider, String providerId) { ... }
```

---

## 5. UserRepository 확장

**`src/main/java/org/example/repository/UserRepository.java`**

```java
Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
Optional<User> findByEmail(String email); // 향후 계정 연동용 (현재는 저장만)
```

---

## 6. OAuth2 추상화 레이어 (OCP 설계) — `security/oauth2` 패키지

### A. `OAuth2UserInfo.java` (interface)

제공자마다 다른 응답 attribute를 통일된 인터페이스로 추상화.

```java
public interface OAuth2UserInfo {
    String getProviderId();   // Google: "sub", GitHub: "id"
    String getEmail();
    String getName();
}
```

### B. `GoogleOAuth2UserInfo.java`

`OAuth2UserInfo` 구현체. Google 전용 attribute 파싱 캡슐화.

```java
// attributes.get("sub"), attributes.get("email"), attributes.get("name")
```

향후 GitHub 추가 시 `GithubOAuth2UserInfo`만 새로 추가하면 됨 (기존 코드 수정 불필요).

### C. `OAuth2UserInfoFactory.java`

`registrationId` → 적합한 `OAuth2UserInfo` 구현체 반환.

```java
public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
    return switch (registrationId.toLowerCase()) {
        case "google" -> new GoogleOAuth2UserInfo(attributes);
        // 향후: case "github" -> new GithubOAuth2UserInfo(attributes);
        default -> throw new IllegalArgumentException("Unsupported provider: " + registrationId);
    };
}
```

### D. `CustomOAuth2User.java`

`OAuth2User` 구현. Google `OAuth2User` + 우리 DB `User` 엔티티를 함께 보관.
SuccessHandler에서 User 엔티티(id, username)에 접근하기 위해 필요.

### E. `CustomOAuth2UserService.java`

`DefaultOAuth2UserService` 확장. **Provider별 분기 없음** — Factory에 위임.

`loadUser()` 흐름:

1. `super.loadUser()`로 Google에서 사용자 정보 수신
2. `OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, attributes)` 호출
3. `findByProviderAndProviderId(GOOGLE, providerId)` DB 조회
4. 없으면 자동 회원가입 (`ROLE_USER` 부여)
5. `CustomOAuth2User` 반환

### F. `CookieOAuth2AuthorizationRequestRepository.java` ← **CSRF/State 방어 핵심**

`AuthorizationRequestRepository<OAuth2AuthorizationRequest>` 구현.

**왜 필요한가?**
우리 앱은 `SessionCreationPolicy.STATELESS`이므로 Spring의 기본 저장소
(`HttpSessionOAuth2AuthorizationRequestRepository`)가 동작하지 않는다.
세션이 없으면 state 파라미터를 저장할 곳이 없어 CSRF 방어가 무력화된다.

**동작 방식**:

- `saveAuthorizationRequest()`: OAuth2 인증 요청 시 state를 포함한 요청 객체를 직렬화(Base64)하여 **HttpOnly 쿠키**에 저장
- `loadAuthorizationRequest()` / `removeAuthorizationRequest()`: 콜백 시 쿠키에서 꺼내 역직렬화
- Spring의 `OAuth2LoginAuthenticationFilter`가 콜백 도착 후 이 저장소에서 기존 요청을 조회하여 **state 값 일치 여부를 자동 검증** → CSRF 방어

쿠키 속성: HttpOnly, Secure(운영), Path=/oauth2, SameSite=Lax, 단기 TTL(5분)

### G. `OAuth2AuthenticationSuccessHandler.java`

`SimpleUrlAuthenticationSuccessHandler` 확장.

흐름:

1. `CustomOAuth2User`에서 `User` 추출
2. `JwtTokenProvider`로 AT + RT 생성 (기존과 동일)
3. `RefreshTokenRepository` upsert (기존 방식과 동일)
4. RT를 HttpOnly 쿠키로 설정
5. **`CookieOAuth2AuthorizationRequestRepository`의 OAuth2 state 쿠키 삭제** (정리)
6. **`{redirect-uri}#accessToken={AT}` 로 리다이렉트 (Fragment 사용)**
   - Query param(`?`)과 달리 Fragment(`#`)는 서버 로그 / Referer 헤더에 포함되지 않아 보안상 개선됨

### H. `OAuth2AuthenticationFailureHandler.java`

`SimpleUrlAuthenticationFailureHandler` 확장.
OAuth2 state 쿠키 삭제 후 `{redirect-uri}?error={message}` 로 리다이렉트.

---

## 7. SecurityConfig 수정

**`src/main/java/org/example/config/SecurityConfig.java`**

1. `CustomOAuth2UserService`, `OAuth2AuthenticationSuccessHandler`, `OAuth2AuthenticationFailureHandler`, `CookieOAuth2AuthorizationRequestRepository` 주입
2. `permitAll()` 경로에 `/oauth2/**`, `/login/oauth2/**` 추가
3. `.oauth2Login()` 추가:

   ```java
   .oauth2Login(oauth2 -> oauth2
       .authorizationEndpoint(ep ->
           ep.authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository))
       .redirectionEndpoint(ep -> ep.baseUri("/login/oauth2/code/*"))
       .userInfoEndpoint(ep -> ep.userService(customOAuth2UserService))
       .successHandler(oAuth2AuthenticationSuccessHandler)
       .failureHandler(oAuth2AuthenticationFailureHandler)
   )
   ```

   - `authorizationEndpoint`에 `CookieOAuth2AuthorizationRequestRepository`를 등록함으로써
     state 저장/검증이 세션 대신 쿠키 기반으로 동작하게 됨

---

## 8. JwtAuthenticationFilter shouldNotFilter 확장

**`src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java`**

```java
|| path.startsWith("/oauth2/authorization")
|| path.startsWith("/login/oauth2")
```

---

## 코드 작성 원칙

- **모든 클래스, 메서드, 필드에 한국어 주석 작성** (클래스 수준 JavaDoc + 핵심 로직 인라인 주석)
- 기존 코드의 주석 스타일 유지

---

## 구현 순서

1. `docker-compose.yml` 생성
2. `AuthProvider.java` Enum 생성
3. `User.java` 엔티티 필드 추가
4. `UserRepository.java` 메서드 추가
5. `application.yml` 업데이트
6. `OAuth2UserInfo.java` (interface) 생성
7. `GoogleOAuth2UserInfo.java` 생성
8. `OAuth2UserInfoFactory.java` 생성
9. `CustomOAuth2User.java` 생성
10. `CustomOAuth2UserService.java` 생성
11. `CookieOAuth2AuthorizationRequestRepository.java` 생성
12. `OAuth2AuthenticationSuccessHandler.java` 생성
13. `OAuth2AuthenticationFailureHandler.java` 생성
14. `SecurityConfig.java` oauth2Login 추가
15. `JwtAuthenticationFilter.java` shouldNotFilter 확장

---

## 검증 방법

1. `docker-compose up -d` → 앱 기동, Hibernate DDL 정상 적용 확인
2. `http://localhost:8080/oauth2/authorization/google` 접속 → Google 동의 화면
3. 동의 후 `localhost:3000/oauth2/callback#accessToken=...` 리다이렉트 확인
4. 발급된 AT로 `GET /user/profile` → 200 OK 확인
5. DB `users` 테이블에 `provider='GOOGLE'` 레코드 생성 확인
6. 동일 계정 재로그인 시 중복 생성 없이 기존 유저 재활용 확인
7. 기존 `POST /login` 정상 동작 확인 (regression 없음)

---

## 향후 개선 사항 (이번 범위 外)

### 1. OAuth2 AT 전달 방식 보안 강화 (가장 중요)

현재: Fragment(`#accessToken=...`) → 브라우저 히스토리에는 여전히 남을 수 있음.
**개선 방향**: 일회성 단기 코드(Authorization Code) 교환 패턴

- SuccessHandler에서 단기 코드(TTL 30초) 발급 후 `?code=...` 리다이렉트
- 프론트가 `POST /oauth2/token?code=...` 호출 → AT(JSON) + RT(Cookie) 수신
- 코드는 1회 사용 후 즉시 무효화 (Redis 활용)

### 2. 계정 연동을 위한 Identity 테이블 분리 (1:N)

현재: User 엔티티에 provider/providerId 직접 포함 (1:1 구조, 단일 OAuth2 연동만 가능).
**개선 방향**: `UserOAuthAccount` 테이블 분리

```
users (1) ←── (N) user_oauth_accounts (provider, provider_id, user_id FK)
```

- 하나의 계정에 Google + GitHub + Kakao 동시 연동 가능
- 연동/해제 API 구현 가능

### 3. 추가 Provider 등록 시 작업 범위

현재 설계(OCP 준수) 덕분에 필요한 작업:

- `OAuth2UserInfoFactory`에 case 1줄 추가
- 신규 `XxxOAuth2UserInfo.java` 파일 1개 추가
- `AuthProvider` Enum에 값 1개 추가
- `application.yml`에 provider 등록 정보 추가
  기존 코드 수정 없음.

## 구현 완료

### 생성된 파일

| 파일                                                                                                                                                                                                               | 역할                                       |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------ |
| [docker-compose.yml](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/docker-compose.yml)                                                                                                     | PostgreSQL 17-alpine + Redis 컨테이너 정의 |
| [AuthProvider.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/domain/entity/AuthProvider.java)                                                               | 인증 제공자 Enum (LOCAL, GOOGLE)           |
| [OAuth2UserInfo.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/security/oauth2/OAuth2UserInfo.java)                                                         | 제공자별 사용자 정보 파싱 추상 인터페이스  |
| [GoogleOAuth2UserInfo.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/security/oauth2/GoogleOAuth2UserInfo.java)                                             | Google 전용 attribute 파싱 구현체          |
| [OAuth2UserInfoFactory.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/security/oauth2/OAuth2UserInfoFactory.java)                                           | registrationId → 파서 팩토리               |
| [CustomOAuth2User.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/security/oauth2/CustomOAuth2User.java)                                                     | OAuth2User + User 엔티티 래퍼              |
| [CustomOAuth2UserService.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java)                                       | OAuth2 사용자 로드 + 자동 회원가입         |
| [CookieOAuth2AuthorizationRequestRepository.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/security/oauth2/CookieOAuth2AuthorizationRequestRepository.java) | CSRF/State 방어 — state를 쿠키에 저장      |
| [OAuth2AuthenticationSuccessHandler.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/security/oauth2/OAuth2AuthenticationSuccessHandler.java)                 | JWT 발급 + `#accessToken=...` 리다이렉트   |
| [OAuth2AuthenticationFailureHandler.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/security/oauth2/OAuth2AuthenticationFailureHandler.java)                 | 실패 시 `?error=...` 리다이렉트            |

### 수정된 파일

| 파일                                                                                                                                                                      | 변경 내용                                                      |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| [application.yml](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/resources/application.yml)                                               | Supabase → Docker DB, Redis 활성화, OAuth2 + redirect-uri 추가 |
| [User.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/domain/entity/User.java)                                      | provider, providerId, email 필드 추가, password nullable       |
| [UserRepository.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/repository/UserRepository.java)                     | `findByProviderAndProviderId`, `findByEmail` 추가              |
| [SecurityConfig.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/config/SecurityConfig.java)                         | `.oauth2Login()` 설정 추가                                     |
| [JwtAuthenticationFilter.java](vscode-webview://1ripseoqandfvfll75n8l3nbb3orgufg4u5jg0kif006tefnigfq/src/main/java/org/example/security/jwt/JwtAuthenticationFilter.java) | OAuth2 경로 skip 추가                                          |

### 실행 방법

```bash
# 1. Docker 컨테이너 시작
docker-compose up -d
```

# 1. Docker 컨테이너 시작

docker-compose up -d

# 2. 환경변수 설정 후 앱 실행

export GOOGLE_CLIENT_ID=your-client-id
export GOOGLE_CLIENT_SECRET=your-client-secret
./gradlew bootRun

# 3. Google Cloud Console에서 Authorized redirect URI 등록 필요

# http://localhost:8080/login/oauth2/code/google
