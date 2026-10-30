# 산출물 계약

## 위치와 이름

`_workspace/<run-id>/` 아래에만 쓴다. `_workspace/`는 `.gitignore`에 있다.

| 파일 | 작성자 | 내용 |
|---|---|---|
| `00_brief.md` | 오케스트레이터 | 기능(브랜치) 단위 작업 지시서 |
| `00_diff-r<k>.patch` | 오케스트레이터 | 리뷰 대상 diff(`git diff develop...HEAD`) |
| `20_test-writer_note_q<k>.md` | test-writer | 테스트 작성 결과 |
| `20_test-writer_bug-report_q<k>.md` | test-writer | 버그 보고(있을 때만) |
| `30_domain-reviewer_report_r<k>.md` | 오케스트레이터(domain-reviewer의 최종 응답을 저장) | 정책·docs 리뷰 판정 |
| `30_code-reviewer_report_r<k>.md` | 오케스트레이터(code-reviewer의 최종 응답을 저장) | 설계·품질 리뷰 판정과 설계 제안(높음 위험도만) |
| `40_delivery.md` | 오케스트레이터 | 커밋·push·PR 결과 |

`q<k>`는 요청 순번, `r<k>`는 리뷰 라운드다. 둘 다 1부터 센다. 같은 이름 파일이 있으면 덮어쓰지 않고 순번을 올린다.

## run-id

현재 브랜치 이름에서 앞의 `feature/`를 떼고 남은 `/`를 `-`로 바꾼다. 예: `feature/26-agent-harness` → `26-agent-harness`. `feature/`로 시작하지 않는 브랜치(main·develop 제외)는 이름 전체의 `/`를 `-`로 바꾼다.

## 00_brief.md 서식

```markdown
# brief: <run-id>

- 이슈: #<n> <제목> (없으면 `없음`)
- 브랜치: <이름>
- 마일스톤: M<n>
- 위험도: 낮음 / 보통 / 높음 (근거 한 줄)

## 완료 기준
(이슈 본문에서 옮긴다)

## 참고 문서
(파일과 부분. 예: `features/03-참여하기.md`, `generated/API-목록.md`)

## 호출 계획
(위험도에 따라 호출할 에이전트와 이유)

## 진행 기록
(Phase별 한 줄, 시각)
```

## Agent 호출 프롬프트 틀

내용을 복붙하지 않고 **경로만** 준다.

```
<run-id>의 <요청 q<k> 또는 리뷰 r<k>>를 수행하라.
입력: _workspace/<run-id>/00_brief.md [, 그 밖의 입력 파일]
출력: _workspace/<run-id>/<파일명> 하나만 쓴다.
읽을 docs: <절 목록>
```
