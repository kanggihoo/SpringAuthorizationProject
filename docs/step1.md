## 1단계: Core Security & JPA 기반 기본 인증

### 1. 환경 설정 및 기술 스택

- **Framework**: Spring Boot 3.x, Spring Data JPA.
- **Database**: PostgreSQL (Docker 또는 로컬 설치).
- **Validation**: `@NotBlank`, `@Size` 등을 이용한 회원가입 입력값 검증.
- **Test Tool**: **Postman** (세션 쿠키 유지를 통한 인증 상태 테스트).

### 2. 도메인 설계 (3-Table Structure)

휘발성 데이터(실패 횟수 등)를 배제하고 계정의 정적 상태와 권한 관계에만 집중합니다.

- **User 엔티티**:
  - 계정 정보: `username`(ID), `password`(암호화), `nickname`(별명).
  - 상태 필드: `enabled`, `accountNonLocked` (초기값: true).
- **Role 엔티티**:
  - 권한 정보: `name` (예: `ROLE_USER`, `ROLE_ADMIN`).
- **User_Roles (중간 매핑 테이블)**:
  - 유저와 권한의 N:M 관계를 해소하는 교차 테이블.
  - JPA `@ManyToMany` 설정을 통해 물리적인 테이블 관리 최소화.

### 3. 인증 및 회원 관리 로직

- **회원가입 (직접 구현)**:
  - `/signup` 엔드포인트를 열고, `BCryptPasswordEncoder`로 비밀번호를 해싱하여 DB에 저장.
  - 가입 시 기본적으로 `ROLE_USER` 권한을 매핑.
- **비밀번호 암호화**: Security 설정에서 `BCryptPasswordEncoder`를 빈으로 등록하여 인증 과정에서 자동 활용.
- **CustomUserDetailsService**:
  - DB에서 `User` 정보를 가져와 Security의 `UserDetails` 구현체(닉네임 포함)로 변환.
- **내장 세션 인증 (Form Login)**:
  - Spring Security의 기본 `/login` 프로세스 활용.
  - Postman 요청 시 `x-www-form-urlencoded` 형식으로 ID/PW 전송.

### 4. 인가 및 권한 제어

- **URL 권한 제어**: `HttpSecurity` 설정에서 `requestMatchers` 활용.
  - `/admin/**`: `ROLE_ADMIN` 전용.
  - `/user/**`: `ROLE_USER` 이상(ADMIN 포함) 접근 가능.
- **Role Hierarchy (권한 계층)**:
  - 애플리케이션 설정에서 `ROLE_ADMIN > ROLE_USER` 계층 정의.
  - ADMIN 계정으로 로그인 시 별도의 추가 매핑 없이 USER 권한 페이지 접근 보장.

### 5. Postman 테스트 시나리오

1. **회원가입**: `POST /signup` (JSON 데이터 전송) → DB 저장 확인.
2. **로그인**: `POST /login` (Body: x-www-form-urlencoded) → 응답 헤더의 `JSESSIONID` 확인.
3. **인가 테스트**: `GET /user/profile` 또는 `GET /admin/manage` 호출 시 Postman이 자동으로 쿠키를 실어 보내는지 확인 및 `200 OK` 또는 `403 Forbidden` 결과 검증.
