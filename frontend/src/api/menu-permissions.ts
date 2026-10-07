import { api } from "./client";
import type { Permission, Query, Row } from "./types";

export const menuPermissionsApi = {
  list: (query: Query) => api.list<Permission>("/api/menu-permissions", query),
  save: (permission: Row) =>
    api.mutate("PUT", "/api/menu-permissions", { permissions: [permission] }),
};
