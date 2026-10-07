import { api } from "./client";
import type { Query, Row, UserItem } from "./types";

export const usersApi = {
  search: (query: Query = {}) => api.list<UserItem>("/api/users", query),
  useStatus: (accountId: number, use_status: string) =>
    api.mutate("PATCH", `/api/users/${accountId}/use-status`, { use_status }),
  roles: (accountId: number, assignments: Row[]) =>
    api.mutate("PUT", `/api/users/${accountId}/roles`, { assignments }),
};
