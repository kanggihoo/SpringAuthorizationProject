# 카카오 OAuth2 연동 가이드 및 개념 정리 (Spring Security)

## 1. 카카오 사용자 정보 API 구조의 특징

각 소셜 로그인 제공자마다 사용자 정보를 던져주는 JSON 형식이 제각각입니다. 네이버가 데이터를 `response`라는 껍데기로 감쌌다면, 카카오는 계층적이고 복잡한 구조를 가지고 있습니다.

**[카카오 프로필 응답 JSON 예시]**
```json
{
    "id": 123456789,
    "connected_at": "2022-04-11T01:45:28Z",
    "kakao_account": {
        "profile": {
            "nickname": "홍길동",
            "thumbnail_image_url": "http://yyy.kakao.com/.../img_110x110.jpg"
        },
        "email": "sample@sample.com"
    }
}
```
*   **고유 ID (`id`)**: 최상단 계층에 위치 (`Long` 타입이므로 캐스팅 주의)
*   **이메일 (`email`)**: 최상단이 아닌 `kakao_account` 맵 안에 위치
*   **이름 (`nickname`)**: `kakao_account` 안에서도 한 뎁스 더 들어간 `profile` 맵 안에 위치

이러한 구조적 차이에도 불구하고, 우리의 `OAuth2UserInfo` 인터페이스 기반 아키텍처 덕분에 핵심 로직을 전혀 건드리지 않고 카카오만 파싱하는 클래스를 추가해 쉽게 대처할 수 있습니다.

---

## 2. 우리 프로젝트에 Kakao OAuth2.0 추가를 위한 4가지 변경사항

### ① `application.yml` 수정 (카카오 수동 설정 추가)
카카오 역시 기본 내장 제공자가 아니므로 통신할 서버 주소(URI)들을 직접 알려줘야 합니다. 네이버와 달리 기준점이 되는 `user-name-attribute`를 최상위 키인 `id`로 잡아줍니다.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          # (기존 생략)
          kakao: # ✅ 카카오 등록
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET} # 보안 설정 켰을 경우 사용
            client-name: Kakao
            # 카카오 디벨로퍼스에서 설정한 동의항목 ID와 일치시켜야 함
            scope: profile_nickname, account_email
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/{action}/oauth2/code/{registrationId}"
        provider:
          # ✅ 카카오 인증 서버 주소 명시
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            # 🔥 카카오는 최상위 필드인 'id'를 기준으로 파싱 시작
            user-name-attribute: id 
```

### ② `AuthProvider.java` Enum 추가
기존 제공자 목록에 KAKAO를 추가해줍니다.

```java
// src/main/java/org/example/domain/entity/AuthProvider.java
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    NAVER,
    KAKAO // ✅ 추가
}
```

### ③ `KakaoOAuth2UserInfo.java` 파일 신규 생성 (파서 클래스)
복잡하게 감싸진 카카오의 이메일과 이름 데이터를 꺼내기 위한 전용 파서 틀을 생성합니다.

```java
// src/main/java/org/example/security/oauth2/KakaoOAuth2UserInfo.java
package org.example.security.oauth2;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProviderId() {
        // 카카오 id는 10자리 이상의 Long 타입이므로 String으로 변환해서 반환
        return String.valueOf(attributes.get("id")); 
    }

    @Override
    public String getEmail() {
        // 계정 정보는 'kakao_account' 맵 안에 있음
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        if (account == null) return null;
        
        return (String) account.get("email");
    }

    @Override
    public String getName() {
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        if (account == null) return null;
        
        // 닉네임 정보는 'kakao_account' 안의 'profile' 맵 안에 위치함
        Map<String, Object> profile = (Map<String, Object>) account.get("profile");
        if (profile == null) return null;
        
        return (String) profile.get("nickname");
    }
}
```

### ④ `OAuth2UserInfoFactory.java` 분기 추가
요청이 "kakao"로 들어왔을 때, 방금 만든 카카오 전용 파서를 반환하도록 switch-case문에 코드를 추가합니다.

```java
// src/main/java/org/example/security/oauth2/OAuth2UserInfoFactory.java
public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {

    return switch (registrationId.toLowerCase()) {
      case "google" -> new GoogleOAuth2UserInfo(attributes);
      case "naver"  -> new NaverOAuth2UserInfo(attributes);
      case "kakao"  -> new KakaoOAuth2UserInfo(attributes); // ✅ 카카오 추가 완료!
      default -> throw new IllegalArgumentException(
          "지원하지 않는 OAuth2 제공자입니다: " + registrationId);
    };
}
```
