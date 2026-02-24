## 1. 프로젝트 요구사항 및 아키텍처 구조

### 인증 및 토큰 관리 (JWT & Redis)

- 무상태(Stateless) 인증: 세션을 사용하지 않고 JWT를 사용하여 확장성 확보.
- Dual Token 전략: Access Token(단기)과 Refresh Token(장기) 발급.
- RTR (Refresh Token Rotation): Refresh Token 사용 시 새로운 Access/Refresh Token 세트를 재발급하여 탈취 위험 감소.
- Redis 활용:
    - Refresh Token 저장 (RTR 검증 및 중복 로그인 제어).
    - Blacklist 저장 (로그아웃된 Access Token의 남은 유효 시간 동안 차단).
- 엔드포인트 분리: 로그인(/login)과 토큰 갱신(/refresh) 경로를 명확히 구분.

### 사용자 관리 및 보안 (PostgreSQL & JPA)

- DB 설계: PostgreSQL에 커스텀 User 테이블 구성.
- 비밀번호 암호화: BCryptPasswordEncoder를 통한 해싱 및 솔팅 저장.
- 데이터 접근: Spring Data JPA를 기본으로 하되, 복잡한 조회는 Querydsl로 타입 안정성 확보.
- 계정 잠금 정책: 로그인 실패 횟수 초과 시 계정 잠금(enabled 또는 accountNonLocked 필드 활용) 및 관리자 해제 기능.
- 정보 수정: 사용자가 자신의 정보를 확인하고 변경하면 즉시 DB와 SecurityContext에 반영.

### OAuth2 및 권한 제어

- 소셜 로그인: 구글, 깃허브, 카카오, 네이버 등 다중 프로바이더 지원.
- 보안 강화: 초기 Code Grant 방식에서 PKCE(Proof Key for Code Exchange)를 적용하여 보안 강화.
- RBAC (Role-Based Access Control):
    - Filter 레벨: HttpSecurity 설정에서 URL 패턴별 권한 제한.
    - Method 레벨: @PreAuthorize 등을 사용하여 서비스 레이어에서 세밀한 권한 검증.

---

## 2. 계정 잠금 및 알림 시스템 로직

### 계정 잠금 메커니즘

- DaoAuthenticationProvider가 로그인 시도 시 UserDetails의 isAccountNonLocked()를 체크.
- 로그인 실패 시 커스텀 핸들러 또는 리스너에서 실패 횟수를 카운트.
- 실패 횟수가 임계치(예: 5회)를 초과하면 DB의 is_locked 상태를 true로 변경.

### 알림 기능 (Notification)

- Spring의 ApplicationEventPublisher를 활용하여 비동기 이벤트 구조 설계.
- 로그인 실패/계정 잠금 발생 시 이벤트를 발행.
*EventListener가 이를 감지하여 사용자 이메일이나 SMS로 보안 경고 알림 발송 로직 트리거.

---

## 3. OAuth2 기반 자체 JWT 발급 흐름

### 인증 프로세스 고도화

- Provider 정보 수집: OAuth2 제공자로부터 받은 유저 정보를 CustomOAuth2UserService에서 정제.
- CustomSuccessHandler: OAuth2 인증 성공 직후 실행.
    1. Provider에서 받은 고유 ID(sub)를 바탕으로 DB 유저 조회 또는 자동 회원가입.
    2. 서버 전용 Access Token과 Refresh Token 생성.
    3. 클라이언트에 토큰 응답 (RTR 적용을 위해 Refresh Token은 Redis에 저장).
- 통합 인증 체계: 이후 클라이언트는 소셜 로그인 여부와 관계없이 서버가 발급한 자체 JWT로만 API 요청 수행.

---

## 4. Spring Security 필터 및 관리자(Manager) 매칭 구조

여러 로그인 방식이 공존할 때, 각 단계별 컴포넌트의 역할과 서비스 간의 매칭 과정입니다.

### 인증 필터 및 매니저 (Authentication)

- UsernamePasswordAuthenticationFilter: 일반 로그인(ID/PW) 요청을 가로채어 AuthenticationManager(ProviderManager)에게 전달.
- OAuth2LoginAuthenticationFilter: OAuth2 인증 프로세스 처리 후 인증 객체 생성.
- JwtAuthenticationFilter (Custom): 모든 보안 요청의 헤더에서 JWT를 추출하여 검증하고 SecurityContextHolder에 인증 정보를 저장.
- AuthenticationManager: 등록된 여러 AuthenticationProvider(Dao, OAuth2 등) 중 적합한 것을 찾아 실제 인증 로직 수행.

### 인가 관리자 및 서비스 매칭 (Authorization)

- Filter 단 (Request Authorization):
    - AuthorizationFilter가 모든 요청을 검사.
    - AuthorizationManager가 현재 사용자의 GrantedAuthority와 설정된 requestMatchers를 대조하여 접근 허용 여부 결정.
- Service 단 (Method Security):
    - @PreAuthorize 등의 어노테이션이 붙은 메서드 호출 시 MethodSecurityAuthorizationManager가 작동.
    - AOP를 통해 서비스 로직 실행 전 현재 사용자의 권한을 인터셉트하여 최종 검증.
