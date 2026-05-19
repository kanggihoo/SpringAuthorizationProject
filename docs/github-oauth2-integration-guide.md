# 깃허브(GitHub) OAuth2 연동 가이드 및 개념 정리 (Spring Security)

## 1. 깃허브 연동의 특징 (Google과 동일한 내장 제공자)

유저님 말씀대로 깃허브(GitHub)는 전 세계 개발자들이 공통으로 사용하는 글로벌 플랫폼입니다. 그래서 Spring Security(정확히는 `CommonOAuth2Provider.java` 클래스 내부) 안에 Google, GitHub, Facebook, Okta의 설정 정보가 **미리 내장(Default)**되어 있습니다.

따라서 네이버나 카카오처럼 `application.yml` 하단에 `provider`의 인증 서버 URL(`authorization-uri`, `token-uri` 등)을 길게 명시할 필요가 없습니다. 단지 클라이언트 ID와 비밀번호만 등록해주면 바로 알아서 동작합니다.

**[깃허브 식별 정보 (JSON) 예시]**
```json
{
  "login": "kkh-developer",
  "id": 12345678,
  "node_id": "MDQ6VXNlcjI5M...",
  "avatar_url": "https://avatars.githubusercontent.com/u/12345678?v=4",
  "name": "강기후",
  "email": "kkh@example.com"
}
```
*   **고유 ID (`id`)**: Integer 타입입니다.
*   **이메일 (`email`)**: 최상단 필드에 위치합니다. 단, 사용자가 이메일을 숨김(Private) 처리하면 API 결과에 포함되지 않을 수 있습니다.
*   **이름 (`name`)**: 최상단 필드에 위치합니다. 유저들이 주로 쓰는 깃허브 닉네임과 같은 아이디는 `login` 필드에 들어있습니다.

---

## 2. 우리 프로젝트에 Github OAuth2.0 추가를 위한 4가지 변경사항

### ① `application.yml` 수정 (초간단 설정)
네이버/카카오와 달리 `provider` 영역은 적어줄 필요가 없고, 오직 `registration` 부분만 추가해주면 됩니다.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          # (기존 구글, 네이버, 카카오 등 생략)
          github: # ✅ 깃허브 등록 (아래 provider 정보 하드코딩 필요 없음!)
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            # scope를 지정하지 않아도 기본적으로 사용자 프로필 조회가 가능합니다.
            # 이메일까지 확실히 가져오시려면 보통 아래 권한을 설정합니다.
            scope: read:user, user:email
```

### ② `AuthProvider.java` Enum 추가
기존 제공자 목록에 GITHUB를 추가해줍니다.

```java
// src/main/java/org/example/domain/entity/AuthProvider.java
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    NAVER,
    KAKAO,
    GITHUB // ✅ 추가
}
```

### ③ `GithubOAuth2UserInfo.java` 파일 신규 생성 (파서 클래스)
구글과 흡사하게 데이터가 평면적으로(1 Depth) 들어있으므로 꺼내기가 매우 수월합니다. 단지 차이점이라면 id가 문자열이 아닌 정수형으로 내려오고, name을 미설정한 유저는 구분이 필요하다는 점입니다.

```java
// src/main/java/org/example/security/oauth2/GithubOAuth2UserInfo.java
package org.example.security.oauth2;

import java.util.Map;

public class GithubOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GithubOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProviderId() {
        // github id는 숫자형(Integer)으로 내려오므로 String 변환이 필요합니다.
        Object id = attributes.get("id");
        return id == null ? null : String.valueOf(id);
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getName() {
        String name = (String) attributes.get("name");
        // 간혹 깃허브 프로필에 실명(Name)을 등록하지 않은 사용자들은 name이 null입니다.
        // 이 경우에는 로그인 아이디(login 필드)로 이름을 대신하도록 예외 처리를 해주면 좋습니다.
        if (name == null || name.isEmpty()) {
            return (String) attributes.get("login");
        }
        return name;
    }
}
```

### ④ `OAuth2UserInfoFactory.java` 분기 추가
요청이 "github"로 들어왔을 때, 해당 파서를 반환하도록 switch-case문에 코드를 한 줄 추가합니다.

```java
// src/main/java/org/example/security/oauth2/OAuth2UserInfoFactory.java
public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {

    return switch (registrationId.toLowerCase()) {
      case "google" -> new GoogleOAuth2UserInfo(attributes);
      case "naver"  -> new NaverOAuth2UserInfo(attributes);
      case "kakao"  -> new KakaoOAuth2UserInfo(attributes);
      case "github" -> new GithubOAuth2UserInfo(attributes); // ✅ 깃허브 추가 완료!
      default -> throw new IllegalArgumentException(
          "지원하지 않는 OAuth2 제공자입니다: " + registrationId);
    };
}
```
