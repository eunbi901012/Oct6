export type RoleCode =
  | "R01"
  | "R02"
  | "R03"
  | "R04"
  | "R05"
  | "R06"
  | "R07"
  | "R08"
  | "R09";
export type Row = Record<string, unknown>;
export interface Meta {
  page?: number;
  size?: number;
  total?: number;
}
export interface Envelope<T> {
  success: boolean;
  meta: Meta;
  data: T;
}
export interface FieldError {
  field: string;
  message: string;
}
export interface Menu extends Row {
  menu_id: number;
  parent_menu_id: number | null;
  display_order: number | null;
  menu_name: string | null;
  screen_id: string;
  url: string | null;
  icon: string | null;
  business_category: string | null;
  description: string | null;
  use_status: string | null;
  created_at: string;
  updated_at: string;
}
export interface CurrentUser {
  accountId: number;
  loginId: string;
  roles: RoleCode[];
  allowedMenus: Menu[];
}
export interface Account extends Row {
  account_id: number;
  login_id: string;
  use_status: string | null;
}
export interface Personnel extends Row {
  employee_number: string | null;
  person_name: string | null;
  organization_id: number;
  job_grade: string | null;
  employment_status: string | null;
  retirement_date: string | null;
  last_synced_at: string | null;
}
export interface UserRole extends Row {
  assignment_id: number;
  account_id: number;
  role_code: RoleCode;
  approver_id: number;
  valid_start: string | null;
  valid_end: string | null;
  assignment_source: string | null;
  assignment_status: string | null;
}
export interface UserItem extends Row {
  account: Account | null;
  personnel: Personnel | null;
  positions: Array<Row & { position_name: string | null }>;
  roles: UserRole[];
}
export interface Role extends Row {
  role_code: RoleCode;
  role_name: string | null;
}
export interface Organization extends Row {
  organization_id: number;
  parent_organization_id: number | null;
  organization_name: string | null;
  organization_code: string | null;
}
export interface CodeGroup extends Row {
  code_group_id: number;
  group_id: string;
  group_name: string | null;
}
export interface DetailCode extends Row {
  detail_code_id: number;
  code_group_id: number;
  parent_detail_code_id: number | null;
  code_value: string | null;
  code_name: string | null;
}
export interface Permission extends Row {
  menu_id: number;
  access_allowed: boolean | null;
}
export type Query = Record<string, string | number | undefined>;
