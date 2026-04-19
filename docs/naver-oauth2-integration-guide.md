# 네이버 OAuth2 연동 가이드 및 개념 정리 (Spring Security)

## 1. OAuth2 vs OIDC 동작 방식 비교 (추가 API 요청 유무)

*   **OIDC (OpenID Connect)의 특징**
    인증 서버로부터 Access Token을 발급받을 때, 유저의 신상 정보가 모두 포함된 **`id_token`**도 함께 발급받습니다. 토큰 자체에 유저 정보가 들어있으므로 백엔드 서버가 소셜 서버에 유저 정보를 조회하기 위한 **추가적인 API 호출을 할 필요가 없습니다.**
*   **일반 OAuth 2.0의 특징**
    발급받는 Access Token은 단순히 "문을 열 수 있는 열쇠"일 뿐, 그 안에 사용자 정보(이름, 이메일 등)가 들어있지 않습니다. 따라서 유저의 식별 정보를 얻기 위해서는 백엔드 서버가 해당 열쇠(Access Token)를 들고 소셜 서버의 User Info API로 **추가적인 HTTP 통신(API 요청)을 별도로 수행**해야만 합니다.

## 2. Spring Security의 강력함: 추가 API 요청의 은닉화(추상화)

OAuth 2.0 방식은 프로필 조회를 위해 "추가 API 요청"이 무조건 발생해야 합니다. 하지만 우리 프로젝트 코드에서는 `RestTemplate`이나 `WebClient` 등으로 네이버나 구글에 HTTP 통신을 보내는 코드를 직접 작성하지 않습니다. 
그 이유는 **Spring Security 프레임워크가 복잡한 통신 과정을 "알아서, 뒤에서" 처리**해주기 때문입니다.

### 📍 이 추가 API 요청은 도대체 '언제' 날아갈까요?

우리의 핵심 서비스 클래스인 `CustomOAuth2UserService.java` 코드를 보면 다음과 같은 부분이 있습니다.

```java
@Override
public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    
    // 🔥 바로 이 단 한줄 "super.loadUser"가 호출되는 순간에 일어납니다! 🔥
    OAuth2User oauth2User = super.loadUser(userRequest); 
    
    // ...
}
```

부모 클래스의 `super.loadUser(userRequest)`가 실행될 때, Spring Security는 내부적으로 다음 과정을 순식간에 자동 수행합니다.
1. 인자로 넘겨진 `userRequest`를 분석해 요청의 목적지(Google, Naver 등)와 Endpoint 주소를 파악합니다.
2. 획득해 둔 Access Token을 HTTP Header에 싣고 **실제 소셜 서버의 User Info API로 HTTP GET 통신**을 보냅니다.
3. 소셜 서버가 반환해준 JSON 데이터(유저 정보)를 파싱하여 자바 객체(`OAuth2User`)로 예쁘게 포장해서 우리 코드에 돌려줍니다.

### 🌍 목적지를 결정하는 방법: Google(자동) vs Naver(수동)
*   **Google**: 전 세계를 아우르는 글로벌 표준 플랫폼이므로, Spring Security 라이브러리 내부에 구글의 유저 정보 API 주소(`https://www.googleapis.com/oauth2/v3/userinfo`)가 기본값으로 셋팅되어 있습니다. 그래서 Client ID/Secret만 넣으면 알아서 통신이 이루어집니다.
*   **Naver**: 국내 전용 플랫폼이라 Spring Security가 이 사이트의 주소 스펙을 기본적으로 알지 못합니다. 따라서 **Spring Security가 `super.loadUser`를 호출할 때 어디로 통신(찌를지)해야 하는지 목적지(Endpoint) 주소들을 `application.yml`에 명시적으로 추가 지정**해 주어야 합니다.

---

## 3. 우리 프로젝트에 Naver OAuth2.0 추가를 위한 4가지 변경사항

현재 우리 프로젝트는 OCP(개방-폐쇄 원칙)를 완벽히 준수하도록 우아하게 설계되어 있어, 기존 핵심 로직(`CustomOAuth2UserService` 등)을 단 한 줄도 건드리지 않고 아래 4개의 파일 추가/수정만으로 완전한 연동이 가능합니다.

### ① `application.yml` 수정 (네이버 수동 설정 추가)
네이버 클라이언트 정보와 통신할 서버의 목적지 주소를 알려줍니다.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          # (기존 구글 설정...)
          naver: # ✅ 추가
            client-id: ${NAVER_CLIENT_ID}
            client-secret: ${NAVER_CLIENT_SECRET}
            client-name: Naver
            scope: name, email, profile_image
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/{action}/oauth2/code/{registrationId}"
        provider:
          # ✅ 네이버는 Spring Security 기본 내장 제공자가 아니므로 추가해야 함
          naver:
            authorization-uri: https://nid.naver.com/oauth2.0/authorize
            token-uri: https://nid.naver.com/oauth2.0/token
            # 🔥 super.loadUser 호출 시 찌를 추가 API의 목적지 주소!! 🔥
            user-info-uri: https://openapi.naver.com/v1/nid/me
            # 중요: 네이버는 실제 데이터가 'response' 라는 필드명 안에 감싸져 옴
            user-name-attribute: response 
```

### ② `AuthProvider.java` Enum 추가
기존 제공자 목록에 NAVER를 추가해줍니다.

```java
// src/main/java/org/example/domain/entity/AuthProvider.java
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    NAVER // ✅ 추가
}
```

### ③ `NaverOAuth2UserInfo.java` 파일 신규 생성
네이버의 독특한 응답 형식(`{ "response": { "id": "...", "email": "..." } }`)에서 손쉽게 데이터를 뽑아오기 위한 네이버 전용 파서 틀을 생성합니다.

```java
// src/main/java/org/example/security/oauth2/NaverOAuth2UserInfo.java
package org.example.security.oauth2;

import java.util.Map;

public class NaverOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        // 이미 yml의 user-name-attribute: response 설정에 의해
        // 여기서 넘어오는 데이터는 response 내부의 알맹이 데이터입니다.
        this.attributes = attributes; 
    }

    @Override
    public String getProviderId() {
        return (String) attributes.get("id"); // 네이버 고유 ID 키
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }
}
```

### ④ `OAuth2UserInfoFactory.java` 분기 추가
요청이 "naver"로 들어왔을 때, 방금 만든 커스텀 파서를 반환하도록 팩토리의 switch-case문에 코드를 추가합니다.

```java
// src/main/java/org/example/security/oauth2/OAuth2UserInfoFactory.java
public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {

    return switch (registrationId.toLowerCase()) {
      case "google" -> new GoogleOAuth2UserInfo(attributes);
      case "naver" -> new NaverOAuth2UserInfo(attributes); // ✅ 추가
      default -> throw new IllegalArgumentException(
          "지원하지 않는 OAuth2 제공자입니다: " + registrationId);
    };
}
```
