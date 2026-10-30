<!-- 자동 생성 문서다. 고치지 말고 코드(컨트롤러와 스웨거 어노테이션)를 고친다. -->
<!-- 다시 만들기: DOCS_UPDATE=1 ./gradlew test --tests '*OpenApiDocTest' -->

# API 목록

서버가 제공하는 API 14개를 영역별로 모았다. 경로는 모두 `/api/v1`로 시작한다(표에서는 뺐다). 오류의 뜻과 상태는 [오류 코드](오류-코드.md)에 있고, 요청·응답 필드는 [openapi.json](openapi.json)을 스웨거 뷰어나 타입 생성 도구에 넣어 본다. 기능 칸은 그 API를 쓰는 기능 페이지다.

카카오 로그인 시작과 콜백은 스프링 시큐리티가 처리하는 경로라 이 목록에 없다.

## 투표

| API | 하는 일 | 성공 | 오류 | 기능 |
| --- | --- | --- | --- | --- |
| `GET /polls` | 투표 목록 | 200 | `MALFORMED_REQUEST` | [01-둘러보기](../features/01-둘러보기.md) |
| `POST /polls` | 투표 생성 | 201 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `DUPLICATE_POLL`, `POLL_RATE_LIMITED` | [02-투표-만들기](../features/02-투표-만들기.md) |
| `GET /polls/{pollId}` | 투표 상세 | 200 | `MALFORMED_REQUEST`, `POLL_NOT_FOUND` | [01-둘러보기](../features/01-둘러보기.md), [03-참여하기](../features/03-참여하기.md), [04-결과-보기](../features/04-결과-보기.md) |

## 참여

| API | 하는 일 | 성공 | 오류 | 기능 |
| --- | --- | --- | --- | --- |
| `POST /polls/{pollId}/votes` | 투표 참여 | 201 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_OPTION`, `UNAUTHENTICATED`, `MEMBER_SUSPENDED`, `POLL_NOT_FOUND`, `ALREADY_VOTED`, `POLL_CLOSED` | [03-참여하기](../features/03-참여하기.md), [04-결과-보기](../features/04-결과-보기.md) |

## 신고

| API | 하는 일 | 성공 | 오류 | 기능 |
| --- | --- | --- | --- | --- |
| `POST /polls/{pollId}/reports` | 투표 신고 | 201 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `MEMBER_SUSPENDED`, `POLL_NOT_FOUND`, `REPORT_ALREADY_SUBMITTED` | [05-신고하기](../features/05-신고하기.md) |

## 회원

| API | 하는 일 | 성공 | 오류 | 기능 |
| --- | --- | --- | --- | --- |
| `GET /members/me` | 내 정보 조회 | 200 | `UNAUTHENTICATED` | [07-로그인과-회원](../features/07-로그인과-회원.md) |
| `DELETE /members/me` | 회원 탈퇴 | 204 | `UNAUTHENTICATED` | [07-로그인과-회원](../features/07-로그인과-회원.md) |

## 인증

| API | 하는 일 | 성공 | 오류 | 기능 |
| --- | --- | --- | --- | --- |
| `POST /auth/logout` | 로그아웃 | 204 | - | [07-로그인과-회원](../features/07-로그인과-회원.md) |
| `POST /auth/refresh` | 토큰 재발급 | 204 | `UNAUTHENTICATED` | [07-로그인과-회원](../features/07-로그인과-회원.md) |

## 관리자 투표

| API | 하는 일 | 성공 | 오류 | 기능 |
| --- | --- | --- | --- | --- |
| `PATCH /admin/polls/{pollId}/status` | 투표 숨김·삭제 | 204 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `POLL_NOT_FOUND`, `POLL_NOT_PUBLIC` | [06-관리자-조치](../features/06-관리자-조치.md) |

## 관리자 신고

| API | 하는 일 | 성공 | 오류 | 기능 |
| --- | --- | --- | --- | --- |
| `GET /admin/reports` | 신고 목록 | 200 | `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN` | [06-관리자-조치](../features/06-관리자-조치.md) |
| `GET /admin/reports/{reportId}` | 신고 상세 | 200 | `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `REPORT_NOT_FOUND` | [06-관리자-조치](../features/06-관리자-조치.md) |
| `POST /admin/reports/{reportId}/dismiss` | 신고 기각 | 204 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `REPORT_NOT_FOUND`, `REPORT_ALREADY_HANDLED` | [06-관리자-조치](../features/06-관리자-조치.md) |

## 관리자 회원

| API | 하는 일 | 성공 | 오류 | 기능 |
| --- | --- | --- | --- | --- |
| `POST /admin/members/{memberId}/suspend` | 회원 정지 | 204 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `MEMBER_NOT_FOUND`, `MEMBER_NOT_ACTIVE` | [06-관리자-조치](../features/06-관리자-조치.md) |
