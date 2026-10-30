# 생성 문서

사람이 고치지 않는다. 코드와 마이그레이션에서 만들고, 코드와 어긋나면 빌드(CI)가 실패한다. 고칠 때는 문서가 아니라 원본(코드)을 고친 뒤 다시 만든다.

| 문서 | 원본 | 다시 만들기 |
| --- | --- | --- |
| [오류 코드](오류-코드.md) | `ErrorCode`를 구현한 enum | `DOCS_UPDATE=1 ./gradlew test --tests '*ErrorCodeDocTest'` |
| [openapi.json](openapi.json) | 컨트롤러와 스웨거 어노테이션(`/v3/api-docs`) | `DOCS_UPDATE=1 ./gradlew test --tests '*OpenApiDocTest'` |

`openapi.json`은 API 문서를 만드는 도구(스웨거 뷰어, 타입 생성, 목 서버)에 그대로 넣는 파일이다. 기능 페이지의 "사용하는 API"와 `docs-check`가 서로 대조한다.
