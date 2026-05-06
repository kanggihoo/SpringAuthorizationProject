# Troubleshooting

## OAuth2 유저가 `GOOGLE`이 아니라 `LOCAL`로 저장된 문제

### 문제 상황

OAuth2 최초 로그인 시 `CustomOAuth2UserService.registerNewOAuth2User(...)`에서
`User.oauthBuilder()`를 사용해 새 유저를 생성하고 있었다.

로그와 디버거에서는 아래 값들이 정상처럼 보였다.

- `provider = GOOGLE`
- `email = 11kkh19999@gmail.com`
- `providerId = 113274200417408424821`

하지만 실제 PostgreSQL `users` 테이블에는 아래처럼 저장됐다.

```text
provider = LOCAL
email = null
provider_id = null
username = GOOGLE_113274200417408424821
nickname = 강기호
```

즉 OAuth2 전용 필드가 반영되지 않고, LOCAL 유저 생성 경로처럼 저장되는 문제가 있었다.

---

## 원인

원인은 `User` 엔티티에서 Lombok `@Builder`를 두 생성자에 함께 사용한 방식이었다.

문제 코드:

```java
@Builder
public User(String username, String password, String nickname) {
  this.username = username;
  this.password = password;
  this.nickname = nickname;
  this.provider = AuthProvider.LOCAL;
  this.enabled = true;
  this.accountNonLocked = true;
}

@Builder(builderMethodName = "oauthBuilder")
public User(String username, String nickname, String email,
    AuthProvider provider, String providerId) {
  this.username = username;
  this.nickname = nickname;
  this.email = email;
  this.provider = provider;
  this.providerId = providerId;
  this.enabled = true;
  this.accountNonLocked = true;
}
```

겉보기에는:

- `User.builder()` -> LOCAL 생성용
- `User.oauthBuilder()` -> OAuth2 생성용

처럼 보이지만, 실제로는 `builderMethodName`이 **빌더 시작 메서드 이름만 바꿀 뿐**, 내부 builder 클래스를 분리하지 않는다.

즉 컴파일 결과는 사실상:

```java
UserBuilder builder()
UserBuilder oauthBuilder()
```

형태였고, `build()` 구현은 하나만 존재했다.

문제는 이 `build()`가 LOCAL 생성자를 호출하고 있었다는 점이다.

```java
new User(username, password, nickname)
```

그래서 `oauthBuilder()`로 아래처럼 호출해도:

```java
User.oauthBuilder()
    .username(...)
    .nickname(...)
    .email(...)
    .provider(...)
    .providerId(...)
    .build();
```

최종적으로는 `email`, `provider`, `providerId`가 생성자에 반영되지 않았다.

결과적으로:

- `provider`는 LOCAL 생성자에서 `AuthProvider.LOCAL`로 고정
- `email`은 저장되지 않음
- `providerId`도 저장되지 않음

상태가 되었다.

---

## 어떻게 확인했는가

원인 확인은 컴파일된 클래스의 바이트코드를 직접 확인해서 했다.

```powershell
javap -classpath build\classes\java\main -p -c "org.example.domain.entity.User$UserBuilder"
```

여기서 `build()`가 실제로 다음 생성자를 호출하는 것을 확인했다.

```java
User(String username, String password, String nickname)
```

즉 `oauthBuilder()`도 별도 OAuth2 빌더가 아니라 같은 `UserBuilder`를 사용하고 있었다.

---

## 수정 방법

builder 스타일은 유지하되, **builder 메서드 이름뿐 아니라 builder 클래스 이름도 분리**했다.

수정 후 코드:

```java
@Builder(builderClassName = "LocalUserBuilder")
public User(String username, String password, String nickname) {
  this.username = username;
  this.password = password;
  this.nickname = nickname;
  this.provider = AuthProvider.LOCAL;
  this.enabled = true;
  this.accountNonLocked = true;
}

@Builder(builderMethodName = "oauthBuilder", builderClassName = "OAuth2UserBuilder")
public User(String username, String nickname, String email,
    AuthProvider provider, String providerId) {
  this.username = username;
  this.nickname = nickname;
  this.email = email;
  this.provider = provider;
  this.providerId = providerId;
  this.enabled = true;
  this.accountNonLocked = true;
}
```

이제 생성 구조는 아래처럼 분리된다.

- `User.builder()` -> `LocalUserBuilder`
- `User.oauthBuilder()` -> `OAuth2UserBuilder`

각 `build()`도 서로 다른 생성자를 호출한다.

- `LocalUserBuilder.build()` -> LOCAL 생성자
- `OAuth2UserBuilder.build()` -> OAuth2 생성자

---

## 수정 후 검증

다시 컴파일 후 아래 명령으로 builder 클래스가 실제로 분리됐는지 확인했다.

```powershell
javap -classpath build\classes\java\main -p -c "org.example.domain.entity.User$LocalUserBuilder"
javap -classpath build\classes\java\main -p -c "org.example.domain.entity.User$OAuth2UserBuilder"
```

확인 결과:

- `User$LocalUserBuilder.class`
- `User$OAuth2UserBuilder.class`

가 각각 생성됐고, `OAuth2UserBuilder.build()`가 실제로 OAuth2 생성자를 호출하는 것을 확인했다.

그 후 OAuth2 로그인 재테스트 결과 DB 저장도 정상화됐다.

```text
provider = GOOGLE
email = 11kkh19999@gmail.com
provider_id = 113274200417408424821
username = GOOGLE_113274200417408424821
```

---

## 교훈

- `builderMethodName`은 시작 메서드 이름만 바꾼다.
- 생성 경로가 둘 이상이면 `builderClassName`까지 분리해야 한다.
- JPA 엔티티 생성 이상 현상은 로그만 보지 말고, 실제 DB row와 컴파일된 builder 동작까지 확인해야 한다.
