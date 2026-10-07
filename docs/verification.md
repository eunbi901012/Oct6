# Runner 검증 절차

이 문서는 실행 결과가 아니라 생성된 앱의 후속 검증 절차다.
코드 생성 세션은 빌드·테스트·패키지 설치·컨테이너·네트워크 실행을 하지 않는다.
실행 증거가 없는 항목은 모두 awaiting runner verification이다.

## 준비 및 npm lockfile

- 저장소 루트에서 실행한다. Node 20.x, npm 10.x, Java 17, Maven, Docker Compose v2가 필요하다.
- 코드 생성 중에는 npm을 실행하지 않았으므로 `frontend/package-lock.json`은 아직 없다.
- runner의 검증/수리 단계에서만 다음을 실행해 실제 npm lockfile을 materialize한다.
- lockfile 없이 `npm ci` 또는 frontend Docker 빌드를 실행하면 실패한다. 임의 lockfile로 우회하지 않는다.

```sh
cd frontend
npm install --package-lock-only
npm ci
npm run typecheck
npm run test -- --run
npm run build
```

명령의 성공을 관측한 뒤에만 해당 항목을 통과로 기록한다.
`frontend/package-lock.json`만 보존하고 node_modules 및 dist를 커밋하지 않는다.

## Compose

DB 암호는 환경변수 `POSTGRES_PASSWORD`로 공급한다. 암호를 문서·로그에 복사하지 않는다.
해당 환경변수가 없으면 Compose는 fail-fast한다. 앱 기본 진입점은 frontend 8080,
backend 8081이며 `FRONTEND_PORT`/`BACKEND_PORT`로 변경할 수 있다.
DB는 host port를 게시하지 않는다. 앱 두 서비스의 published port는 저장소 규칙을 따른다.

```sh
docker compose -f infra/docker-compose.yml config --quiet
docker compose -f infra/docker-compose.yml build
docker compose -f infra/docker-compose.yml up -d --wait
curl --fail --silent --show-error http://localhost:8080/api/health
```

`config`의 전체 environment 출력을 기록하지 않는다. `--quiet`로 유효성만 검사한다.
local 프로필은 합성 DB 시드와 로컬 관리자 계정을 포함하므로 운영에 그대로 재사용하지 않는다.
운영 프로필, Secure 쿠키, HTTPS ingress, 인증 및 계정 정책은 배포 전에 확정한다.

## 백엔드 집중 실행

전체 Maven suite 대신 실제 생성된 클래스별로 실행하고 각각 receipt를 보관한다.

```sh
cd backend
mvn -Dtest=LoginContractTest test
mvn -Dtest=UsersContractTest test
mvn -Dtest=OrganizationsContractTest test
mvn -Dtest=RolesContractTest test
mvn -Dtest=UserRolesContractTest test
mvn -Dtest=MenuPermissionsContractTest test
mvn -Dtest=MenuStructureContractTest test
mvn -Dtest=MenuInformationContractTest test
mvn -Dtest=CodeGroupsContractTest test
mvn -Dtest=DetailCodesContractTest test
mvn -Dtest=AccountsContractTest test
mvn -Dtest=SecurityContractTest test
mvn -Dtest=HealthContractTest test
mvn -Dtest=ResponseBoundaryContractTest test
mvn -Dtest=RequiredHttpCasesContractTest test
```

실제 PostgreSQL Testcontainers IT는 Docker 사용 가능 여부를 먼저 확인하고 선택된 클래스만
Failsafe로 실행한다. skip/disabled/미발견/인프라 실패는 PASS가 아니다.
MockMvc slice의 성공은 실제 SQL, Flyway, BCrypt 로그인 및 mapper materialization 증거가 아니다.

## HTTP preview smoke

`tests/preview_smoke.py`는 표준 라이브러리로 실제 HTTP 요청을 보내는 보조 점검이다.
실제 로컬 계정의 SMOKE_LOGIN_ID/SMOKE_PASSWORD와 BASE_URL을 환경변수로 제공한 뒤
`python tests/preview_smoke.py`로 실행한다. credentials와 cookie 값은 출력하지 않는다.
이 점검은 health, 로그인/현재 사용자, 9개 관리 읽기 API, 목록 envelope, 메뉴 seed 및
로그아웃 후 401을 확인한다. HTTP 읽기 성공만으로 쓰기·SQL·UI 인수를 완료 처리하지 않는다.

## 브라우저 인수

Playwright는 실행 중인 Compose 앱을 대상으로 한다. BASE_URL 설정 이름은
`frontend/playwright.config.ts`의 실제 선언을 확인한다. 테스트를 위해 dev server를 따로 띄우지 않는다.

```sh
cd frontend
npm run test:e2e
```

로그인, 허용 메뉴 탐색, 9개 leaf 접근, 원천 읽기 전용, 저장 후 재조회,
권한 없는 메뉴 숨김과 직접 요청 거부, empty/error/permission 상태를 확인한다.
미승인 조직 쓰기와 기타 정책 게이트는 성공 저장이 아니라 차단 및 데이터 불변을 관측한다.

## 영속성 인수

- 실제 HTTP로 로컬 역할/메뉴/코드를 저장한 뒤 GET/DB readback으로 확인한다.
- `docker compose -f infra/docker-compose.yml restart backend database` 후 healthy를 기다린다.
- 새 로그인으로 같은 데이터를 다시 조회해 저장 유지 여부를 확인한다.
- 테스트 목적 외 volume 제거를 하지 않는다. `down -v`는 저장 데이터를 삭제한다.
- migration 실행, SQL materialization, 인증·권한, 저장 side effect, 재시작 유지 결과를 각각 분리한다.

## 승인 대기

조직 관계/이력(OQ-001), 변경 추적 영속화/보존(OQ-002), 권한 충돌/기능·범위(OQ-003/004),
역할 기간·승인·보직(OQ-005), 계정·사용중지·세션 정책(OQ-006/DATA-004),
최후 관리자 보호(OQ-007), UI 페이지/route/레퍼런스 정책은 승인된 것으로 간주하지 않는다.
업무 정책 미확정과 테스트 미실행을 전체 구현 완료/인수 합격으로 표현하지 않는다.

## 정적 검토에서 발견한 계약 정렬 문제

- OpenAPI의 `Menu.screen_id`, `UserRole.approver_id`, `CodeGroup.managing_organization_id`와
  `KorusPersonnelSnapshot.organization_id`는 required/nonnullable인데 데이터 모델의 필수·NULL 정책은
  승인 대기다. 그룹 메뉴와 미연결 행에서 NULL이 존재할 수 있어 응답 schema 적합성을 주장하지 않는다.
  임의 화면 ID·승인자·부서 ID를 채우거나 입력 계약을 수정하여 이 문제를 은폐하지 않았다.
- `ResponseProjection`은 optional/nonnullable 참조의 NULL만 생략한다. required 필드 또는 nullable 필드의
  삭제, 임의 JSON 속성 삭제, mapper 내부 map 변경으로 위 문제를 우회하지 않는다.
- `allowedMenus`는 서버·클라이언트가 사용하는 기술 확장이나 staged `CurrentUser`에는 없다.
  OQ-UI-012 및 응답 계약 정렬이 필요하다. 확장이 없을 때 클라이언트는 실패 폐쇄한다.
- MockMvc 공통 계약 fixture는 argument discovery 전에 `@BeforeAll`에서 로드한다.
  이것은 정적 생명주기 결함 수정이며 JUnit 실행 성공 증거가 아니다.
- 테스트 소스의 긴 행과 미결 데이터 정책을 포함한 최종 품질 검토는 runner 인수 항목이다.
