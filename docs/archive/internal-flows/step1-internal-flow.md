# Spring Security 내부 동작 흐름 및 테스트 가이드

본 문서는 `Step1.md`에서 구현한 Core Security 및 JPA 기반 기본 인증 로직이 프레임워크 내부에서 어떤 시퀀스로 동작하는지 설명하는 문서

---

## 1. 기본 인증 로직 분석 (Form Login)
표준 폼 로그인 기반으로 사용자가 자격 증명(ID/비밀번호)을 제출했을 때 Spring Security의 내부 처리 루틴은 다음과 같은 단계를 거칩니다.

1. 사용자가 브라우저나 Postman을 통해 `POST /login` (Content-Type: `x-www-form-urlencoded`) 요청을 전송합니다.
2. Spring Security 필터 체인의 **`UsernamePasswordAuthenticationFilter`**가 이 요청을 가로채어 파라미터에서 `username`과 `password`를 추출합니다.
3. 추출된 데이터를 바탕으로 아직 인증되지 않은 빈 껍데기의 **`UsernamePasswordAuthenticationToken`** 객체를 생성합니다.
4. 해당 토큰 객체는 인증을 총괄하는 **`AuthenticationManager`**(주로 `ProviderManager` 구현체)에게 전달됩니다.
5. `ProviderManager`는 등록된 여러 **`AuthenticationProvider`**들 중에서 폼 로그인 토큰을 처리할 수 있는 **`DaoAuthenticationProvider`**를 호출하여 실제 인증을 위임합니다.

---

## 2. 시나리오별 동작 분석

### 2.1 인증 성공 시나리오
올바른 자격 증명을 제출하여 정상적으로 Spring Security 인증이 처리되는 구체적인 흐름입니다.

1. **토큰 생성 및 검증 위임**: `UsernamePasswordAuthenticationFilter`가 미인증 `UsernamePasswordAuthenticationToken`을 생성하고, `DaoAuthenticationProvider`까지 흐름이 이어집니다.
2. **UserDetailsService 연동**: `DaoAuthenticationProvider`는 사용자의 실제 DB 정보를 가져오기 위해 우리가 등록한 **`CustomUserDetailsService.loadUserByUsername(String username)`** 메서드를 호출합니다.
3. **DB 조회 및 UserDetails 반환**: `CustomUserDetailsService` 내부에서 `UserRepository.findByUsername()`을 실행해 엔티티를 찾습니다. 찾은 `User` 정보를 바탕으로 **`CustomUserDetails`**(UserDetails의 구현체) 객체를 생성하여 반환합니다.
4. **비밀번호 일치 검사**: `DaoAuthenticationProvider`는 반환받은 `CustomUserDetails`의 해시된 암호와 사용자가 입력한 순수 암호를 **`PasswordEncoder`**(`BCryptPasswordEncoder`)를 사용하여 비교(`matches()` 메서드)합니다. 
5. **Authentication 객체 최종 생성**: 비밀번호가 일치하면 인증 성공입니다. 인증이 완료된 원본 `CustomUserDetails`를 통째로 `Principal`에 담고, 유저의 권한 정보 모음(`Authorities`)까지 채워 넣은 완전한 상태의 새로운 **`UsernamePasswordAuthenticationToken`**(최종 인증된 Authentication 객체)을 생성합니다.
6. **SecurityContext 저장**: 생성된 객체는 **`SecurityContextHolder`**의 `SecurityContext` 내부에 저장(`setAuthentication()`)됩니다. 이후 다른 필터나 컨트롤러(`@AuthenticationPrincipal`)에서 쉽게 꺼내 쓸 수 있습니다.
7. **성공 핸들러**: 최종적으로 이 요청은 **`AuthenticationSuccessHandler`**를 거쳐 `SecurityConfig`에 정의된 `defaultSuccessUrl("/", true)` 설정에 맞게 지정한 URL(루트 `/`)로 리다이렉트 응답을 반환합니다. 이때 응답으로 **`JSESSIONID`**라는 이름의 세션 쿠키가 발급되며, 브라우저 환경이나 Postman에서 발급된 쿠키를 직접 확인할 수 있습니다.

### 2.2 인증 실패 시나리오
잘못된 ID나 비밀번호 입력 시 인증이 실패하고 예외 처리 단계로 넘어가는 과정입니다.

1. 일반적인 폼 요청과 동일하게 로직이 `UsernamePasswordAuthenticationFilter` -> `DaoAuthenticationProvider` 로 들어옵니다.
2. **예외 발생 지점**:
   - DB에 찾는 회원이 없는 경우: `CustomUserDetailsService`에서 대상을 찾지 못하고 **`UsernameNotFoundException`**을 발생시킵니다.
   - 비밀번호 검증이 실패한 경우: `DaoAuthenticationProvider`에서 해시값이 불일치함을 판단하여 **`BadCredentialsException`**을 발생시킵니다.
3. **AuthenticationFailureHandler 호출**: 예외가 발생하면 예외 객체는 호출의 역순을 타고 돌아와 처리 필터(`UsernamePasswordAuthenticationFilter`)에서 잡힙니다. 이후 **`AuthenticationEntryPoint`** 또는 기본 **`AuthenticationFailureHandler`**가 실패 처리를 맡아, 기본 에러 로그인 폼(`/login?error`)으로 다시 리다이렉트하거나 401 메세지를 응답하게 됩니다.

### 2.3 인가 실패 시나리오 (권한 부족)
인증 자체는 성공했으나(로그인 O), 특정 자원(`ADMIN`)에 대한 권한이 부족할 때 나타나는 흐름입니다.

1. 사용자가 이미 로그인되어 발급받은 `JSESSIONID`를 지닌 채로 권한이 필요한 `GET /admin/manage` 엔드포인트를 호출합니다.
2. **SecurityContext 복원**: `SecurityContextPersistenceFilter` 또는 유사한 필터가 세션의 `JSESSIONID`를 바탕으로 `SecurityContext` 내부의 유저 `Authentication` 정보를 복원합니다.
3. **권한 인가 검증**: 요청 흐름이 필터 체인의 가장 끝단인 인가 담당 필터(**`AuthorizationFilter`**, 구버전은 `FilterSecurityInterceptor`)에 도달합니다.
4. 여기서는 `SecurityConfig`에 정의된 `requestMatchers("/admin/**").hasRole("ADMIN")` 조건을 수행합니다. 사용자의 `Authentication.getAuthorities()` 정보 안에 해당 권한 문자열(`ROLE_ADMIN`)이 있는지 체크합니다.
5. **ExceptionTranslationFilter 위임**: `testuser`는 `ROLE_USER` 권한만 있어 검증이 실패하므로 **`AccessDeniedException`**이 터집니다.
6. 이 예외를 상위 체인인 **`ExceptionTranslationFilter`**가 가로채어, 인가 실패 상황임을 인지하고 등록된 **`AccessDeniedHandler`**를 동작시킵니다. 이 핸들러는 403 상태 코드를 반환하거나 403 에러 안내 HTML로 리다이렉트 시킵니다.

---

## 3. 테스트 가이드 (Postman을 이용한 API 테스트)
현재 화면(Frontend/UI)이 없는 환경이므로, API 테스터인 Postman을 통해 세션의 유지와 인증, 인가 흐름을 테스트합니다.  
> **주의**: Postman은 응답받은 쿠키(`JSESSIONID`)를 내부 Cookie Jar에 자동으로 캐싱하여 다음 요청 시 세션을 유지해줍니다.

### 3.1 회원가입
- **Method**: `POST`
- **URL**: `http://localhost:8080/signup`
- **Header**: `Content-Type: application/json`
- **Body**: (raw - JSON)
  ```json
  {
    "username": "testuser",
    "password": "password123",
    "nickname": "Tester"
  }
  ```
- **결과**: `200 OK` 및 `"회원가입이 완료되었습니다."` 텍스트 반환. DB에 `testuser` 정보가 성공적으로 인서트 되었음을 알 수 있습니다.

### 3.2 로그인 시도 및 성공 확인
- **Method**: `POST`
- **URL**: `http://localhost:8080/login`
- **Header**: `Content-Type: application/x-www-form-urlencoded`
- **Body**: (x-www-form-urlencoded)
  - `username` : `testuser`
  - `password` : `password123`
- **결과**: 루트 URL(`/`)로 302 리다이렉트 된 후 최종적으로 `TestController`의 메인 페이지 응답(`"안녕하세요, Tester님! ..."`) 문구가 반환됩니다. **(이 시점에 Postman의 Cookies 탭에 JSESSIONID가 세팅되는지 확인 필수)**.

### 3.3 인가 (Authorization) 테스트
**3.3.1 USER 권한 접근 (/user/profile) - 성공**
- **Method**: `GET`
- **URL**: `http://localhost:8080/user/profile`
- **Header 및 Body**: 추가 설정 불필요 (Postman이 자동으로 `JSESSIONID` 쿠키를 동봉)
- **결과**: `200 OK` 및 `"회원 프로필 페이지입니다. 반가워요, Tester님!"` 반환. 인증된 USER 권한이 정상 인가되었음을 알 수 있습니다.

**3.3.2 ADMIN 권한 접근 거부 (/admin/manage) - 실패**
- **Method**: `GET`
- **URL**: `http://localhost:8080/admin/manage`
- **Header 및 Body**: 추가 설정 불필요 (`JSESSIONID` 존재)
- **결과**: 권한이 부족하여 **`403 Forbidden`**을 반환합니다. 필터 체인에서 인가가 거부된 사실을 증명합니다.

### 3.4 비로그인 상태 거부 테스트
- **조치**: Postman의 Cookies 섹션을 열고 현재 도메인(`localhost`)에 부여된 `JSESSIONID` 항목을 삭제합니다.
- **Method**: `GET`
- **URL**: `http://localhost:8080/user/profile`
- **결과**: 로그인이 만료되었기 때문에, Spring Security가 인증이 필요하다고 인지하여 `AuthenticationEntryPoint`에 의해 기본 로그인 페이지의 HTML 코드를 200 반환하거나 `/login` (GET)으로 리다이렉션을 발생시킵니다. 브라우저인 경우 로그인 입력 폼이 뜹니다.
