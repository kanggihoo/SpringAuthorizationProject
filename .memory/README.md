# Memory

이 디렉토리는 AI 세션 재개용 압축 메모리다.

`docs/`의 긴 설계 문서, 구현 기록, provider guide를 복사하지 않는다.
새 세션에서는 이 디렉토리로 현재 위치를 빠르게 잡고, 필요한 경우에만 관련 `docs/` 파일을 읽는다.

## 읽는 순서

1. `current-state.md`
2. `tasks.md`
3. `decisions.md`
4. `conventions.md`a

## 문서 역할

- `current-state.md`: 현재 프로젝트 단계, 바로 이어서 볼 문서, 주의할 점
- `tasks.md`: 다음에 실행할 작업 큐
- `decisions.md`: 반복하면 안 되는 설계/테스트 결정
- `conventions.md`: 이 프로젝트에서 AI가 따라야 하는 작업 규칙

## 운영 규칙

- 세션 시작 시 `.memory/`를 먼저 읽고, 필요한 `docs/`만 선별해서 읽는다.
- 세션 종료 시 `current-state.md`와 `tasks.md`만 우선 갱신한다.
- 설계 판단이 바뀌었을 때만 `decisions.md`를 갱신한다.
- 완료된 구현 세부 설명은 git history와 source code를 신뢰한다.
