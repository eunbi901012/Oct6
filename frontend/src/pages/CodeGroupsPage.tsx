import { CatalogPage } from "./CatalogPage";
import { groupFields } from "./catalog";

export function CodeGroupsPage() {
  return (
    <CatalogPage
      endpoint="code-groups"
      title="코드그룹 관리"
      fields={groupFields}
      idKey="group_id"
    />
  );
}
