# 2단계: 무상태(Stateless) 인증과 JWT 도입 (확장성 확보)

세션을 제거하고 JWT를 도입하여 서버의 확장성을 높이는 단계. 기존 Form Login 방식에서 완전히 벗어나 RESTful API 구조에 적합한 인증 체계를 구축

## 핵심 정책

- 인증 필터 구성: JWT 검증을 위한 Custom Security Filter 및 Provider 추가.
- 무상태 정책: Spring Security의 세션 정책을 SessionCreationPolicy.STATELESS로 변경.
- 데이터 통신 규격: 클라이언트의 요청은 x-www-form-urlencoded가 아닌 application/json 형식을 기준으로 처리.
- 토큰 분리 전송: Access Token은 JSON 응답 본문(Body)에 담고, Refresh Token은 보안을 위해 HttpOnly, Secure, SameSite=Lax 옵션이 적용된 쿠키(Cookie)에 담아 반환.

## 1. JWT 유틸리티 클래스 (JwtTokenProvider) 상세

단순히 토큰 생성에 그치지 않고, 보안과 성능을 고려하여 다음 기능을 포함합니다.

- Secret Key 관리: HS256 알고리즘에 적합한 32바이트의 시크릿 키를 사용하며, 소스 코드가 아닌 application.yml 또는 환경 변수에서 관리하도록 구성.
- Claims 구성: 페이로드(Subject)에 유저의 식별값(PK 또는 Username)을 넣고, 권한(Roles) 정보를 리스트 형태로 포함시켜 토큰만으로 유저를 식별할 수 있게 설계.
- 예외 세분화: 토큰 검증 시 발생하는 예외를 만료(Expired), 형식 오류(Malformed), 지원되지 않는 토큰(Unsupported) 등으로 명확히 구분하여 예외 객체를 던집니다.

## 2. JwtAuthenticationFilter 구현

UsernamePasswordAuthenticationFilter 이전에 배치되어 매 API 요청마다 JWT를 검증하는 핵심 필터입니다.

- ShouldNotFilter 적용: 특정 경로(로그인, 회원가입, 정적 리소스 등 인증이 필요 없는 엔드포인트)는 토큰 추출 및 검증 로직을 타지 않도록 오버라이드하여 서버 성능을 최적화합니다.
- SecurityContext 삽입 및 DB 조회 최적화: 검증이 완료된 토큰의 Claims 정보(식별값, 권한)만을 바탕으로 UsernamePasswordAuthenticationToken을 즉석에서 생성하여 SecurityContextHolder에 저장합니다. (매 API 요청마다 발생하는 불필요한 DB 조회를 제거)

---

## 3. 로그인, 로그아웃 및 예외 응답 구조 변경

Form Login 핸들러를 대체할 직접적인 엔드포인트 처리가 필요합니다.

- Login Controller: /login 요청(application/json)을 받아 AuthenticationManager를 통해 인증을 수행합니다. 최초 로그인 시에만 CustomUserDetailsService가 동작하여 DB를 조회합니다. 성공 시 토큰을 생성하여 분리 전송(Body & Cookie) 규칙에 맞게 응답합니다.
- Logout Controller (추가): /logout 요청 시 클라이언트의 쿠키를 만료시키고, 서버 DB에 저장된 해당 유저의 Refresh Token 레코드를 삭제하여 추후 재발급을 원천 차단합니다.
- 예외 핸들링 구조: 세션 방식의 리다이렉트를 제거하고, AuthenticationEntryPoint (401)와 AccessDeniedHandler (403)를 커스텀하여 프론트엔드가 이해할 수 있는 JSON 형태의 에러 메시지를 반환합니다.

---

## 4. 토큰 발급 및 재발급 관련 명세 (RTR 적용)

### A. Refresh Token DB 구조 (PostgreSQL)

사용자별로 단 하나의 유효한 Refresh Token만 관리합니다.

- id: PK (Long)
- user_id: 사용자 테이블과의 연관 관계 (1:1 매핑 권장)
- refresh_token: 실제 JWT 문자열 (String)
- expiry_date: 토큰의 만료 일시 (LocalDateTime)

### B. 로그인 시 (Initial Login - @Transactional 적용)

1. 인증: AuthenticationManager를 통해 사용자 자격 증명(ID/PW) 확인.
2. 생성: JwtProvider를 통해 Access Token(AT)과 Refresh Token(RT) 세트 생성.
3. 저장: DB에서 해당 사용자의 기존 RT를 조회. 존재하면 새 값으로 엔티티 수정(Dirty Checking), 없다면 새로 Insert 수행.
4. 응답: AT는 Body에 JSON 형식으로, RT는 보안 쿠키(Lax)에 담아 반환.

### C. 토큰 재발급 시 (Reissue / Refresh - @Transactional 적용)

1. 요청 수신: 클라이언트가 /reissue 엔드포인트로 쿠키에 담긴 RT를 전달.
2. 1차 검증: JwtProvider로 전달받은 RT의 서명 및 만료 일자 유효성 검증.
3. DB 조회: 해당 사용자의 RT 정보를 DB에서 검색.
4. 보안 검증 (RTR 방어): DB에 저장된 RT 값과 전달받은 RT 값 대조.
    - 불일치 시: 토큰 탈취 혹은 이미 사용된 토큰으로 간주하여 즉시 해당 유저의 DB 레코드를 삭제하고 에러 반환 (강제 로그아웃 효과).
5. 갱신 (Rotation): 일치한다면 새로운 AT와 RT 세트를 재발급. 기존 조회한 DB 엔티티의 토큰 값과 만료 일자를 새 정보로 업데이트 (트랜잭션 종료 시 반영).
6. 응답: 새로운 토큰 세트를 반환.

---

## 5. 예외처리 위임 구조

Spring Security 필터 단에서 발생한 예외를 Spring MVC의 @RestControllerAdvice에서 통합 관리할 수 있도록 HandlerExceptionResolver를 활용합니다.

### A. ExceptionHandlerFilter (예외 전파 필터)

이 필터는 JwtAuthenticationFilter보다 앞에 위치하여 발생하는 예외를 감시합니다.

1. JwtAuthenticationFilter의 동작을 try-catch로 감쌉니다.
2. 예외가 발생하면 handlerExceptionResolver.resolveException(request, response, null, e)를 호출합니다.
3. 제어권이 MVC로 넘어가며 GlobalExceptionHandler 등에서 일관된 JSON 에러 응답을 구성할 수 있습니다.

### B. JWT 예외 세분화 및 대응 로직
| **예외 종류** | **원인** | **던질 예외 (예시)** | **클라이언트 대응** |
| --- | --- | --- | --- |
| **ExpiredJwtException** | 유효 기간 만료 | `ExpiredTokenException` | `/reissue` 호출 (토큰 갱신) |
| **MalformedJwtException** | 토큰 구조가 잘못됨 | `InvalidTokenException` | 재로그인 (로그아웃 처리) |
| **SignatureException** | 서명이 일치하지 않음 | `InvalidTokenException` | 재로그인 (보안 경고) |
| **UnsupportedJwtException** | 지원되지 않는 형식 | `InvalidTokenException` | 재로그인 |
| **IllegalArgumentException** | 토큰이 비어있음 등 | `EmptyTokenException` | 상황에 따른 처리 |