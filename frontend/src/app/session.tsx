import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import type { ReactNode } from "react";
import { failure } from "../api/client";
import { loginApi } from "../api/login";
import type { CurrentUser } from "../api/types";

interface SessionValue {
  user: CurrentUser | null;
  loading: boolean;
  error: string;
  refresh: () => Promise<void>;
}
const SessionContext = createContext<SessionValue | null>(null);

function validDecision(value: unknown): value is CurrentUser {
  if (!value || typeof value !== "object") return false;
  const current = value as Record<string, unknown>;
  return (
    Number.isSafeInteger(current.accountId) &&
    typeof current.loginId === "string" &&
    Array.isArray(current.roles) &&
    current.roles.every((role) => typeof role === "string") &&
    Array.isArray(current.allowedMenus) &&
    current.allowedMenus.every((entry: unknown) => {
      if (!entry || typeof entry !== "object" || Array.isArray(entry))
        return false;
      const menu = entry as Record<string, unknown>;
      return (
        Number.isSafeInteger(menu.menu_id) &&
        typeof menu.screen_id === "string" &&
        (menu.url === null || typeof menu.url === "string") &&
        (menu.parent_menu_id === null ||
          Number.isSafeInteger(menu.parent_menu_id)) &&
        (menu.display_order === null ||
          Number.isSafeInteger(menu.display_order))
      );
    })
  );
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const generation = useRef(0);
  const refresh = useCallback(async () => {
    const request = ++generation.current;
    setError("");
    try {
      const result = await loginApi.me();
      if (request !== generation.current) return;
      if (!validDecision(result.data)) {
        throw new Error(
          "서버 허용 메뉴 계약을 확인할 수 없습니다. OQ-UI-012 확인 필요.",
        );
      }
      setUser(result.data);
    } catch (cause) {
      if (request !== generation.current) return;
      setUser(null);
      const problem = failure(cause);
      if (problem.status !== 401) setError(problem.message);
    } finally {
      if (request === generation.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    void refresh();
    const expire = () => {
      generation.current += 1;
      setUser(null);
      setLoading(false);
    };
    window.addEventListener("session-expired", expire);
    return () => {
      generation.current += 1;
      window.removeEventListener("session-expired", expire);
    };
  }, [refresh]);
  return (
    <SessionContext.Provider value={{ user, loading, error, refresh }}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error("세션 공급자가 필요합니다.");
  return value;
}
