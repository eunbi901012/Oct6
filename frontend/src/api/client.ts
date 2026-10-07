import type { Envelope, FieldError, Query } from "./types";

export class ApiFailure extends Error {
  constructor(
    public status: number,
    message: string,
    public fieldErrors: FieldError[] = [],
  ) {
    super(message);
    this.name = "ApiFailure";
  }
}

export async function request<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<Envelope<T>> {
  if (!path.startsWith("/api/"))
    throw new Error("API는 동일 origin 상대경로만 허용합니다.");
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  } catch {
    throw new ApiFailure(0, "서버에 연결할 수 없습니다. 다시 시도하세요.");
  }
  if (response.status === 401)
    window.dispatchEvent(new Event("session-expired"));
  let result: Envelope<T> & { message?: string; fieldErrors?: FieldError[] };
  try {
    result = (await response.json()) as typeof result;
  } catch {
    throw new ApiFailure(
      response.status,
      "서버 응답 형식을 확인할 수 없습니다.",
    );
  }
  if (!response.ok || !result.success) {
    throw new ApiFailure(
      response.status,
      result.message ?? "요청을 처리하지 못했습니다.",
      result.fieldErrors,
    );
  }
  return result;
}

export const api = {
  async list<T>(
    path: string,
    query: Query = {},
    paginated = false,
  ): Promise<T[]> {
    const items: T[] = [];
    let page = 0;
    do {
      const params = new URLSearchParams();
      Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined && value !== "") params.set(key, String(value));
      });
      // One-row traversal is the contract's example, not a UI default page-size policy.
      if (paginated) {
        params.set("page", String(page));
        params.set("size", "1");
      }
      const suffix = params.size ? `?${params.toString()}` : "";
      const response = await request<T[]>(`${path}${suffix}`);
      if (!Array.isArray(response.data))
        throw new ApiFailure(502, "목록 응답이 올바르지 않습니다.");
      items.push(...response.data);
      if (!paginated) break;
      const { total, size } = response.meta;
      if (
        !Number.isSafeInteger(total) ||
        !Number.isSafeInteger(size) ||
        total === undefined ||
        total < 0 ||
        size === undefined ||
        size < 1
      ) {
        throw new ApiFailure(502, "페이지 응답 정보가 올바르지 않습니다.");
      }
      if ((page + 1) * size >= total) break;
      if (response.data.length === 0)
        throw new ApiFailure(409, "목록이 변경되었습니다. 다시 조회하세요.");
      page += 1;
    } while (true);
    return items;
  },
  async mutate(method: string, path: string, body?: unknown): Promise<void> {
    await request<unknown>(path, method, body);
  },
};

export function failure(error: unknown): ApiFailure {
  return error instanceof ApiFailure
    ? error
    : new ApiFailure(0, error instanceof Error ? error.message : "처리 실패");
}
