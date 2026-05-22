# Auth Security Lab

Spring Security 기반 인증/인가 시스템에서 보안 실패 시나리오를 재현 가능한 테스트와 evidence로 검증하는 컨텍스트다. 이 프로젝트의 중심 질문은 "로그인이 되는가"가 아니라 "토큰 탈취, 로그아웃, 권한 오용, 계정 잠금 같은 실패 조건을 방어 정책으로 증명할 수 있는가"이다.

## Language

### Project Planning

**Phase**:
포트폴리오와 evidence에서 사용하는 보안 목표 단위.
_Avoid_: Step, 작업 묶음

**Step**:
`docs/plans/`에 남기는 구현 계획과 작업 이력 단위.
_Avoid_: Phase, 보안 목표

**Security Scenario**:
공격 또는 실패 조건과 기대 방어 결과를 한 쌍으로 정의한 검증 대상.
_Avoid_: 기능 테스트, 단순 케이스

**Evidence**:
보안 시나리오가 재현 가능한 테스트 실행 결과로 검증됐음을 보여주는 공식 기록.
_Avoid_: 후기, 작업 로그

**Evidence Matrix**:
`docs/evidence.md`에서 Phase별 보안 주장, 시나리오, 기대 결과, 대상 테스트, 상태, 검증 메모를 관리하는 판정표.
_Avoid_: 발표 자료, 설명 문서

**Portfolio Evidence Document**:
`docs/portfolio/`에 두는 외부 공유용 해설 문서. Phase 요약, Evidence Matrix 발췌, Mermaid 흐름도, "무엇을 증명하는가" 설명을 담을 수 있다.
_Avoid_: 완료 판정 원본, 별도 Phase evidence 원장

**Phase Completion Criteria**:
Phase 완료를 주장하기 전에 통과해야 하는 필수 보안 테스트 목록.
_Avoid_: 체크리스트, TODO

**Coverage Target**:
현재 HEAD의 코드베이스 전체 기준으로 확인하는 JaCoCo line coverage 품질 기준.
_Avoid_: Phase별 커버리지, 과거 브랜치 커버리지

### Authentication

**User**:
LOCAL 회원가입 사용자와 OAuth2 사용자를 모두 포괄하는 서비스 계정.
_Avoid_: Member, Account

**Local User**:
서비스가 직접 받은 username과 password로 인증하는 사용자.
_Avoid_: 일반 회원, Form User

**OAuth2 User**:
외부 OAuth2 Provider의 사용자 정보를 서비스 User에 연결한 사용자.
_Avoid_: Social User, Google User

**Authenticated User**:
Local User와 OAuth2 User가 인증 후 Protected API에서 같은 방식으로 사용되도록 표현한 서비스 User.
_Avoid_: Principal, CustomUserDetails, OAuth2User

**Auth Provider**:
User가 어떤 인증 출처로 생성됐는지 나타내는 분류.
_Avoid_: Login Type, Provider Type

**Role**:
서비스 내부 인가 판단에 사용하는 권한 이름.
_Avoid_: Permission, Grade

**Protected API**:
유효한 서비스 Access Token과 필요한 Role이 있어야 접근 가능한 API.
_Avoid_: Secure API, Private API

**Auth Failure**:
인증, 토큰, User 상태 검증이 실패했을 때 서비스가 반환하고 기록할 수 있도록 이름 붙인 실패 종류.
_Avoid_: RuntimeException, IllegalArgumentException, Error

### Token Model

**Access Token**:
Protected API 접근에 사용하는 짧은 수명의 서비스 JWT.
_Avoid_: AT, 로그인 토큰

**Refresh Token**:
Access Token 재발급에 사용하는 긴 수명의 서비스 JWT.
_Avoid_: RT, 재발급 토큰

**Refresh Token Rotation**:
Refresh Token 사용 시 새 Refresh Token으로 교체하고 이전 토큰을 더 이상 허용하지 않는 정책.
_Avoid_: RTR만 단독 사용

**Token Store**:
서비스가 Refresh Token과 차단된 Access Token 상태를 저장하는 인증 상태 저장소.
_Avoid_: Session Store, Token DB

**Token Delivery**:
서비스와 브라우저 사이에서 Access Token과 Refresh Token을 전달하는 방식과 저장 위치를 정하는 정책.
_Avoid_: Token Store, Client Storage

**Logout Blacklist**:
로그아웃된 Access Token을 남은 만료 시간 동안 거부하기 위한 차단 목록.
_Avoid_: Ban List, Deny List

**Blacklisted Access Token**:
서명과 만료 시간이 유효하더라도 로그아웃으로 인해 더 이상 인증에 사용할 수 없는 Access Token.
_Avoid_: Expired Token, Invalid Token

**JWT Subject**:
서비스 JWT에서 토큰 소유자를 식별하는 값이며 현재는 username을 사용한다.
_Avoid_: userId, accountId

### OAuth2

**OAuth2 Callback**:
외부 Provider 인증 성공 후 Spring Security가 서비스로 돌려받는 요청.
_Avoid_: Redirect API, Login Callback

**OAuth2 Success Handler**:
OAuth2 Callback 성공 후 서비스 JWT 발급과 프론트엔드 리다이렉트를 책임지는 컴포넌트.
_Avoid_: OAuth Login Controller

**One-time Code Exchange**:
OAuth2 성공 후 Access Token을 URL에 직접 싣지 않고 단기 코드를 토큰으로 교환하는 전달 방식.
_Avoid_: Fragment Token Delivery, Query Token Delivery

### Account Protection

**Account Lock**:
연속 로그인 실패 등 위험 조건에서 User의 인증 가능 상태를 차단하는 정책.
_Avoid_: User Ban, Disabled

**Admin Recovery**:
관리자 권한으로 잠긴 User의 인증 가능 상태를 복구하는 흐름.
_Avoid_: Unlock Button, Reset

**Security Audit Event**:
로그인 실패, 계정 잠금, 토큰 재사용 등 보안상 의미 있는 사건의 기록 단위.
_Avoid_: Log, Alarm

**Redis Failure Policy**:
Redis 장애 시 인증 기능별로 차단할지 허용할지 정하는 정책.
_Avoid_: Redis 예외 처리, 장애 대응

## Relationships

- A **User** has one **Auth Provider** and one or more **Roles**.
- A **Local User** is a **User** whose **Auth Provider** is LOCAL.
- An **OAuth2 User** is a **User** linked through an external Provider identifier.
- An **Authenticated User** represents either a **Local User** or an **OAuth2 User** after service authentication.
- An **Authenticated User** must be enabled and not under **Account Lock** before accessing a **Protected API**.
- An **Auth Failure** has a stable code and HTTP status so each **Security Scenario** can assert the expected failure mode.
- A login issues one **Access Token** and one **Refresh Token**.
- A **Refresh Token** belongs to exactly one **JWT Subject**.
- The **Token Store** stores one active **Refresh Token** per **JWT Subject** in the current policy.
- **Token Delivery** sends Refresh Tokens only as HttpOnly cookies and keeps browser-readable Access Tokens out of persistent storage.
- **Refresh Token Rotation** replaces the active **Refresh Token** for the same **JWT Subject**.
- A logout removes the active **Refresh Token** and adds the current **Access Token** to the **Logout Blacklist**.
- A **Blacklisted Access Token** must not authenticate a **Protected API** request.
- **Account Lock** must invalidate Protected API access even when the caller still has an unexpired **Access Token**.
- An unexpired **Access Token** must not bypass the **Authenticated User** state check.
- **Admin Recovery** reverses **Account Lock** but does not grant additional **Roles**.
- A **Security Scenario** produces **Evidence** through an automated test or documented execution result.
- The **Evidence Matrix** in `docs/evidence.md` is the source of truth for Phase completion status.
- A **Portfolio Evidence Document** may explain one Phase with diagrams and summaries, but it must not define a separate completion status.
- A **Phase** groups security goals; a **Step** records implementation work toward one or more Phases.
- A **Phase Completion Criteria** lists the required tests for one **Phase** before implementation is considered complete.
- The **Coverage Target** is checked on the current HEAD codebase as a whole; it is not a per-Phase completion criterion.

## Example dialogue

> **Dev:** "Phase 2가 끝났다고 말해도 될까요?"
> **Domain expert:** "아직 아니요. Refresh Token은 Redis에 저장되지만 Blacklisted Access Token이 JWT 필터에서 거부되는 evidence가 있어야 Phase 2라고 부를 수 있습니다."
>
> **Dev:** "OAuth2 로그인도 같은 Token Store를 쓰나요?"
> **Domain expert:** "목표는 그렇습니다. 현재 코드가 PostgreSQL RefreshToken을 참조한다면 그건 구현 차이로 표시하고 Step 4-A에서 닫아야 합니다."

## Flagged Ambiguities

- "Phase"와 "Step"이 섞여 쓰였다. 해결: **Phase**는 보안 목표와 evidence 분류, **Step**은 `docs/plans/`의 구현 이력으로 구분한다.
- "User", "Account", "Member"가 같은 의미로 쓰일 수 있다. 해결: 도메인 용어는 **User**로 통일하고, 잠금 정책을 말할 때만 **Account Lock**을 사용한다.
- Token Store 키의 소유자가 계획 초기에는 `userId`로 표현됐고 인수인계와 현재 코드는 `username`을 사용한다. 해결: 현재 정책은 **JWT Subject**인 username을 canonical owner로 본다.
- "Logout Blacklist 구현"은 등록과 차단을 모두 포함해야 한다. 현재 코드 기준 등록은 `AuthServiceImpl.logout()`에 있고, JWT 필터 차단은 별도 구현/evidence가 필요하다.
- OAuth2의 Refresh Token 저장소는 **Token Store**로 통일했다. 현재 `OAuth2AuthenticationSuccessHandler`는 `TokenLifecycleService`를 통해 서비스 JWT를 발급한다.
- Local User와 OAuth2 User의 Spring Security principal Adapter 이름이 다르더라도 도메인에서는 **Authenticated User**로 부른다. 해결: Protected API 인증 시 enabled, **Account Lock**, **Role** 판단은 **Authenticated User** 상태 확인으로 표현한다.
- 인증 실패, Refresh Token 재사용, User 상태 실패를 일반 예외 이름으로 부르면 의미가 흐려진다. 해결: **Auth Failure** code로 표현하고 HTTP status 매핑은 한 곳에서 관리한다.
- "80% 커버리지"와 Phase 완료 기준이 섞여 보일 수 있다. 해결: 80%는 현재 HEAD 전체 코드베이스의 품질 기준이고, Phase 완료는 해당 Phase의 **Phase Completion Criteria** 통과 여부로 판단한다.
- "Phase별 evidence 파일을 만들지 않는다"는 규칙과 `docs/portfolio/phase*.md` 문서가 충돌해 보일 수 있다. 해결: 완료 판정 원본은 항상 `docs/evidence.md`의 **Evidence Matrix**이고, `docs/portfolio/` 문서는 외부 공유를 위한 해설과 시각 자료만 담는다.
