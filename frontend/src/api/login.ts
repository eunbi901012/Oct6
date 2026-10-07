import { request } from "./client";
import type { CurrentUser } from "./types";

export const loginApi = {
  login: (login_id: string, password: string) =>
    request("/api/auth/login", "POST", { login_id, password }),
  me: () => request<CurrentUser>("/api/auth/me"),
};
