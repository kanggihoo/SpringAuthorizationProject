# Conventions

## Memory

- `.memory/`는 짧은 handoff 용도다.
- 긴 구현 설명은 `docs/`에 둔다.
- 완료된 세부 구현은 source code와 git history에서 확인한다.
- `docs/memory/project-status.md`처럼 오래된 상태 문서가 있으면 최신 Step 문서를 우선한다.
- 작업 종료 시 `current-state.md`와 `tasks.md`를 갱신한다.

## Project Style

- 한국어 JavaDoc과 `@DisplayName` 스타일이 기존 문서의 기본 방향이다.
- constructor injection을 사용한다.
- Controller는 얇게 유지한다.
- Entity와 DTO를 분리한다.
- Entity를 API response로 직접 반환하지 않는다.
- Entity의 무분별한 setter 대신 비즈니스 메서드로 상태를 바꾼다.
- 예외는 `GlobalExceptionHandler`, Security entry point/denied handler, filter exception delegation 구조와 충돌하지 않게 처리한다.

## Security

- 세션 기반 인증으로 되돌리지 않는다.
- 기본 정책은 stateless JWT다.
- AT/RT 책임을 명확히 분리한다.
- RT는 HttpOnly cookie로 전달한다.
- 로그아웃은 RT 삭제와 AT blacklist 등록을 모두 수행해야 한다.
- OAuth2 callback/state는 session이 아니라 cookie repository 기준으로 본다.
- 새 OAuth2 provider 추가 시 core auth flow보다 `OAuth2UserInfo` 구현체와 factory 확장을 우선한다.

## Testing

- 구현 없이 import 에러만 나는 테스트를 Red로 보지 않는다.
- 한 번에 하나의 원인만 수정하고 검증한다.
- 예상과 다른 HTTP status가 나오면 먼저 response header, 특히 `Location`을 확인한다.
- `@AuthenticationPrincipal CustomUserDetails` 테스트에는 `@WithMockUser`를 무리하게 쓰지 않는다.
- controller slice에서 JWT 필터까지 검증하려 하지 않는다.
- JWT 필터 검증은 별도 security/integration test로 둔다.
- `TokenResponseDto.refreshToken`이 `@JsonIgnore`이면 response body가 아니라 cookie/header로 검증한다.

## Commands

- Windows 환경이므로 Gradle은 보통 `./gradlew.bat` 또는 PowerShell에서 `./gradlew`를 사용한다.
- 문서의 bash 예시는 Windows PowerShell에 맞게 조정한다.
- 테스트 실행 예시는 필요 시 checkstyle을 제외할 수 있다.

```powershell
./gradlew test --tests "org.example.security.jwt.JwtAuthenticationFilterTest" -x checkstyleMain -x checkstyleTest
./gradlew test --tests "org.example.security.oauth2.OAuth2AuthenticationSuccessHandlerTest" -x checkstyleMain -x checkstyleTest
./gradlew test --tests "org.example.exception.GlobalExceptionHandlerTest" -x checkstyleMain -x checkstyleTest
```
