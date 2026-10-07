import { CatalogPage } from "./CatalogPage";
import { roleFields } from "./catalog";

export function RolesPage() {
  return (
    <CatalogPage
      endpoint="roles"
      title="역할 관리"
      fields={roleFields}
      idKey="role_code"
    />
  );
}
