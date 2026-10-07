import { api } from "./client";
import type { CodeGroup, Row } from "./types";

export const codeGroupsApi = {
  list: () => api.list<CodeGroup>("/api/code-groups"),
  create: (body: Row) => api.mutate("POST", "/api/code-groups", body),
  update: (oldGroupId: string, body: Row) =>
    api.mutate(
      "PATCH",
      `/api/code-groups/${encodeURIComponent(oldGroupId)}`,
      body,
    ),
};
