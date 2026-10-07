# 한국교원대학교 공통 관리 기반

Java 17 / Spring Boot 3.3 / Maven executable boot jar / blocking MyBatis / PostgreSQL 16과
React 18 / TypeScript / Vite 5 / npm으로 구현하는 시스템 관리 애플리케이션이다.

로그인과 사용자·조직, 역할·사용자 역할·메뉴 권한, 메뉴 구조·정보, 코드그룹·상세코드의
9개 관리 화면을 포함한다. 외부 SSO/KORUS 연결, 업적 평가 업무, 파일·Excel·감사 운영은 제외한다.
합성 원천정보는 local 프로필의 DB 스냅샷에서 읽는다. 브라우저 API는 상대경로 `/api/...`만 사용한다.

## 실행 전 확인

- 코드 생성 단계에서는 테스트·빌드·패키지 설치·컨테이너 실행을 하지 않았다.
- `frontend/package-lock.json`은 runner가 npm으로 materialize해야 한다.
  lockfile 없이 frontend Docker build의 `npm ci`는 실행되지 않는다.
- DB 암호는 `POSTGRES_PASSWORD` 환경변수로 제공한다. 파일에 저장하지 않는다.
- Docker Compose v2 설정은 `infra/docker-compose.yml`이다.
- 기본 frontend 진입점은 8080, backend published port는 8081이다.
  `FRONTEND_PORT`/`BACKEND_PORT`로 조정할 수 있다. database는 host port를 게시하지 않는다.
- local 프로필은 명세가 허용한 로컬 데모 계정을 포함한다. 실제 운영 계정·인사정보가 아니다.
  로컬 전용 로그인은 admin / admin이며 운영에 재사용하지 않는다.

상세 runner 절차: [docs/verification.md](docs/verification.md).
DB 타입·마이그레이션·승인 경계: [backend/src/main/resources/db/README.md](backend/src/main/resources/db/README.md).

## 배포 구조

frontend nginx → `/api/` proxy → backend Spring Boot → PostgreSQL.
Compose 앱 서비스는 image/build/pull_policy 및 healthcheck를 모두 가진다.
backend는 JDK 빌드와 JRE 런타임을 분리하고 비특권 계정으로 executable jar를 실행한다.
frontend의 Node build stage에는 테스트 컨텍스트가 들어가며 nginx 런타임에는 빌드된 정적 파일만 들어간다.
PostgreSQL의 named volume은 재시작 시 로컬 변경을 보존한다.

`schema.sql`은 V1 Flyway migration과 동일한 최초 스키마 정본이다.
Spring SQL init은 사용하지 않고 Flyway만 DDL을 실행한다.
local 프로필만 별도 `db/local` 시드를 실행하고 운영에는 포함하지 않는다.

## 승인 및 검증 상태

조직 관계·이력의 쓰기 경계, 변경 추적 영속화/보존, 권한 충돌·범위·기간·상태 효력,
계정/세션 정책 및 UI 레퍼런스의 미결 질문은 승인된 것으로 간주하지 않는다.
원천 조직 쓰기 등 미승인 처리는 저장 성공 대신 명시적인 승인 대기 오류로 차단한다.
현재 시드는 명시 메뉴 권한을 사용하며 R09 전역 우회 권한으로 대체하지 않는다.
`/api/auth/me`의 `allowedMenus`는 화면 탐색을 위한 기술 확장으로 서버 판정 결과를 전달한다.
변경 전후값·처리자·시점은 ChangeTrace 애플리케이션 이벤트로 전달하지만,
이벤트의 영속 저장·보존 기간·필수 사유는 OQ-002 미승인 상태다. 감사 운영을 구현한 것으로 간주하지 않는다.

단순 소스 작성/정적 정렬 확인은 컴파일·테스트·실제 SQL·브라우저 인수의 성공 증거가 아니다.
현 상태는 **awaiting runner verification**이며 전체 요구 인수 완료를 주장하지 않는다.
Swagger UI와 선택적 mapper probe는 핵심 계약을 대체하지 않는 후속 도구로 남겨 두었다.
