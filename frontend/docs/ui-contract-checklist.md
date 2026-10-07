# UI 개선 계약 체크리스트

## 구현 전 추출

기준: `.aiops-spec/ui-design.md`, `default-ui-style.md`, 실제 frontend API client와
backend controller/service/Responses/ResponseProjection. wireframe 배치는 복제하지 않는다.

공통: API 목록 → 저장된 행 선택 → 같은 route의 등록/수정 → 서버 검증 → 재조회.
등록·검색·선택은 저장 중 차단하고 실패 초안을 유지한다. 원천/불변 필드는 payload에서 제외한다.
loading/empty/error/permission/success를 구별하며 401/403은 보호 데이터를 숨긴다.
서버 allowedMenus의 URL과 screenId를 함께 판정하고 역할코드 우회를 두지 않는다.
미승인 검색·페이지 UI·bulk/export/dashboard/API-only 계정 및 로그아웃 기능은 추가하지 않는다.

| route / archetype | 목적·CTA | 필드·payload·제약 | 성공 동작 |
|---|---|---|---|
| /login · 인증 | login_id/password 제출 | password 마스킹, POST /api/auth/login; /api/auth/me 확인 | 허용 메뉴 직접 선택, 자동 목적지 없음 |
| /admin/users · 검색/목록/상세 | 7조건 검색, 사용여부/역할 개별 저장·취소 | employeeNumber/personName/organizationId/jobGrade/employmentStatus/roleCode/useStatus; KORUS readonly, account_id로 PATCH use-status/PUT roles; 승인자 자동 대입 금지 | 같은 사용자 재조회, 부분 성공과 각 오류 분리 |
| /admin/organizations · 계층 | organizationCode 조회, 관계·기간 확인 | parent_organization_id/effective_start/effective_end; OQ-001 전 쓰기 차단 | 조회 계층 유지, 이력 운영 화면 생성 안 함 |
| /admin/roles · 목록/상세 | 조회·등록·수정·취소 | role_code 신규 계약 enum, 수정 불변; role_name/purpose/assignment_criteria/default_data_scope | 같은 roleCode 재조회 |
| /admin/user-roles · 기간 폼 | 사용자 선택, 부여·변경·회수·취소 | account_id/assignments, assignment_id path; approver_id/valid_start/valid_end/assignment_source; DELETE body 기록 | 같은 사용자 현재 역할 재조회 |
| /admin/menu-permissions · 권한 표 | 대상 선택·조회·접근 설정 저장 | roleCode/organizationId/userId query; permissions 1행 배열; 계층 보조 GET 오류 별도 표시 | 같은 대상 재조회, /me 권한 갱신 |
| /admin/menu-structure · 계층 | 조회·부모 저장·형제 순서 저장·취소 | menuId path, parent_menu_id; 동일 부모 items[].menu_id/display_order | 같은 선택 메뉴 재조회 |
| /admin/menu-information · 실행정보 | 조회·등록·수정·저장 링크 | menu_name/screen_id/url/icon/business_category/description; 저장된 menu_id 사용 | 저장은 현재 route, 저장 URL+screenId 검증 후 링크 이동 |
| /admin/code-groups · 목록/상세 | 조회·등록·수정·상세코드 링크 | group_id/group_name/description/managing_organization_id; 수정 path 기존 groupId | 같은 그룹 재조회, 저장 groupId를 detail-codes query로 전달 |
| /admin/detail-codes · 계층/속성 | 그룹 조회·등록·수정·취소 | query groupId와 내부 code_group_id 구분; code_value/code_name/parent_detail_code_id/display_order/additional_attributes/use_status/valid_start/valid_end | 같은 그룹·코드 재조회, URL 문맥 변경 시 초안 초기화 |

## 시각적 적용

새 UI 라이브러리 없이 기존 React/CSS 사용. Civic Blue OKLCH 토큰, 아이콘 SVG,
계층형 사이드바, sticky header, 모바일 메뉴 접기, 페이지 설명, 카드형 검색/편집 영역,
수평 스크롤 표, 의미 있는 empty, skeleton, 상태 문구·색상, focus/transition 적용.
숫자 KPI/더미 행/새 필터/독립 dashboard는 생성하지 않는다.

## 미승인 정책 경계

조직 쓰기 OQ-001, 역할 회수 상태 OQ-DATA-004, 기간·접근 효과 OQ-005,
계정 사용여부/세션 차단 OQ-006, 권한 합성 OQ-003 및 선택기 보조권한 OQ-UI-012는
backend가 실제로 허용하는 범위만 소비한다. frontend에서 새 정책을 확정하지 않는다.
날짜·참조 null 해제 의미와 메뉴 구조 screen_id nullable/OpenAPI 차이는 후속 계약 승인 대상이다.
