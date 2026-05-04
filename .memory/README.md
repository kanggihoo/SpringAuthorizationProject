# Memory

이 디렉토리는 AI 세션 재개용 압축 메모리다.

`docs/`의 긴 설계 문서, 구현 기록, provider guide를 복사하지 않는다.
새 세션에서는 이 디렉토리로 현재 위치를 빠르게 잡고, 필요한 경우에만 관련 `docs/` 파일을 읽는다.

## 읽는 순서

1. `current-state.md`
2. `tasks.md`
3. `decisions.md`
4. `conventions.md`

## 문서 역할

- `current-state.md`: 현재 프로젝트 단계, 바로 이어서 볼 문서, 주의할 점
- `tasks.md`: 다음에 실행할 작업 큐
- `decisions.md`: 반복하면 안 되는 설계/테스트 결정
- `conventions.md`: 이 프로젝트에서 AI가 따라야 하는 작업 규칙

## 관련 긴 문서

- `docs/memory/project-context.md`: 기존 AI 메모리. 일부 오래된 정보가 있을 수 있으므로 `.memory/`를 우선한다.
- `docs/memory/project-status.md`: Step 2 기준의 오래된 상태 문서. 최신 상태 판단에는 Step 3/4 문서를 우선한다.
- `docs/archive/completed-plans/step2.md`: JWT 무상태 인증, RTR, 예외 위임 구조.
- `docs/archive/completed-plans/step3(Oauth2).md`: Google OAuth2, Docker Compose, OAuth2 추상화, Success/FailureHandler.
- `docs/plans/step4-7-roadmap.md`: Step 4~7 전체 로드맵.
- `docs/plans/step4-A-handover.md`: Step 4-A에서 바로 이어받을 위치.
- `docs/plans/step4-A-troubleshooting.md`: Step 4-A 테스트/구현 중 발생한 문제와 해결.
- `docs/review/testCodeReview.md`: 테스트 개선 방향과 채택된 결정.
- `docs/review/retrospective-test.md`: AuthControllerTest 302 문제 회고.

## 운영 규칙

- 세션 시작 시 `.memory/`를 먼저 읽고, 필요한 `docs/`만 선별해서 읽는다.
- 세션 종료 시 `current-state.md`와 `tasks.md`만 우선 갱신한다.
- 설계 판단이 바뀌었을 때만 `decisions.md`를 갱신한다.
- 완료된 구현 세부 설명은 git history와 source code를 신뢰한다.
