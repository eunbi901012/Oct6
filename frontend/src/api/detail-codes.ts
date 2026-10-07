import { api } from "./client";
import type { DetailCode, Row } from "./types";

export const detailCodesApi = {
  list: (groupId: string) =>
    api.list<DetailCode>("/api/detail-codes", { groupId }),
  create: (body: Row) => api.mutate("POST", "/api/detail-codes", body),
  update: (detailCodeId: number, body: Row) =>
    api.mutate("PATCH", `/api/detail-codes/${detailCodeId}`, body),
};
