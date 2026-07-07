const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };

  // The dashboard session is an httpOnly cookie — this app never reads or attaches the JWT
  // itself. `credentials: "include"` is what makes the browser send/store that cookie on
  // requests to the API's origin (a different origin from this app even in prod).
  const res = await fetch(`${API_URL}${path}`, { ...options, headers, credentials: "include" });

  // A genuinely empty body (e.g. 204 No Content) is not a parse failure — treat it as
  // an empty envelope so callers destructuring `.data` don't throw on `null`.
  const text = await res.text();
  if (!text) {
    if (!res.ok) throw new Error(`Request failed: ${res.status}`);
    return { data: null } as T;
  }

  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    // Non-JSON body. On a real error status, fall back to a status-based message below;
    // on a reported success we can't trust the payload, so surface that distinctly instead
    // of silently returning null cast as T (which masked real failures as empty success).
    if (res.ok) throw new Error("Received an invalid response from the server.");
    body = null;
  }

  if (!res.ok) {
    const message = (body as { message?: string } | null)?.message ?? `Request failed: ${res.status}`;
    throw new Error(message);
  }
  return body as T;
}

const api = {
  post: <T>(path: string, data: unknown) => request<T>(path, { method: "POST", body: JSON.stringify(data) }),
  put: <T>(path: string, data: unknown) => request<T>(path, { method: "PUT", body: JSON.stringify(data) }),
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

// ---- Auth ----
export interface ApiKeyInfo {
  apiKey: string;
  createdAt: string;
}

// The JWT is never in these bodies — the backend sets it as an httpOnly cookie on the response.
export interface LoginResponse {
  data: { merchantId: string; businessName: string; businessEmail: string };
}

export interface RegisterResponse {
  data: { merchantId: string; businessName: string; businessEmail: string };
}

export const authApi = {
  login: (email: string, password: string) => api.post<LoginResponse>("/v1/auth/login", { email, password }),
  logout: () => api.post<{ data: null }>("/v1/auth/logout", {}),
  forgotPassword: (email: string) => api.post<{ data: null }>("/v1/auth/forgot-password", { email }),
  resetPassword: (token: string, newPassword: string) => api.post<{ data: null }>("/v1/auth/reset-password", { token, newPassword }),
  resendVerification: (email: string) => api.post<{ data: null }>("/v1/auth/resend-verification", { email }),
  verifyEmail: (token: string) => api.post<{ data: null }>("/v1/auth/verify-email", { token }),
  register: (payload: { businessName: string; businessEmail: string; password: string }) =>
    api.post<RegisterResponse>("/v1/auth/register", payload),
};

// ---- Dashboard (JWT-authed) ----
// Cyrus runs on a single Nomba account — no TEST/LIVE split. One API key, one wallet,
// one webhook config per merchant.
export interface DashboardStats {
  data: { customers: number; virtualAccounts: number; walletBalance: number };
}

export interface ApiKeyItem {
  id: string;
  prefix: string;
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
  createApiKey: () => api.post<CreatedApiKeyResponse>("/v1/merchants/me/api-keys", {}),
  revokeApiKey: (id: string) => api.delete<{ data: null }>(`/v1/merchants/me/api-keys/${id}`),
};

// ---- Wallet ----
export interface WalletBalance {
  data: { availableBalance: number };
}

export const walletApi = {
  get: () => api.get<WalletBalance>("/v1/merchants/me/wallet"),
};

// ---- Beneficiaries ----
export interface BeneficiaryItem {
  id: string;
  nickname: string;
  accountName: string;
  accountNumber: string;
  bankCode: string;
  bankName: string;
  createdAt: string;
}

export const beneficiaryApi = {
  list: () => api.get<{ data: BeneficiaryItem[] }>("/v1/merchants/me/beneficiaries"),
  create: (payload: { nickname: string; accountNumber: string; bankCode: string; bankName: string }) =>
    api.post<{ data: BeneficiaryItem }>("/v1/merchants/me/beneficiaries", payload),
};

// ---- Payouts ----
export interface PayoutItem {
  id: string;
  reference: string;
  status: string;
  amount: number;
  fee: number | null;
  beneficiaryId: string;
  providerReference: string | null;
  failureReason: string | null;
  createdAt: string;
}

export const payoutApi = {
  list: () => api.get<{ data: { content: PayoutItem[] } }>("/v1/merchants/me/payouts"),
  get: (id: string) => api.get<{ data: PayoutItem }>(`/v1/merchants/me/payouts/${id}`),
  create: (payload: { beneficiaryId: string; amount: number; narration?: string }) =>
    api.post<{ data: PayoutItem }>("/v1/merchants/me/payouts", payload),
};

// ---- Webhooks ----
export interface WebhookConfigItem {
  url: string;
  hasSecret: boolean;
}

export interface WebhookConfigResponse {
  data: { url: string; secret: string | null; hasSecret: boolean };
}

export const webhookApi = {
  get: () => api.get<{ data: WebhookConfigItem | null }>("/v1/merchants/me/webhooks"),
  set: (url: string) => api.put<WebhookConfigResponse>("/v1/merchants/me/webhooks", { url }),
  rotateSecret: () => api.post<WebhookConfigResponse>("/v1/merchants/me/webhooks/rotate-secret", {}),
  remove: () => api.delete<{ data: null }>("/v1/merchants/me/webhooks"),
};

export const API_BASE_URL = API_URL;
