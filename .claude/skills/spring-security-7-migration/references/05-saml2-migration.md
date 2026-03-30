# Spring Security 7 - SAML2 마이그레이션

> 공식 문서: https://docs.spring.io/spring-security/reference/migration/servlet/saml2.html
> **SAML2 인증을 사용하는 경우에만 해당**

---

## 목차

1. [LogoutRequest 실패 시 LogoutResponse 반환](#1-logoutrequest-실패-시-logoutresponse-반환)
2. [Saml2AuthenticatedPrincipal → Saml2ResponseAssertionAccessor](#2-saml2authenticatedprincipal--saml2responseassertionaccessor)
3. [GET 요청으로 SAML Response 처리 비활성화 준비](#3-get-요청으로-saml-response-처리-비활성화-준비)

---

## 1. LogoutRequest 실패 시 LogoutResponse 반환

### 변경 내용

SAML2 표준에 따르면, `<saml2:LogoutRequest>` 처리가 실패할 경우 SP(Service Provider)는
오류 `<saml2:LogoutResponse>`를 IdP에 반환해야 한다. 기존에는 401 오류를 반환하여
로그아웃 흐름이 깨지는 문제가 있었다.

**Spring Security 7에서는 이 동작이 자동으로 수정된다. 별도 코드 변경이 필요하지 않다.**

### 이전 동작으로 되돌리려면 (선택 사항)

예외적으로 기존 동작이 필요한 경우, 커스텀 `Saml2LogoutResponseResolver`를 Bean으로 등록한다:

```java
@Bean
Saml2LogoutResponseResolver logoutResponseResolver(
        RelyingPartyRegistrationRepository registrations) {
    OpenSaml5LogoutResponseResolver delegate =
        new OpenSaml5LogoutResponseResolver(registrations);
    return new Saml2LogoutResponseResolver() {
        @Override
        public void resolve(HttpServletRequest request, Authentication authentication) {
            delegate.resolve(request, authentication);
        }

        @Override
        public void resolve(HttpServletRequest request, Authentication authentication,
                Saml2AuthenticationException error) {
            return null;  // 오류 시 null 반환 → 기존 401 동작과 유사
        }
    };
}
```

---

## 2. `Saml2AuthenticatedPrincipal` → `Saml2ResponseAssertionAccessor`

### 변경 내용

Spring Security 7은 `<saml2:Assertion>` 세부 정보를 principal과 분리하여 관리하도록
구조가 변경되었다. 이를 통해 Single Logout에 필요한 assertion 데이터를 Spring Security가
직접 접근할 수 있게 된다.

- `Saml2AuthenticatedPrincipal` → deprecated
- 대신 `Saml2ResponseAssertionAccessor`를 사용
- 기본값을 사용하는 경우 **자동으로 변경됨** (코드 수정 불필요)

### 커스텀 `ResponseAuthenticationConverter` 사용 시

`Saml2Authentication`을 직접 생성하는 경우, `Saml2AssertionAuthentication`으로 전환을
고려한다:

```java
@Bean
OpenSaml5AuthenticationProvider authenticationProvider() {
    OpenSaml5AuthenticationProvider provider = new OpenSaml5AuthenticationProvider();
    ResponseAuthenticationConverter defaults = new ResponseAuthenticationConverter();

    provider.setResponseAuthenticationConverter(
        defaults.andThen((authentication) -> new Saml2Authentication(
            authentication.getPrincipal(),
            authentication.getSaml2Response(),
            authentication.getAuthorities()
        ))
    );
    return provider;
}
```

> **권장**: `Saml2Authentication`을 직접 생성하는 경우 `Saml2AssertionAuthentication`으로
> 전환하면 현재 기본 동작의 이점을 그대로 활용할 수 있다.

---

## 3. GET 요청으로 SAML Response 처리 비활성화 준비

### 변경 내용

SAML2 스펙은 `<saml2:Response>` 페이로드를 GET 요청으로 전송하는 것을 지원하지 않는다.
Spring Security 8에서는 `Saml2AuthenticationTokenConverter`와 `OpenSaml5AuthenticationTokenConverter`가
GET 요청을 기본적으로 처리하지 않을 예정이다.

**Spring Security 7에서는 아직 필수가 아니지만, 8로의 마이그레이션을 준비하기 위해
미리 설정해 두는 것을 권장한다.**

### Spring Security 8 대비 준비 (권장)

```java
@Bean
OpenSaml5AuthenticationTokenConverter authenticationConverter(
        RelyingPartyRegistrationRepository registrations) {
    OpenSaml5AuthenticationTokenConverter converter =
        new OpenSaml5AuthenticationTokenConverter(registrations);
    converter.setShouldConvertGetRequests(false);  // GET 요청 처리 비활성화
    return converter;
}
```

### GET 요청 처리를 계속 유지해야 하는 경우

```java
converter.setShouldConvertGetRequests(true);  // 명시적으로 활성화
```

---

## 체크리스트

- [ ] LogoutRequest 실패 응답 동작 테스트 (자동 수정됨, 검증만 필요)
- [ ] `Saml2AuthenticatedPrincipal` 직접 사용 여부 확인 → `Saml2ResponseAssertionAccessor`로 전환 검토
- [ ] `setShouldConvertGetRequests(false)` 적용하여 Spring Security 8 대비
