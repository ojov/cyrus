const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

function getToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)cyrus_token=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`${API_URL}${path}`, { ...options, headers });
  const body = await res.json();

  if (!res.ok) {
    throw new Error(body?.message ?? `Request failed: ${res.status}`);
  }
  return body;
}

export const api = {
  post: <T>(path: string, data: unknown) =>
    request<T>(path, { method: "POST", body: JSON.stringify(data) }),
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

// --- Auth ---
export interface LoginResponse {
  data: {
    token: string;
    tokenType: string;
    merchantId: string;
    businessName: string;
    businessEmail: string;
  };
}

export interface RegisterResponse {
  data: {
    merchantId: string;
    apiKey: string;
  };
}

export const authApi = {
  login: (email: string, password: string) =>
    api.post<LoginResponse>("/v1/auth/login", { email, password }),

  register: (payload: {
    businessName: string;
    businessEmail: string;
    password: string;
    nombaClientId: string;
    nombaClientSecret: string;
    nombaParentAccountId: string;
    subAccountIds: string[];
  }) => api.post<RegisterResponse>("/v1/merchants/register", payload),
};
