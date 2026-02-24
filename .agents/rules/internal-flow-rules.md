---
trigger: manual
---

[Role & Task]
내가 제공하는 자바 코드를 분석하여, 발생 가능한 주요 시나리오별로 내부 프레임워크 수준에서 어떤 일이 벌어지는지 Deep Dive하여 설명해줘. 단순히 코드에 작성된 내용뿐만 아니라, Spring Security의 필터 체인 내부에서 호출되는 인터페이스와 구현체들의 동작 시퀀스를 상세히 기술하는 것이 목표야.

[Requirements]
1. 시나리오별 동작 분석 
단순한 코드 나열이 아니라, 다음 시나리오들을 구분하여 각각의 실행 흐름을 설명해줘.
- 인증 성공 시나리오: 올바른 자격 증명 제출 시 Authentication 객체 생성 및 저장 과정
- 인증 실패 시나리오: 잘못된 정보 입력 시 예외 처리 및 EntryPoint 호출 과정
- 인가 실패 시나리오: 인증은 되었으나 특정 자원에 대한 권한이 부족할 때의 흐름


2. 내부 동작 시퀀스 상세화
사용자 요청이 들어오는 시점부터 인증이 완료될 때까지의 과정을 Spring Security 내부 클래스 및 인터페이스명을 명시하며 설명해줘. 특히 다음 요소들이 포함되어야 해.

- SecurityFilterChain 내에서 작동하는 구체적인 Filter 명칭 (예: UsernamePasswordAuthenticationFilter 등)

- AuthenticationManager, AuthenticationProvider의 호출 흐름 및 데이터 전달 과정

- 우리가 커스텀 구현한 UserDetailsService와 프레임워크가 연결되는 지점

- 인증 성공 후 UserDetails 객체가 생성되어 Authentication 객체로 변환되고, SecurityContextHolder에 저장되는 메커니즘

2. 기본 인증 로직 분석
표준 Login Form을 통해 데이터가 입력되었을 때, 프레임워크가 기본적으로 제공하는 인증 처리 루틴을 단계별로 설명해줘.

3. 테스트 가이드 (Postman)
현재 Frontend UI가 없는 환경임을 고려하여, 각 엔드포인트별로 Postman을 통해 인증 로직을 테스트할 수 있는 구체적인 방법(Method, Header, Body 구성 등)을 작성해줘.

[Output Format]
- 시나리오별 섹션 구분: 각 동작 상황을 제목으로 나누어 가독성 있게 작성할 것.
- 시퀀스 리스트: 동작 순서를 1, 2, 3 단계별로 나열하며 관련 클래스/인터페이스 API 명칭을 명시할 것.
- Postman 테스트 가이드는 실제 요청 예시를 포함하여 가독성 있게 정리할 것.