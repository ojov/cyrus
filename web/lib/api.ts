const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

function getToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)cyrus_token=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`${API_URL}${path}`, { ...options, headers });
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(body?.message ?? `Request failed: ${res.status}`);
  }
  return body as T;
}

const api = {
  post: <T>(path: string, data: unknown) => request<T>(path, { method: "POST", body: JSON.stringify(data) }),
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

// ---- Auth ----
export interface ApiKeyInfo {
  apiKey: string;
  environment: string;
  createdAt: string;
}

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
    businessName: string;
    businessEmail: string;
    token: string;
  };
}

export const authApi = {
  login: (email: string, password: string) => api.post<LoginResponse>("/v1/auth/login", { email, password }),
  forgotPassword: (email: string) => api.post<{ data: null }>("/v1/auth/forgot-password", { email }),
  resetPassword: (token: string, newPassword: string) => api.post<{ data: null }>("/v1/auth/reset-password", { token, newPassword }),
  resendVerification: (email: string) => api.post<{ data: null }>("/v1/auth/resend-verification", { email }),
  verifyEmail: (token: string) => api.post<{ data: null }>("/v1/auth/verify-email", { token }),
  register: (payload: {
    businessName: string;
    businessEmail: string;
    password: string;
    nombaClientId: string;
    nombaClientSecret: string;
    nombaParentAccountId: string;
    subAccountIds: string[];
  }) => api.post<RegisterResponse>("/v1/auth/register", payload),
};

// ---- Dashboard (JWT-authed) ----
export interface DashboardStats {
  data: { customers: number; virtualAccounts: number; liveModeActive: boolean };
}

export interface ApiKeyItem {
  id: string;
  prefix: string;
  environment: string;
  status: string;
  createdAt: string;
  lastUsedAt: string | null;
}

export interface ApiKeyListResponse {
  data: ApiKeyItem[];
}

export interface CreatedApiKeyResponse {
  data: { apiKeys: ApiKeyInfo[] };
}

export const dashboardApi = {
  stats: () => api.get<DashboardStats>("/v1/merchants/me/stats"),
  listApiKeys: () => api.get<ApiKeyListResponse>("/v1/merchants/me/api-keys"),
  createApiKey: (environment: "TEST" | "LIVE") =>
    api.post<CreatedApiKeyResponse>("/v1/merchants/me/api-keys", { environment }),
  revokeApiKey: (id: string) => api.delete<{ data: null }>(`/v1/merchants/me/api-keys/${id}`),
  goLive: (nombaClientId: string, nombaClientSecret: string) =>
    api.post<CreatedApiKeyResponse>("/v1/merchants/me/go-live", { nombaClientId, nombaClientSecret }),
};

export const API_BASE_URL = API_URL;
