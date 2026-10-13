# git 워크플로

브랜치·커밋·PR 규칙의 기준은 `docs/개발-가이드.md` §1~§2다. 여기에는 하네스가 지킬 동작만 적는다.

## 하네스의 동작

- 기능 브랜치는 `develop`에서 분기한다: `git switch develop && git pull && git switch -c feature/<이슈번호>-<영문-요약>`. 모드 A에서 사용자 확인 후 만든다.
- `main`·`develop`에서는 쓰기 모드를 진행하지 않는다.
- **커밋·push·PR은 사용자 확인을 받은 뒤에만** 한다. 확인 없이 하지 않는다.
- 경로를 명시해 스테이징한다. `git add -A`·`git add .`는 쓰지 않는다. `.env`와 `.env.*`(`.env.example`은 샘플이라 예외)는 절대 스테이징하지 않는다. `application-local.yaml`은 비밀 값 없이 환경 변수 참조만 두므로 커밋 대상이지만, 스테이징 전에 실제 값이 들어갔는지 확인한다.
- 커밋 메시지는 `docs/개발-가이드.md` §2 형식이다. 제안은 하네스가 하고 최종 문구는 사용자가 정한다.
- push: `git push -u origin <브랜치>`. force push 금지. 거부되면 우회하지 않고 보고한다.
- PR: `gh pr create --base develop --head <브랜치> --title "<제목>" --body-file <본문 파일>`. 제목은 커밋 형식이다. 본문에 `Closes #<n>`(이슈가 있을 때만)을 넣는다.
- 전제: 최신 리뷰 판정이 FAIL이 아니고, 리뷰 시점 HEAD와 현재 HEAD가 같다. 어긋나면 재리뷰를 먼저 안내한다.
- 머지(`feature → develop` squash, `develop → main` merge commit)와 배포는 사람이 한다. 하네스는 `gh pr merge`, main·develop push, 배포를 하지 않는다.

## settings.json 권한 설계 이유

- `allow`에는 읽기·조회성 명령과 컴파일·테스트 명령만 둔다. `git add`·`git commit`·`git switch`·`git push`·`gh pr create`는 **허용 목록에 넣지 않는다** — 실행마다 권한 확인이 떠서, 고정 지점의 사용자 확인 뒤에도 한 번 더 거른다(Bash가 있는 test-writer에 대한 2차 방어).
- `deny`에는 비밀값 읽기, force push, main·develop push, `gh pr merge`, `rm -rf`를 둔다. GitHub 브랜치 보호가 같은 것을 서버에서 한 번 더 막는다.
