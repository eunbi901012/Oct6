# 공통 관리 프런트엔드

React 18 / TypeScript / Vite 5 기반 한국어 관리 앱입니다. 데이터와 참조 선택지는 실제 상대경로 API로 조회합니다.
`/login`은 공개 화면이며 나머지 9개 관리 route는 서버에서 허용한 메뉴만 표시하고 진입시킵니다.
독립 dashboard, 계정 생성/비밀번호 재설정 화면, 이력 조회 API, 외부 기관 호출을 추가하지 않았습니다.

## 서버 계약

- 요청은 `/api/...`, 쿠키는 동일 origin이며 DTO는 snake_case, query/path 이름은 OpenAPI의 camelCase입니다.
- `GET /api/auth/me`의 `data`는 `accountId`, `loginId`, `roles`, `allowedMenus: Menu[]`입니다.
  `allowedMenus`는 서버가 허용한 행만 반환하는 협업 확장입니다. 누락/잘못된 배열은 실패 폐쇄합니다.
  내비게이션은 `/api/menu-structure`를 호출하지 않으며 R09 별도 우회도 없습니다.
- 보호 화면의 조회/저장 403은 데이터를 숨기고 현재 인증 메뉴를 다시 확인합니다. 401은 로그인 안내입니다.
- 참조 선택지와 권한 행의 계층 보조 요청이 거부되면 해당 영역에 오류를 표시합니다.
  이 보조 요청은 현재 화면 자체의 접근 거부로 가장하지 않습니다.
- 사용자 사용여부 PATCH와 역할 PUT은 별도 transaction, 초안, 저장, 취소, 메시지로 분리했습니다.
- 역할 묶음의 승인자는 각 실제 사용자 선택으로 명시하며 로그인 처리자를 자동으로 넣지 않습니다.
- 선택 상세는 목록 응답입니다. 없는 상세 GET이나 selector 전용 endpoint를 만들지 않았습니다.
- 코드그룹 `group_id`는 조회/route 문맥이고 `code_group_id`는 상세코드 입력의 내부 FK입니다.
  그룹 키 수정은 기존 키를 path에, 변경 키를 body에 사용합니다.
- 메뉴 parent/order와 실행정보는 별도 operation입니다. 저장 링크는 저장된 URL/화면ID와 허용 메뉴를
  대조한 후 명시적으로 클릭할 때만 열립니다. 범위 밖 URL은 실행하지 않습니다.
- 실제 backend가 전체 목록을 반환하므로 기본 목록·참조 조회는 page/size 없이 한 요청을 사용합니다.
  API client의 명시적인 paginated 옵션은 유지하며 UI 페이지 정책/기본 정렬은 발명하지 않습니다.

## 실행과 검증

UI 개선 작업에서는 기존 dependency를 설치하고 `package-lock.json`을 생성했습니다.
새 UI 라이브러리는 추가하지 않았습니다. React/CSS로 Civic Blue 토큰, 관리 shell, split 로그인,
표·폼·배지, skeleton/empty/error/success 상태와 반응형 내비게이션을 구현했습니다.
실제 TypeScript compile·Vitest·Vite production build·비인증 Playwright 검증 결과는
`docs/ui-contract-checklist.md`의 검증 기록을 확인하세요. 편집 도구의 skipped 부가 검사는 성공 근거가 아닙니다.

개발 서버는 `/api`를 실제 backend로 프록시합니다. 기본 대상은 `http://localhost:8080`이며
다른 환경에서는 `API_PROXY_TARGET` 환경변수로 변경할 수 있습니다. 운영 nginx 계약은 유지합니다.
Dockerfile의 `npm ci`를 위한 실제 lockfile을 포함하며 install fallback은 사용하지 않습니다.

Runner 검증 명령:

```sh
npm ci
npm run typecheck
npm test
npm run build
npm run test:e2e
```

Playwright는 이미 실행 중인 실제 프런트/백엔드/DB를 사용하며 webServer를 자동 실행하지 않습니다.
`E2E_BASE_URL` 기본은 `http://localhost:8080`이며 compose published frontend URL로 맞춰야 합니다.
`E2E_LOGIN_ID`, `E2E_PASSWORD`는 실제 허용 관리자 계정으로 runner 환경에 제공해야 합니다.
`E2E_DENIED_LOGIN_ID`, `E2E_DENIED_PASSWORD`가 없으면 실제 미허용 계정 시나리오만 명시적으로 skip합니다.
자격증명 값은 파일에 포함하지 않습니다.

E2E는 request interception/mocking 없이 실제 조회·저장·재조회·회수를 수행합니다.
역할/실행정보/코드그룹 변경값은 finally에서 복원하고 역할 부여는 회수합니다.
별도의 disposable 테스트 DB와 승인된 FIX-PERSONNEL / FIX-ASSIGNMENTS / FIX-PERMISSIONS /
FIX-CODES fixture가 필요합니다. fixture 부족은 성공으로 대체하지 않고 실패 사유로 표시합니다.
사용여부·메뉴 권한·순서의 idempotent 저장은 미확정 상태 enum/합성 정책을 발명하지 않기 위함입니다.
Vitest component/client 테스트만 독립적인 시험용 응답을 사용하며 앱 실행 데이터로 포함하지 않습니다.

## 남은 승인 사항

- OQ-001: 조직 원천/로컬 쓰기 경계. 관계·기간 폼은 읽기 전용이고 저장을 전송하지 않습니다.
- OQ-UI-002: 조직 관계 이력의 표시 위치/조회 방식. 승인되지 않은 이력 화면/API는 없습니다.
- OQ-UI-012: allowedMenus 협업 확장, 사용자/역할/조직/그룹 참조 조회의 보조 권한,
  조직 조건조회 결과의 상하위 완전성. 의존 API 403은 실제 오류로 노출됩니다.
- OQ-003/004/005/006/007 및 OQ-DATA-004: 충돌, 기능 범위, 승인/기간/부여 구분,
  계정/세션 차단, 마지막 관리자 정책은 서버에서 승인된 규칙만 적용합니다.
- OQ-DATA-001/003: 필드 검증, 그룹/코드값 변경 참조 정책, nullable 입력/삭제 의미.
  부모 제거는 non-nullable 요청 계약 때문에 차단합니다. 빈 optional date/FK/JSON 값은 보내지 않습니다.
  수정 폼은 변경된 편집 가능 필드만 전송하며 일반 문자열을 지우면 빈 문자열을 전송합니다.
  문자열에 null을 보내거나 미변경·불변 필드를 변경하지 않습니다. 서버의 세부 검증은 유지합니다.
- OQ-UI-001/009/011/013: 미정 pagination 정책·로그인 자동 초기 진입·저장 링크의 범위 밖 정책은 유지합니다.
  시각 스타일은 `default-ui-style.md`의 Civic Blue를 적용하며 route와 screenId가 모두 일치할 때만
  허용 링크·직접 진입을 활성화합니다. 역할코드 기반 우회나 임의 화면은 없습니다.

## 배포

Docker의 `build` stage는 node20에서 npm ci/build를 수행합니다. 테스트 소스는 build stage에만 복사합니다.
최종 nginx 이미지는 dist와 nginx 설정만 포함합니다. `/api/`는 backend:8080으로 프록시하며 SPA fallback과
`/healthz` healthcheck를 포함합니다. upstream service 이름/포트는 runner의 compose와 일치해야 합니다.

## 정적 검토와 회귀 테스트 인계

입력 문서의 Screen Inventory/Action Mapping 및 화면별 binding과 OpenAPI Input schema를 대조했습니다.
공개 로그인과 9개 관리 route가 연결되어 있으며 조직 쓰기는 OQ-001 때문에 차단 상태를 유지합니다.
로그아웃 CTA, API-only 계정 화면, 운영용 더미 목록은 없습니다. 문자열 초기화는 계약의 string 값으로
전송하며 날짜·숫자·참조·JSON에 null 또는 미승인 삭제 의미를 추가하지 않습니다.

`tests/unit/editor-regressions.test.tsx`에 다음 회귀 시나리오를 작성했으며 UI 개선에서 실제 실행했습니다.

- 선택 역할의 일반 문자열 지우기와 미변경·불변 필드 제외.
- 취소 후 조회값 및 제출 body 복원.
- 역할 변환 오류 시 기존 유효 payload 폐기, 오류 수정 후 저장 재개.
- 상세코드 query 이동/뒤로가기의 선택·초안 초기화와 새 그룹 코드 식별자 PATCH.
- 빈 숫자·날짜·JSON 및 미변경 null 문자열에 대한 임의 null 전송 방지.

상세코드 페이지는 groupId별 편집 문맥을 분리하며 그룹 변경 시 조회 선택지·선택 코드·편집·메시지를
초기화합니다. 수정 요청은 기존 그룹 FK를 재전송하지 않고 신규 등록에만 선택 그룹 내부 키를 사용합니다.
