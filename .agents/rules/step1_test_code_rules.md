---
trigger: manual
---

### **Spring Boot + Security 인증 구현 테스트 계획 (최종본)**

### **📝 Phase 0: 테스트 환경 및 도구 세팅**

- **H2 인메모리 DB 도입**: `build.gradle`에 H2 의존성(`testRuntimeOnly 'com.h2database:h2'`)을 추가합니다.
- **테스트 전용 설정 분리**: `src/test/resources/application-test.yml`을 생성하여 H2 URL과 `spring.jpa.hibernate.ddl-auto: create-drop`을 설정하고, 테스트 클래스에 `@ActiveProfiles("test")`를 부착하여 운영/개발 DB(PostgreSQL)와 완벽히 격리합니다.
- **JaCoCo 설정**: `build.gradle`에 플러그인을 세팅하여 HTML 리포트를 생성하는 워크플로우만 구성합니다.

### **📝 Phase 1: 데이터 접근 계층 (Repository) - `@DataJpaTest` + H2**

- **`UserRepositoryTest` 단일 파일로 통합**: 불필요하게 `RoleRepositoryTest`를 분리하지 않고, 하나의 테스트에서 통합 검증합니다.
  - Role 저장 ➡️ User 생성 시 해당 Role 매핑 ➡️ User 저장 ➡️ `findByUsername()`으로 조회 시 데이터와 매핑 테이블(user_roles)이 모두 정상 유지되는지 한 번의 흐름으로 검증합니다.

### **📝 Phase 2: 도메인/서비스 계층 단위 테스트 - `@ExtendWith(MockitoExtension.class)`**

- **`UserServiceImplTest` (🌟 최우선 순위)**:
  - **정상 회원가입 로직 검증 (+ ArgumentCaptor 도입)**: `save()` 메서드 호출 횟수만 검증하는 것을 넘어, \**`ArgumentCaptor<User>`*를 사용하여 실제 저장되려던 Entity 객체를 낚아채어 검증합니다.
    - 낚아챈 `User`의 `password`가 원문이 아닌 인코딩된 문자열인지 검증
    - 함께 세팅된 닉네임, 생성된 Role 등이 기대값과 일치하는지 검증
  - **중복 검증 로직**: 동일한 아이디 등록 시 예외 발생 검증.
  - **권한 자동 생성**: 테이블에 기본 Role이 없을 시 새로 생성하는 분기 검증.
- **`CustomUserDetailsServiceTest`**:
  - `loadUserByUsername()` 성공/실패(`UsernameNotFoundException`) 케이스 검증.

### **📝 Phase 3: 웹 및 시큐리티 통합 계층 - 용도에 따른 어노테이션 명확한 분리**

가장 중요한 컨트롤러/보안 체인 테스트입니다. 목적에 따라 로드하는 빈을 분리합니다.

**1. `AuthControllerTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`)** DB 연동 및 Security 전체 필터 체인이 필요한 E2E 테스트 목적으로 사용합니다.

- **폼 로그인 성공/실패 (`formLogin()`)**: DB 조회를 동반하는 실제 인증 시나리오 검증.
- **회원가입 E2E 검증**: 정상 회원가입 완료.
- **회원가입 Validation 실패**: `@Valid` 위반 (1자 이름, 짧은 비밀번호 등) 시 `400 Bad Request` 검증.

**2. `SecurityPolicyTest` (`@WebMvcTest(TestController.class)` + `@Import(SecurityConfig.class)`)** 인가(Authorization) 정책만 집중적으로, 가볍게 검증합니다.

- `@WithMockUser(roles = "USER")`, `@WithMockUser(roles = "ADMIN")` 등을 사용하여 SecurityContext에 가짜 유저를 꽂아넣고 권한을 통제합니다.
- `/admin/manage` 접근 시 권한에 따른 허용/`403 Forbidden` 검증.
- `/user/profile` 접근 시 허용 / 비로그인 시 `/login` 리다이렉션 검증.
- **메인 페이지 뷰 분기**: `GET /` 요쳥 시, 익명 유저와 로그인 유저 각각에 대해 반환되는 응답(환영 메시지 등)이 달라지는지 검증.
