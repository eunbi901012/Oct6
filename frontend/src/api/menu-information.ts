import { api } from "./client";
import type { Menu, Row } from "./types";

export const menuInformationApi = {
  list: () => api.list<Menu>("/api/menu-information"),
  create: (body: Row) => api.mutate("POST", "/api/menu-information", body),
  update: (menuId: number, body: Row) =>
    api.mutate("PATCH", `/api/menu-information/${menuId}`, body),
};
