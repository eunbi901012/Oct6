import { api } from "./client";
import type { Menu } from "./types";

export const menuStructureApi = {
  list: () => api.list<Menu>("/api/menu-structure", {}, false),
  parent: (menuId: number, parent_menu_id: number) =>
    api.mutate("PATCH", `/api/menu-structure/${menuId}/parent`, {
      parent_menu_id,
    }),
  order: (
    parentId: number | null,
    items: Array<{ menu_id: number; display_order: number }>,
  ) =>
    api.mutate("PUT", "/api/menu-structure/order", {
      ...(parentId === null ? {} : { parent_menu_id: parentId }),
      items,
    }),
};
