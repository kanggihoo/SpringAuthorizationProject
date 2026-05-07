**리뷰 결과**
현재 테스트는 기본 골격이 좋습니다. 다만 “일반 로그인과 OAuth2 로그인이 결국 동일한 JWT 기반 인증/권한 흐름으로 합류한다”는 핵심 시나리오를 자동으로 보장하는 테스트가 부족합니다.

**우선 추가할 테스트**
| 우선순위 | 시나리오 | 추천 레벨 | 이유 |
|---|---|---|---|
| 1 | 일반 로그인 -> RT Redis 저장 -> `/refresh` RTR -> `/logout` -> blacklist 후 보호 API `401` | 통합 테스트 | 여러 컴포넌트가 연결되는 핵심 인증 흐름 |
| 2 | OAuth2 최초 로그인 시 신규 유저 생성, `provider=GOOGLE`, `providerId`, `email`, `ROLE_USER` 저장 | 단위 또는 JPA 포함 통합 | 과거에 실제 장애가 있었던 영역 |
| 3 | OAuth2 기존 유저 재로그인 시 중복 row 생성 없이 기존 유저 재사용 | 단위 또는 JPA 포함 통합 | 현재 수동 검증 대상이며 회귀 위험 큼 |
| 4 | OAuth2 성공 후 발급된 AT로 `/user/profile`, `/admin/manage` 권한 검증 | 통합 테스트 | OAuth2와 JWT 권한 모델 연결 검증 |
| 5 | blacklist된 AT로 보호 API 접근 시 `401` | Security slice 또는 통합 | 현재 필터 단위는 있지만 HTTP 결과 보장이 약함 |
| 6 | `/refresh`에서 만료/변조/Redis 불일치 RT가 각각 실패하는 HTTP 응답 | WebMvc slice + 서비스 단위 | RTR 보안 정책의 API 계약 검증 |
| 7 | logout 요청에 Authorization 헤더가 없거나 Bearer 형식이 아닐 때 동작 | WebMvc slice 또는 서비스 단위 | 현재 `accessToken=null` 가능성이 있음 |
| 8 | OAuth2 state cookie 저장/로드/삭제 | 단위 테스트 | stateless OAuth2에서 CSRF 방어 핵심 |
| 9 | `CustomUserDetailsService`가 locked/disabled 계정 상태를 반영하는지 | 단위 테스트 | Step 4-B 계정 잠금으로 이어질 기반 |
| 10 | 실제 `SecurityConfig` 기준 공개/보호/관리자 endpoint 정책 | Security 통합 테스트 | 테스트 전용 config와 실제 config 괴리 방지 |

**현재 테스트 상태**
- [AuthControllerTest.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/test/java/org/example/controller/AuthControllerTest.java):49  
  Controller slice 테스트가 `/signup`, `/login`, `/refresh`, `/logout`의 HTTP 입출력과 쿠키 설정을 검증하고 있습니다. 역할은 적절합니다.

- [AuthServiceImplTest.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/test/java/org/example/service/AuthServiceImplTest.java):41  
  로그인, 로그아웃, refresh의 핵심 서비스 로직을 Mockito 단위 테스트로 검증하고 있습니다. Redis 저장 호출과 RTR 불일치 검증이 이미 있습니다.

- [JwtAuthenticationFilterTest.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/test/java/org/example/security/jwt/JwtAuthenticationFilterTest.java):1  
  JWT 필터 단위 테스트가 유효 토큰, blacklist, Authorization header 없음, 제외 경로, 변조 토큰을 커버합니다. 이건 유지하는 게 좋습니다.

- [JwtSecurityIntegrationTest.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/test/java/org/example/security/jwt/JwtSecurityIntegrationTest.java):27  
  보호 API 접근, `401`, `403`, admin 접근을 검증합니다. 다만 실제 전체 인증 흐름보다는 JWT 보안 slice에 가깝습니다.

- [OAuth2AuthenticationSuccessHandlerTest.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/test/java/org/example/security/oauth2/OAuth2AuthenticationSuccessHandlerTest.java):27  
  OAuth2 성공 핸들러의 RT Redis 저장, RT 쿠키, accessToken fragment redirect를 검증합니다. 좋은 단위 테스트입니다.

**단위 테스트로 추가할 것**
- [CustomOAuth2UserService.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java):51  
  `loadUser`는 현재 직접 테스트가 필요합니다. 단, `super.loadUser(userRequest)`가 외부 provider 호출 구조라 순수 단위 테스트가 어렵습니다. 가능하면 provider user-info 파싱/등록 로직을 별도 메서드나 컴포넌트로 분리한 뒤 테스트하는 게 좋습니다.

- [CustomOAuth2UserService.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java):76  
  `findByProviderAndProviderId`로 기존 유저를 찾으면 `userRepository.save`가 호출되지 않아야 합니다.

- [CustomOAuth2UserService.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CustomOAuth2UserService.java):95  
  신규 OAuth2 유저 생성 시 `username = GOOGLE_{providerId}`, `provider = GOOGLE`, `email`, `providerId`, `ROLE_USER`가 들어가야 합니다.

- [CookieOAuth2AuthorizationRequestRepository.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/security/oauth2/CookieOAuth2AuthorizationRequestRepository.java):67  
  OAuth2 authorization request cookie 저장/로드/삭제 테스트가 필요합니다. 이건 Spring context 없이 `MockHttpServletRequest`, `MockHttpServletResponse`로 단위 테스트가 적합합니다.

- [AuthServiceImpl.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/service/AuthServiceImpl.java):68  
  `logout(null accessToken)` 또는 Authorization 헤더 누락 상황을 어떻게 처리할지 명확히 해야 합니다. 현재 controller가 인증된 사용자라도 Bearer 헤더가 없으면 `authService.logout(username, null)`을 호출할 수 있습니다.

**Slice 테스트로 추가할 것**
- [AuthControllerTest.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/test/java/org/example/controller/AuthControllerTest.java):111  
  `/login` 실패 시나리오가 부족합니다. `AuthenticationException` 발생 시 응답 상태가 의도대로 나오는지 검증해야 합니다.

- [AuthControllerTest.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/test/java/org/example/controller/AuthControllerTest.java):168  
  `/refresh`에서 `authService.refresh`가 `IllegalArgumentException`을 던질 때 HTTP 응답이 어떻게 되는지 테스트가 필요합니다. 현재 cookie 없음 `400`만 있습니다.

- [AuthControllerTest.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/test/java/org/example/controller/AuthControllerTest.java):72  
  `/logout`에서 Authorization 헤더가 없거나 `Bearer ` prefix가 없을 때의 계약을 정해야 합니다. 보안적으로는 `400` 또는 `401`이 더 명확합니다.

**통합 테스트로 추가할 것**
- [SecurityConfig.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java):82  
  실제 `SecurityConfig`를 사용하는 통합 테스트가 필요합니다. 현재 일부 테스트는 테스트 전용 security config를 사용하므로 실제 필터 순서, permitAll, logout disable, OAuth2 설정과 차이가 날 수 있습니다.

- [SecurityConfig.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java):104  
  공개 endpoint: `/`, `/signup`, `/login`, `/refresh`, `/oauth2/**`, `/login/oauth2/**`가 실제로 permitAll인지 검증할 수 있습니다.

- [SecurityConfig.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java):112  
  `/admin/**`는 `ROLE_ADMIN`만 접근 가능해야 합니다.

- [SecurityConfig.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/config/SecurityConfig.java):113  
  `/user/**`는 `ROLE_USER`, `ROLE_ADMIN` 접근 가능해야 합니다.

- [AuthServiceImpl.java](/C:/Users/SSAFY/Desktop/SpringAuthorizationProject/src/main/java/org/example/service/AuthServiceImpl.java):39  
  일반 로그인 end-to-end 성격 테스트는 `@SpringBootTest` + MockMvc + Testcontainers PostgreSQL/Redis 조합이 적합합니다. 회원가입, 로그인, refresh, logout, blacklist 후 `401`까지 한 번에 검증하는 테스트를 1개만 두는 게 좋습니다.

**추천 테스트 분류**
- 단위 테스트: `AuthServiceImpl`, `UserServiceImpl`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `OAuth2AuthenticationSuccessHandler`, `CookieOAuth2AuthorizationRequestRepository`
- Slice 테스트: `AuthController`, `GlobalExceptionHandler`, 보안 endpoint별 HTTP 응답
- 통합 테스트: 실제 DB/Redis/필터체인을 포함한 로그인 플로우, OAuth2 유저 저장/재사용, blacklist 후 보호 API 차단

**작성 순서 추천**
1. `CookieOAuth2AuthorizationRequestRepositoryTest` 추가
2. `CustomOAuth2UserService` 테스트 가능하도록 OAuth2 유저 등록 로직 분리 또는 테스트 보강
3. `/refresh`, `/logout`, `/login` 실패 응답 slice 테스트 추가
4. 실제 `SecurityConfig` 기반 권한 정책 통합 테스트 추가
5. 일반 로그인 전체 흐름 통합 테스트 추가
6. OAuth2 유저 신규 생성/재사용 통합 테스트 추가

테스트 코드를 바로 작성한다면, 먼저 1번과 3번이 가장 비용 대비 효과가 큽니다. 이후 5번 통합 테스트로 실제 인증 흐름을 고정하는 순서가 좋습니다.