import { api } from "./client";
import type { Role, Row } from "./types";

export const rolesApi = {
  list: () => api.list<Role>("/api/roles"),
  create: (body: Row) => api.mutate("POST", "/api/roles", body),
  update: (roleCode: string, body: Row) => {
    const { role_code: immutableCode, ...mutable } = body;
    void immutableCode;
    return api.mutate(
      "PATCH",
      `/api/roles/${encodeURIComponent(roleCode)}`,
      mutable,
    );
  },
};
