import { CatalogPage } from "./CatalogPage";
import { menuFields } from "./catalog";

export function MenuInformationPage() {
  return (
    <CatalogPage
      endpoint="menu-information"
      title="메뉴 정보 관리"
      fields={menuFields}
      idKey="menu_id"
    />
  );
}
