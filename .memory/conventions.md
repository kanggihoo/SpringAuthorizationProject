# Project Conventions & AI Directives

이 문서는 프로젝트 전체를 관통하는 핵심 코딩 표준과 AI가 반드시 지켜야 할 규칙을 정의합니다.

## 1. 개발/테스트 방법론 (TDD)

- **Strict TDD (4단계)**: `껍데기(인터페이스/빈메서드) 생성` → `실패하는 테스트 작성(Red)` → `최소 구현으로 통과(Green)` → `리팩토링`.
- 절대 테스트 코드 없이 프로덕션 코드를 먼저 작성하지 않습니다.

## 2. Spring Boot 4.0.2 특화 규칙

- **Mocking**: `@MockBean`, `@SpyBean`은 폐기되었습니다. **반드시 `@MockitoBean`, `@MockitoSpyBean`을 사용**합니다. (import: `org.springframework.test.context.bean.override.mockito.MockitoBean`)
- **WebMvcTest**: `MockMvc` 대신 **`MockMvcTester`와 AssertJ 스타일**을 조합하여 검증합니다.
- **단위 테스트**: Service 계층 테스트는 Spring Context를 띄우지 않는 순수 단위 테스트(`@Mock`, `@InjectMocks`)를 원칙으로 합니다.

## 3. Agent Skills (필수 참고)

- **테스트 코드 작성 시**: 최신 버전(Spring Boot 4, Spring Security 7)에 맞는 정확한 코드를 작성하기 위해 **`spring-boot-testing` 스킬과 `spring-security-7` 스킬을 반드시 최우선으로 참고**하여 적용합니다.
- **Git Commit 메시지 작성 시**: 커밋을 수행하거나 메시지를 작성할 때는 반드시 **`git-commit` 스킬을 활용**하여 프로젝트의 일관된 커밋 규칙을 따릅니다.

## 4. 아키텍처 및 디자인 패턴

- **Stateless**: 어떠한 경우에도 세션(`HttpSession`)을 활용한 인증/상태 저장을 하지 않습니다. `SessionCreationPolicy.STATELESS` 유지.
- **의존성 주입**: 생성자 주입만 허용하며 `@Autowired`는 사용하지 않습니다. (Lombok `@RequiredArgsConstructor` 활용)
- **Entity & DTO 분리**: Entity를 Controller의 응답으로 절대 직접 반환하지 않습니다. 반드시 DTO로 변환합니다.
- **Entity 무결성**: 무의미한 `@Setter`를 전면 금지합니다. 상태 변경은 의미 있는 비즈니스 메서드(ex: `incrementFailureCount()`)를 통해 수행합니다.

## 5. 문서화 및 코드 스타일

- **주석 (필수)**: 모든 클래스와 복잡한 비즈니스 메서드에는 **한국어 JavaDoc** 및 인라인 주석을 작성합니다.
- **테스트 명세**: 테스트 코드 메서드에는 반드시 `@DisplayName`을 사용하여 한국어로 테스트 목적을 명확히 적습니다.
- **명명 규칙**: Redis 키는 대문자 접두사와 콜론(`:`)을 조합합니다. (예: `RT:{username}`, `BL:{accessToken}`)
