import { api } from "./client";
import type { Organization, Query } from "./types";

export const organizationsApi = {
  search: (query: Query = {}) =>
    api.list<Organization>("/api/organizations", query),
  // Organization write operations intentionally have no callable client before OQ-001 approval.
};
