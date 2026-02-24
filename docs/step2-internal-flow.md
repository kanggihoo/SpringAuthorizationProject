# Spring Security JWT 기반 무상태 인증 내부 동작 흐름 및 테스트 가이드

본 문서는 `step2`에서 구축한 JWT(JSON Web Token) 및 무상태(Stateless) 기반의 인증/인가 체계가 Spring Security 프레임워크 내부에서 어떤 시퀀스로 동작하는지 상세히 분석한 설명입니다.

---

## 1. 기본 인증 프로세스 (Custom JWT Login API)
기존 `step1`의 Form Login 기반(기본 제공 필터 활용) 단계를 벗어나, 클라이언트와 JSON으로 통신하는 `AuthController.login()` 직접 구현 방식으로 변경되었습니다.

1. 클라이언트(브라우저/Postman)가 `POST /login`으로 `{"username": "...", "password": "..."}` JSON 요청을 보냅니다.
2. `AuthController`가 이를 DTO로 받아 `AuthService.login()`을 호출합니다.
3. **`AuthenticationManager` 위임 단계**:
   - `AuthService` 내부에서 `UsernamePasswordAuthenticationToken` (미인증 토큰)을 수동으로 생성하여 `AuthenticationManager`의 `authenticate()` 메서드에 넘깁니다.
   - `AuthenticationManager`는 기본적으로 `DaoAuthenticationProvider`를 호출합니다.
4. **UserDetailsService 연동 및 검증**:
   - `DaoAuthenticationProvider`는 입력된 `username`으로 **`CustomUserDetailsService.loadUserByUsername()`**을 호출하여 DB 조회 및 `CustomUserDetails` 객체를 확보합니다.
   - `PasswordEncoder` 객체를 통해 입력받은 비밀번호와 DB의 해시된 비밀번호가 일치하는지(`matches`) 검증합니다.
5. **인증 성공 및 JWT 발급**:
   - 검증이 완료되면 `Authentication` 객체를 반환합니다.
   - 반환받은 Authentication 객체의 Principal(CustomUserDetails)에서 유저명과 권한 정보(Roles)를 추출합니다.
   - **`JwtTokenProvider`**를 이용해 클라이언트에게 전달할 **Access Token(AT)**과 RTR(Refresh Token Rotation) 기법용 **Refresh Token(RT)**을 생성합니다.
6. DB의 `RefreshToken` 엔티티를 갱신/저장하고, AT는 Response Body(DTO)에, RT는 HttpOnly 및 Secure 속성이 적용된 **Cookie**에 담아 응답합니다. (이 지점부터 클라이언트는 무상태로 유지됩니다.)

---

## 2. 시나리오별 동작 분석 (API 요청 흐름)

### 2.1 인증 성공 시나리오 (Access Token 정상 요청)
클라이언트가 발급받은 JWT를 활용하여 인가가 필요한 자원에 접근할 때의 흐름입니다. 유저의 요청마다 토큰 정보를 검증해 임시로 세션을 생성했다 소멸하게 됩니다.

1. **요청 수신**: 클라이언트가 HTTP 헤더에 `Authorization: Bearer <접근_토큰>`을 담아 API를 호출합니다.
2. **`JwtAuthenticationFilter` 가로채기**: `UsernamePasswordAuthenticationFilter` 이전에 커스텀 등록한 `JwtAuthenticationFilter`가 해당 요청 헤더를 파싱합니다.
3. **토큰 검증**: 추출한 토큰 문자열을 `JwtTokenProvider.validateToken()`으로 넘겨 시크릿 키로 서명을 검증하고 만료 여부를 확인합니다.
4. **SecurityContext 저장**:
   - 토큰이 유효하면 `Jwts.parser()`를 통해 Payload(Claims) 안에서 `username`과 `roles` 데이터를 꺼냅니다.
   - 이 정보들을 바탕으로 `UsernamePasswordAuthenticationToken` 객체를 "즉석"으로 생성합니다. (매 요청마다 DB 접근을 최소화하기 위함입니다.)
   - 이 객체를 **`SecurityContextHolder.getContext().setAuthentication()`**을 이용해 프레임워크 전역에 저장합니다.
5. 이후 필터 체인을 정상적으로 통과하여 Controller 메서드에 도달, 비즈니스 로직을 수행합니다.

### 2.2 인증 실패 시나리오 (토큰 미제공, 유효하지 않은 토큰)
Access Token이 없거나 검증(유효성, 만료)에 실패했을 때 보호된 자원에 접근하고자 할 때 발생하는 방어 기제입니다.

1. `JwtAuthenticationFilter`에서 헤더를 찾을 수 없거나, 유효성 검증 로직이 실패합니다. (예: `ExpiredJwtException`, `MalformedJwtException` 등이 터지거나, 아예 인증 처리를 건너뜁니다.)
2. `SecurityContext`가 채워지지 않은 상태(미인증 상태)로 필터 체인이 계속 이어지다가 끝단의 인가 담당 필터(**`AuthorizationFilter`**)에 도착합니다.
3. 메서드나 URL별 권한 설정에 의해 비인가 접근으로 간주되어 권한 예외가 터집니다.
4. **`ExceptionTranslationFilter` 위임**: 발생한 예외를 상위 필터인 `ExceptionTranslationFilter`가 잡고, 미인증 접근이므로 **`AuthenticationEntryPoint`**를 작동시킵니다.
5. 우리가 `SecurityConfig`에 커스텀으로 등록한 **`CustomAuthenticationEntryPoint`**가 호출되어 프론트엔드가 이해할 수 있는 401 Unauthorized 기반 커스텀 JSON 에러를 반환합니다.

> **※ 참고사항 (예외 통합 관리)**: JWT 토큰 자체 파싱 중 발생하는 예외는 일반 MVC Controller Advice(`@RestControllerAdvice`)에서 낚아아챌 수 없습니다. 따라서 `ExceptionHandlerFilter`를 `JwtAuthenticationFilter`보다 앞에 배치하여 필터 단 내의 예외를 캐치하고 `HandlerExceptionResolver`로 위임하는 형태로 설계되었습니다. 이를 통해 `GlobalExceptionHandler`에서 JWT 예외까지 통합하여 JSON 포맷으로 핸들링하게 됩니다.

### 2.3 인가 실패 시나리오 (권한 부족)
인증 자체는 정상 토큰으로 성공했으나(유저 로그인 상태), 타겟 자원(예: `ADMIN` 전용 자원)에 대한 권한 정보가 부족할 때 나타나는 흐름입니다.

1. `JwtAuthenticationFilter`에 의해 성공적으로 `SecurityContext`에 `ROLE_USER` 권한을 가진 사용자 인증 정보가 세팅됩니다.
2. 필터 체인의 가장 끝단인 **`AuthorizationFilter`**에 도달합니다.
3. 여기서 `SecurityConfig`에 정의된 `.requestMatchers("/admin/**").hasRole("ADMIN")` 조건을 검증합니다. 사용자의 Claims 기반 `Authorities` 리스트 안에 `ROLE_ADMIN`이 부재함을 확인합니다.
4. 예외가 던져지며 곧바로 **`AccessDeniedException`**이 터집니다.
5. 상단의 **`ExceptionTranslationFilter`**가 이 예외를 캐치, 등록된 **`AccessDeniedHandler`**를 동작시킵니다.
6. **`CustomAccessDeniedHandler`**에서 403 Forbidden 기반 커스텀 JSON 에러를 응답으로 내보냅니다.

---

## 3. RTR(Refresh Token Rotation) 재발급 및 로그아웃 흐름

Access Token이 만료된 상황을 클라이언트가 인식(401 응답)하면 Token Refresh를 시도해야 합니다.

1. **Refresh 요청**: `POST /refresh` 엔드포인트를 호출합니다. 브라우저/Postman은 자동으로 발급받아두었던 `Refresh-Token` 쿠키를 동봉합니다.
2. `AuthController`에서 해당 쿠키값을 파싱하여 `AuthService.refresh()`로 보냅니다.
3. **토큰 1차 유효성**: `JwtTokenProvider.validateToken()`으로 형태 및 만료 일자를 확인합니다.
4. **DB 대조 (보안 기제)**: `RefreshTokenRepository`를 조회해 현재 저장소에 기록된 사용자의 RT 값과 정확히 일치하는지 확인합니다. (탈취된 토큰 사용 시 DB에서 찾을 수 없어 예외 발생).
5. **갱신 및 Rotation 반영**: 식별된 `User`를 바탕으로 새로운 Access Token과 새로운 Refresh Token을 즉시 발급합니다. DB 레코드에는 기존 RT를 신규 RT로 덮어쓰고 만료 시한을 갱신합니다(Dirty Checking).
6. 브라우저 전송을 위해 기존 쿠키를 새 `Refresh-Token` 밸류로 교환해 응답(JSON+Cookie)을 마칩니다.
7. **로그아웃의 경우**: 쿠키를 빈 값(만료 처리)으로 만들면서 DB에 있는 해당 Refresh Token 기록을 삭제함으로써, 영원히 재발급이 일어나지 않게 물리적으로 차단합니다.

---

## 4. 테스트 가이드 (Postman API 테스트)

현재는 백엔드만 있는 환경이므로 Postman을 활용해 헤더(`Authorization`)와 쿠키(`Cookie`)를 다루어야 합니다.

### 4.1. 회원가입
- **Method**: `POST`
- **URL**: `http://localhost:8080/signup`
- **Header**: `Content-Type: application/json`
- **Body**: 
  ```json
  {
    "username": "user1",
    "password": "password",
    "nickname": "Tester"
  }
  ```
- **결과**: `200 OK`, "회원가입이 완료되었습니다."

### 4.2. 로그인 (토큰 세트 발급받기)
- **Method**: `POST`
- **URL**: `http://localhost:8080/login`
- **Header**: `Content-Type: application/json`
- **Body**: 
  ```json
  {
    "username": "user1",
    "password": "password"
  }
  ```
- **결과**: JSON Body에서 `accessToken` 값을 복사합니다. 추가로 Postman 하단의 **Cookies 탭**에 도메인 `localhost`로 `Refresh-Token`이 할당되어 있는지 직접 확인합니다.

### 4.3. 인가 테스트: USER 접근 성공
- **Method**: `GET`
- **URL**: `http://localhost:8080/user/profile`
- **Header 추가**:
  - Key: `Authorization`
  - Value: `Bearer <복사해둔 accessToken 값>` (Bearer와 공백 한 칸 입력 주의)
- **결과**: 200 응답 및 정상적인 "회원 프로필 페이지입니다." 반환.

### 4.4. 인가 테스트: ADMIN 리소스 접근 거부
- **Method**: `GET`
- **URL**: `http://localhost:8080/admin/manage`
- **Header 추가**: (위 4.3과 동일한 Authorization 헤더 유지)
- **결과**: 권한이 부족하여 **403 Forbidden** 커스텀 JSON 응답이 반환되는 것을 확인합니다.
  ```json
  {
    "path": "/admin/manage",
    "error": "Forbidden",
    "message": "해당 자원에 접근할 권한이 없습니다.",
    "status": 403
  }
  ```

### 4.5. 토큰 재발급 테스트 (Refresh)
- **Method**: `POST`
- **URL**: `http://localhost:8080/refresh`
- **Header/Body**: 헤더에 Authorization을 뺄 것. (`Refresh-Token` 쿠키는 Postman이 스스로 실어서 보냄)
- **결과**: `200 OK` 응답 및 JSON Body에 **새로운(문자열이 달라진) `accessToken`**이, Cookie에 **새로운 `Refresh-Token`**이 잘 발급되는지 관찰합니다.

### 4.6. 로그아웃 
- **Method**: `POST`
- **URL**: `http://localhost:8080/logout`
- **Header 추가**: 
  - `Authorization`: `Bearer <현재의 accessToken 값>`
- **결과**: `200 OK` 응답 확인 후 DB RefreshToken 테이블에서 해당 유저의 레코드가 삭제되었는지, Postman Cookies의 Refresh-Token이 만료(사라짐) 처리되었는지 점검합니다. 
- 이후 유효기간이 끝난 AT나 RT로는 어떤 API도 재인가받을 수 없게 됩니다.
