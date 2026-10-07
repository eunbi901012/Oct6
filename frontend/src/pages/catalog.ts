import type { Field, Reference } from "../components/fields";

export const roleChoices = [
  "R01",
  "R02",
  "R03",
  "R04",
  "R05",
  "R06",
  "R07",
  "R08",
  "R09",
].map((value) => ({ value, label: value }));
export const usersReference: Reference = {
  endpoint: "users",
  id: "account_id",
  label: "login_id",
};
export const rolesReference: Reference = {
  endpoint: "roles",
  id: "role_code",
  label: "role_name",
};
export const organizationsReference: Reference = {
  endpoint: "organizations",
  id: "organization_id",
  label: "organization_name",
};
export const groupsReference: Reference = {
  endpoint: "code-groups",
  id: "group_id",
  label: "group_name",
};
export const menusReference: Reference = {
  endpoint: "menu-structure",
  id: "menu_id",
  label: "menu_name",
  paginated: false,
};
export const roleFields: Field[] = [
  {
    key: "role_code",
    label: "역할코드",
    immutable: true,
    choices: roleChoices,
  },
  { key: "role_name", label: "역할명" },
  { key: "purpose", label: "목적", type: "textarea" },
  { key: "assignment_criteria", label: "부여 기준", type: "textarea" },
  { key: "default_data_scope", label: "데이터 범위 기본값" },
];
export const menuFields: Field[] = [
  { key: "menu_name", label: "메뉴명" },
  { key: "screen_id", label: "화면ID" },
  { key: "url", label: "URL" },
  { key: "icon", label: "아이콘" },
  { key: "business_category", label: "업무구분" },
  { key: "description", label: "설명", type: "textarea" },
];
export const groupFields: Field[] = [
  { key: "group_id", label: "그룹ID" },
  { key: "group_name", label: "명칭" },
  { key: "description", label: "설명", type: "textarea" },
  {
    key: "managing_organization_id",
    label: "관리부서",
    reference: organizationsReference,
  },
];
export const assignmentFields: Field[] = [
  { key: "role_code", label: "역할", reference: rolesReference },
  { key: "approver_id", label: "승인자", reference: usersReference },
  { key: "valid_start", label: "유효 시작일", type: "date" },
  { key: "valid_end", label: "유효 종료일", type: "date" },
  { key: "assignment_source", label: "보직/수동 구분" },
];
