# AuDDum

연애 고민 선택형 투표 서비스 **AuDDum(어떰)** 의 백엔드. Java 25, Spring Boot 4.1, JPA, MySQL 8.4. 설계는 `docs/`에 있고, 이 파일에는 문서 지도와 하네스 포인터만 둔다.

## 문서 지도

| 알고 싶은 것 | 문서 |
|---|---|
| 정책·예외 응답·상태 코드(P-xx) | `docs/예외-정책.md` |
| 용어, 애그리거트, 행위, 규칙, 상태 전이 | `docs/도메인-모델.md` |
| 엔드포인트, 요청·응답, 오류 코드 | `docs/API-명세.md` |
| 로그인·토큰·익명 쿠키·중복 참여 | `docs/인증-중복-참여.md` |
| 패키지 구조, 서비스·응답 규칙 | `docs/아키텍처.md` |
| 테이블·제약·인덱스 | `docs/ERD.md` |
| 브랜치·커밋·PR·로컬 환경 | `docs/개발-가이드.md` |
| 마일스톤과 완료 기준 | `docs/로드맵.md` |

문서는 필요한 **절만** 읽는다. 전체를 통째로 읽지 않는다. 절 색인은 `.claude/skills/auddum-conventions/SKILL.md` §1에 있다.

## 오케스트레이터 포인터

**이슈(기능) 작업 중 팀의 도움이 필요한 요청**이면 `auddum-team` 스킬을 따른다. 모드 선택, Phase 순서, 위험도별 호출 범위, 사용자 확인 지점은 전부 `.claude/skills/auddum-team/SKILL.md`에 있다.

이 파일에는 포인터만 둔다.

## 트리거 규칙

- 이슈 시작("#12 시작하자"), 테스트 먼저 써 달라는 요청, 브랜치 리뷰 요청, 커밋·push·PR 요청 → `auddum-team`
- "리뷰 다시", "이 테스트만 다시", "PR 본문만 다시" 같은 부분 재실행도 `auddum-team`

팀은 **테스트와 리뷰만** 한다(test-writer, domain-reviewer). 서비스·컨트롤러·도메인 모델 코드·마이그레이션 같은 구현은 전부 사용자가 쓴다.

비발동: 구현 코드(서비스·컨트롤러·도메인 모델 코드·마이그레이션) 대신 작성, docs에 없는 정책·행위 추가, 요청 없는 선작성, main/develop 직접 커밋·push, PR 머지, 배포, 개념 질문, 오타·한 줄 수정. 그냥 답한다.

## 규칙(요약)

- 기준은 docs다. 코드가 docs와 다르면 코드를 고친다. 동작이 바뀌면 같은 PR에서 docs를 고친다.
- 브랜치는 git flow(`feature/{이슈번호}-{요약}` ← `develop` ← `main`), release 브랜치는 없다. `main`·`develop`에 직접 커밋하지 않는다.
- 테스트 메서드 이름은 한글이고 `@DisplayName`은 쓰지 않는다.
- 빌드·테스트: `./gradlew build`, `./gradlew test`

## 구성

| 종류 | 위치 | 개수 |
|---|---|---|
| 오케스트레이터 스킬 | `.claude/skills/auddum-team/` | 1 |
| 지원 스킬 | `.claude/skills/auddum-conventions/` | 1 |
| 에이전트 | `.claude/agents/` (test-writer, domain-reviewer) | 2 |
| 중간 산출물 | `_workspace/<run-id>/` | 실행마다 (run-id = 현재 브랜치 이름에서 `feature/`를 뗀 것, `/`는 `-`로) |
| 최종 산출물 | 레포 작업 트리(기능 브랜치 커밋) + GitHub PR | 실행마다 |

실행 모드는 서브에이전트 모드로 고정되어 있다 (`.claude/settings.json`에서 `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=0`).

## 변경 이력

- 2026-10-13 — M1 문서 동기화. 이슈 #1·#2·#3·#7·#8을 병합한 뒤 개발 가이드(PR 템플릿·CI·포맷·로컬 환경·의존성), 테스트·배포 설계서(프로파일), git-workflow(`application-local.yaml` 커밋 허용)를 실제 구현에 맞췄다. 앞으로 이슈를 끝낼 때 같은 PR에서 어긋난 문서를 함께 고친다.

- 2026-10-12 — 이슈 #26. 팀의 역할을 테스트 작성과 리뷰로 한정했다(구현·도메인 모델 코드·마이그레이션은 사용자). 토큰 예산 때문에 작게 시작: 에이전트 2(test-writer, domain-reviewer), 오케스트레이터 1, 지원 스킬 1. 코드 리뷰어는 만들지 않고 고위험 이슈에서 내장 `/code-review`를 쓴다. elcom-server 하네스의 구조(산출물 계약, 고정 지점, 책임 분리)를 줄여서 따랐다.
