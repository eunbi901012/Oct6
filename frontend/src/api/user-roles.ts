import { api } from "./client";
import type { Row, UserRole } from "./types";

export const userRolesApi = {
  list: (userId: string) => api.list<UserRole>("/api/user-roles", { userId }),
  grant: (account_id: number, assignments: Row[]) =>
    api.mutate("POST", "/api/user-roles", { account_id, assignments }),
  change: (assignmentId: number, assignment: Row) =>
    api.mutate("PATCH", `/api/user-roles/${assignmentId}`, assignment),
  revoke: (assignmentId: number, record: Row) =>
    api.mutate("DELETE", `/api/user-roles/${assignmentId}`, record),
};
