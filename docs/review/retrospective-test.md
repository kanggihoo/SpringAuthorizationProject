# 회고: AuthControllerTest 302 문제 해결 과정

## 무엇이 문제였나

`POST /logout` 테스트에서 200을 기대했지만 302가 반환됨.

**실제 원인 (단 하나):**
`@WebMvcTest`는 `SecurityConfig`를 로드하지 않아 Spring Security 기본 `LogoutFilter`가 활성화됨.
기본 `LogoutFilter`가 `POST /logout`을 가로채서 302 리다이렉트를 반환한 것.

**해결책:**
`TestSecurityConfig`에 `.logout(AbstractHttpConfigurer::disable)` 한 줄 추가.

---

## 어디서 헤맸나

### 1. 원인 진단 실패 (가장 큰 문제)
302 응답의 `Location` 헤더를 먼저 확인했어야 했다.
`Location: /login` 이었다면 즉시 "LogoutFilter → formLogin 리다이렉트"로 진단할 수 있었다.
**→ 앞으로: 예상과 다른 상태코드가 나오면 응답 헤더(특히 Location)를 먼저 확인한다.**

### 2. 검증되지 않은 이론으로 코드를 먼저 수정
`@BeforeEach` 타이밍 문제, `springSecurity()` 연결 문제 등 가설을 세우고 코드를 바꾸기 시작했다.
실제 원인과 무관한 변경들이 누적되면서 코드가 복잡해졌다.
**→ 앞으로: 코드 수정 전에 로그/헤더로 원인을 먼저 확정한다. "확인 → 수정" 순서를 지킨다.**

### 3. @WithMockUser의 한계를 나중에 파악
`@WithMockUser`의 principal은 `String`이라 `@AuthenticationPrincipal CustomUserDetails`에 `null`이 주입된다는 사실을 미리 알고 있었음에도 시도했다.
**→ 앞으로: `@AuthenticationPrincipal`에 커스텀 타입을 쓰는 컨트롤러는 `.with(authentication(...))` 방식을 바로 선택한다.**

### 4. MockMvcTester 초기화 방식을 불필요하게 변경
`@BeforeEach` → `@Autowired MockMvc` + `MockMvcTester.create()` → 메서드 방식으로 세 번 바꿨다.
실제 문제는 MockMvcTester 초기화 방식이 아니었다.
**→ 앞으로: 원인이 확정되기 전에는 관련 없는 코드를 바꾸지 않는다.**

---

## 테스트 코드 작성 전반 회고

### 5. 프로덕션 코드를 테스트를 위해 불필요하게 수정했다

`convertTo(TokenResponseDto.class)` 실패 원인이 `@NoArgsConstructor` 부재였는데,
프로덕션 DTO에 `@NoArgsConstructor + @AllArgsConstructor`를 추가했다.

프로덕션 코드 변경은 테스트를 위한 최후 수단이어야 한다.
`@JsonDeserialize` 설정이나 테스트 전용 방법을 먼저 검토했어야 했다.

**→ 앞으로: `convertTo()` 실패 시 DTO 수정 전에 Jackson 설정 문제인지 먼저 확인한다.**

---

### 6. 스킬 권고 패턴(`convertTo`)을 실패하자 포기했다

스킬 P0 권고인 `convertTo(TokenResponseDto.class).satisfies(...)` 패턴이 실패하자
`extractingPath("$.accessToken")`으로 후퇴했다.

실패 원인(Jackson 역직렬화 문제)을 해결했다면 `convertTo`를 유지할 수 있었다.
덜 타입 안전한 방식으로 물러난 결과가 됐다.

**→ 앞으로: 스킬 권고 패턴이 실패하면 포기하지 말고 실패 원인을 해결해서 권고 패턴을 유지한다.**

---

### 7. `@Nested` 그룹핑을 추가했다가 원인을 잘못 짚어 제거했다

스킬 권고(`@Nested`로 엔드포인트별 그룹핑)를 적용했다가,
`@Nested` 클래스가 별도 컨텍스트로 인식된다고 잘못 진단해서 제거했다.

`@WebMvcTest`에서 `@Nested`는 동일 컨텍스트를 공유하므로 원래 문제가 아니었다.
의미 있는 구조 개선을 잘못된 진단으로 되돌린 것이다.

**→ 앞으로: 구조 개선(`@Nested`)과 버그 디버깅을 동시에 하지 않는다. 테스트 통과 후 구조 개선을 별도로 적용한다.**

---

### 8. Reflection 제거 후 `throws Exception` 정리를 놓쳤다

`AuthServiceImplTest`에서 Reflection 제거 후 `login_callsSaveRefreshToken()`, `login_returnsTokenResponse()`의
`throws Exception` 선언이 불필요해졌음에도 그대로 남겼다.

**→ 앞으로: Reflection 제거 같은 리팩터링 후 메서드 시그니처도 함께 정리한다.**

---

## 핵심 교훈 요약

| 상황 | 잘못된 접근 | 올바른 접근 |
|------|-------------|-------------|
| 예상과 다른 상태코드 | 가설 세우고 코드 수정 | 응답 헤더(Location 등) 먼저 확인 |
| `@WebMvcTest` + Security | Security 설정 없이 동작 가정 | 기본 FilterChain 동작 확인 필수 |
| `@AuthenticationPrincipal` 커스텀 타입 | `@WithMockUser` 시도 | `.with(authentication(...))` 바로 선택 |
| 테스트 실패 디버깅 | 여러 부분 동시에 수정 | 한 번에 한 가지만 변경하고 검증 |
| 스킬 권고 패턴 실패 | 더 낮은 수준의 대안으로 후퇴 | 실패 원인 해결 후 권고 패턴 유지 |
| 리팩터링 후 | 변경 범위만 확인 | 메서드 시그니처 등 연관 코드도 정리 |
| 구조 개선 + 디버깅 동시 진행 | 원인 오진으로 개선 사항 롤백 | 디버깅 완료 후 구조 개선을 별도로 적용 |
