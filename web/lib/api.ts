const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

// Token refresh state
let isRefreshing = false;
let failedQueue: Array<{ resolve: () => void; reject: (error: Error) => void }> = [];

const processQueue = (error: Error | null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error instanceof ApiError ? error : new ApiError(401, "Session expired"));
    } else {
      prom.resolve();
    }
  });
  failedQueue = [];
};

async function attemptRefresh(): Promise<boolean> {
  try {
    const res = await fetch(`${API_URL}/v1/auth/refresh`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    });
    return res.ok;
  } catch {
    return false;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };

  const makeRequest = async (): Promise<T> => {
    // The dashboard session is an httpOnly cookie — this app never reads or attaches the JWT
    // itself. `credentials: "include"` is what makes the browser send/store that cookie on
    // requests to the API's origin (a different origin from this app even in prod).
    const res = await fetch(`${API_URL}${path}`, { ...options, headers, credentials: "include" });

    // Handle 401 by attempting a token refresh
    if (res.status === 401 && !(options as RequestInit & { _retry?: boolean })._retry) {
      if (isRefreshing) {
        // Another request is already refreshing — queue this one
        return new Promise<T>((resolve, reject) => {
          failedQueue.push({
            resolve: () => resolve(makeRequest()),
            reject,
          });
        });
      }

      isRefreshing = true;
      (options as RequestInit & { _retry?: boolean })._retry = true;

      const refreshed = await attemptRefresh();
      processQueue(refreshed ? null : new Error("Refresh failed"));
      isRefreshing = false;

      if (refreshed) {
        return makeRequest();
      } else {
        // Refresh failed — redirect to login
        if (typeof window !== "undefined") {
          window.location.href = "/login";
        }
        throw new ApiError(401, "Session expired");
      }
    }

    // A genuinely empty body (e.g. 204 No Content) is not a parse failure — treat it as
    // an empty envelope so callers destructuring `.data` don't throw on `null`.
    const text = await res.text();
    if (!text) {
      if (!res.ok) throw new ApiError(res.status, `Request failed: ${res.status}`);
      return { data: null } as T;
    }

    let body: unknown;
    try {
      body = JSON.parse(text);
    } catch {
      // Non-JSON body. On a real error status, fall back to a status-based message below;
      // on a reported success we can't trust the payload, so surface that distinctly instead
      // of silently returning null cast as T (which masked real failures as empty success).
      if (res.ok) throw new ApiError(0, "Received an invalid response from the server.");
      body = null;
    }

    if (!res.ok) {
      const message = (body as { message?: string } | null)?.message ?? `Request failed: ${res.status}`;
      throw new ApiError(res.status, message);
    }
    return body as T;
  };

  return makeRequest();
}

const api = {
  post: <T>(path: string, data: unknown) => request<T>(path, { method: "POST", body: JSON.stringify(data) }),
  put: <T>(path: string, data: unknown) => request<T>(path, { method: "PUT", body: JSON.stringify(data) }),
  patch: <T>(path: string, data: unknown) => request<T>(path, { method: "PATCH", body: JSON.stringify(data) }),
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
  data: { merchantId: string; businessName: string; businessEmail: string; superAdmin: boolean };
}

export interface RegisterResponse {
  data: { merchantId: string; businessName: string; businessEmail: string };
}

export const authApi = {
  login: (email: string, password: string) => api.post<LoginResponse>("/v1/auth/login", { email, password }),
  logout: () => api.post<{ data: null }>("/v1/auth/logout", {}),
  refresh: () => api.post<{ data: null }>("/v1/auth/refresh", {}),
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
export interface ReconciliationSummary {
  matched: number;
  discrepancy: number;
  manualReview: number;
  pending: number;
  orphaned: number;
}

export interface DailyInflow {
  date: string;
  amountKobo: number;
}

export interface DashboardStats {
  data: {
    customers: number;
    virtualAccounts: number;
    walletBalance: number;
    reconciliation: ReconciliationSummary;
    inflowLast7Days: DailyInflow[];
  };
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
  revokeApiKey: (id: string) => api.post<{ data: null }>(`/v1/merchants/me/api-keys/${id}/revoke`, {}),
  deleteApiKey: (id: string) => api.delete<{ data: null }>(`/v1/merchants/me/api-keys/${id}`),
};

// ---- Profile ----
export interface ProfileData {
  merchantId: string;
  businessName: string;
  businessEmail: string;
  businessType: string | null;
  phone: string | null;
  bankVerificationNumber: string | null;
}

export const profileApi = {
  get: () => api.get<{ data: ProfileData }>("/v1/merchants/me/profile"),
  update: (payload: { businessName?: string; businessType?: string; phone?: string; bankVerificationNumber?: string }) =>
    api.patch<{ data: ProfileData }>("/v1/merchants/me/profile", payload),
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

export interface BankItem {
  code: string;
  name: string;
}

export interface AccountVerification {
  accountNumber: string;
  accountName: string;
  bankCode: string;
}

export const beneficiaryApi = {
  list: () => api.get<{ data: BeneficiaryItem[] }>("/v1/merchants/me/beneficiaries"),
  // Verify the account against the provider before adding, so the merchant confirms the resolved
  // account name. Throws (422) if it can't be verified.
  verify: (accountNumber: string, bankCode: string) =>
    api.post<{ data: AccountVerification }>("/v1/merchants/me/beneficiaries/verify", { accountNumber, bankCode }),
  // No nickname — the label is the verified account name. Verification is re-checked server-side.
  create: (payload: { accountNumber: string; bankCode: string; bankName: string }) =>
    api.post<{ data: BeneficiaryItem }>("/v1/merchants/me/beneficiaries", payload),
  // The bank code must come from here, not be hand-typed — it's what a payout transfer is keyed by.
  // Server-side cached, so this is cheap to call.
  listBanks: () => api.get<{ data: BankItem[] }>("/v1/merchants/me/beneficiaries/banks"),
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

export interface PayoutPage {
  content: PayoutItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export const payoutApi = {
  list: (page = 0, size = 20) =>
    api.get<{ data: PayoutPage }>(`/v1/merchants/me/payouts?page=${page}&size=${size}`),
  get: (id: string) => api.get<{ data: PayoutItem }>(`/v1/merchants/me/payouts/${id}`),
  create: (payload: { beneficiaryId: string; amount: number; narration?: string }) =>
    api.post<{ data: PayoutItem }>("/v1/merchants/me/payouts", payload),
};

// ---- Payment events (exceptions / misdirected-payment triage) ----
export interface PaymentEventItem {
  id: string;
  requestId: string;
  eventType: string;
  status: string;
  failureReason: string | null;
  statusDetails: string | null;
  amount: number | null;
  accountNumber: string | null;
  customerReference: string | null;
  createdAt: string;
}

export interface PaymentEventDetail extends PaymentEventItem {
  payload: string | null;
}

export interface PaymentEventPage {
  content: PaymentEventItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export const paymentEventApi = {
  list: (status?: string) => {
    const qs = status ? `?status=${encodeURIComponent(status)}` : "";
    return api.get<{ data: PaymentEventPage }>(`/v1/admin/payment-events${qs}`);
  },
  get: (id: string) => api.get<{ data: PaymentEventDetail }>(`/v1/admin/payment-events/${id}`),
  replay: (id: string) => api.post<{ data: null }>(`/v1/admin/payment-events/${id}/replay`, {}),
  reattribute: (id: string, customerReference: string) =>
    api.post<{ data: { transactionId: string; customerReference: string } }>(
      `/v1/admin/payment-events/${id}/reattribute`,
      { customerReference },
    ),
};

// ---- Webhooks ----
export interface WebhookConfigItem {
  url: string;
  hasSecret: boolean;
}

export interface WebhookConfigResponse {
  data: { url: string; secret: string | null; hasSecret: boolean };
}

export interface WebhookDeliveryItem {
  id: string;
  transactionId: string | null;
  eventType: string;
  status: string;
  webhookUrl: string;
  attempts: number;
  lastResponseCode: number | null;
  lastError: string | null;
  nextRetryAt: string | null;
  deliveredAt: string | null;
  createdAt: string;
}

export interface WebhookDeliveryPage {
  content: WebhookDeliveryItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export const webhookApi = {
  get: () => api.get<{ data: WebhookConfigItem | null }>("/v1/merchants/me/webhooks"),
  set: (url: string) => api.put<WebhookConfigResponse>("/v1/merchants/me/webhooks", { url }),
  rotateSecret: () => api.post<WebhookConfigResponse>("/v1/merchants/me/webhooks/rotate-secret", {}),
  remove: () => api.delete<{ data: null }>("/v1/merchants/me/webhooks"),
  deliveries: (status?: string, page?: number) => {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (page != null) params.set("page", String(page));
    const qs = params.toString();
    return api.get<{ data: WebhookDeliveryPage }>(`/v1/merchants/me/webhooks/deliveries${qs ? "?" + qs : ""}`);
  },
};

// ---- Customers (dashboard mirror of the developer-facing, API-key-gated /v1/customers/** ----
export interface CustomerDetail {
  id: string;
  reference: string;
  firstName: string;
  lastName: string | null;
  email: string | null;
  phoneNumber: string | null;
  status: string;
  kycTier: string;
  virtualAccount: {
    id: string;
    accountNumber: string;
    accountName: string | null;
    bankName: string | null;
    currency: string;
    status: string;
  };
  createdAt: string;
}

// The dashboard-only single-customer GET additionally does a live authenticity check against
// Nomba directly (cached backend-side ~10min) — the API-key developer endpoint deliberately
// skips this and stays a plain, fast local-only read.
export interface NombaVerification {
  checked: boolean;
  matched: boolean;
  discrepancies: string[];
  fromCache: boolean;
  checkedAt: string;
}

export interface CustomerDetailResponse {
  data: {
    customer: CustomerDetail;
    nombaVerification: NombaVerification;
  };
}

export interface StatementSummary {
  lifetimeKobo: number;
  transactionCount: number;
  pendingCount: number;
  pendingKobo: number;
  manualReviewCount: number;
  discrepancyCount: number;
  lastTransactionAt: string | null;
}

export interface StatementRow {
  date: string;
  payer: string | null;
  ref: string | null;
  matchStatus: string;
  amountKobo: number;
}

export interface StatementPage {
  content: StatementRow[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface CustomerStatement {
  data: {
    customer: CustomerDetail;
    summary: StatementSummary;
    transactions: StatementPage;
  };
}

export interface StatementFilters {
  from?: string;
  to?: string;
  matchStatus?: string;
  page?: number;
  size?: number;
}

export interface CustomerListItem {
  id: string;
  reference: string;
  firstName: string;
  lastName: string | null;
  email: string | null;
  phoneNumber: string | null;
  status: string;
  kycTier: string;
  virtualAccount: {
    id: string;
    accountNumber: string;
    accountName: string | null;
    bankName: string | null;
    currency: string;
    status: string;
  } | null;
  lifetimeKobo: number;
  createdAt: string;
}

export interface CustomerListPage {
  content: CustomerListItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export const customerApi = {
  list: (page = 0, size = 20) =>
    api.get<{ data: CustomerListPage }>(`/v1/merchants/me/customers?page=${page}&size=${size}`),
  get: (reference: string) =>
    api.get<CustomerDetailResponse>(`/v1/merchants/me/customers/${encodeURIComponent(reference)}`),
  getStatement: (reference: string, filters: StatementFilters = {}) => {
    const qs = new URLSearchParams();
    if (filters.from) qs.set("from", filters.from);
    if (filters.to) qs.set("to", filters.to);
    if (filters.matchStatus) qs.set("matchStatus", filters.matchStatus);
    if (filters.page !== undefined) qs.set("page", String(filters.page));
    if (filters.size !== undefined) qs.set("size", String(filters.size));
    const query = qs.toString();
    return api.get<CustomerStatement>(
      `/v1/merchants/me/customers/${encodeURIComponent(reference)}/statement${query ? `?${query}` : ""}`,
    );
  },
};

// ---- Transactions (dashboard mirror of the developer-facing, API-key-gated /v1/transactions/**) ----
export interface TransactionItem {
  reference: string;
  type: string;
  customerReference: string | null;
  date: string;
  payer: string | null;
  providerTransactionId: string | null;
  status: string;
  matchStatus: string;
  amountKobo: number;
  feeKobo: number | null;
}

export interface TransactionPage {
  content: TransactionItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface TransactionFilters {
  customerReference?: string;
  type?: string;
  status?: string;
  matchStatus?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const transactionApi = {
  list: (filters: TransactionFilters = {}) => {
    const qs = new URLSearchParams();
    if (filters.customerReference) qs.set("customerReference", filters.customerReference);
    if (filters.type) qs.set("type", filters.type);
    if (filters.status) qs.set("status", filters.status);
    if (filters.matchStatus) qs.set("matchStatus", filters.matchStatus);
    if (filters.from) qs.set("from", filters.from);
    if (filters.to) qs.set("to", filters.to);
    if (filters.page !== undefined) qs.set("page", String(filters.page));
    if (filters.size !== undefined) qs.set("size", String(filters.size));
    const query = qs.toString();
    return api.get<{ data: TransactionPage }>(`/v1/merchants/me/transactions${query ? `?${query}` : ""}`);
  },
  get: (reference: string) =>
    api.get<{ data: TransactionItem }>(`/v1/merchants/me/transactions/${encodeURIComponent(reference)}`),
};

// ---- Platform (super-admin only) ----
export interface PlatformOverview {
  custody: {
    walletLiabilitiesKobo: number;
    nombaBalanceKobo: number | null;
    coverageKobo: number | null;
    nombaBalanceAvailable: boolean;
  };
  totals: {
    merchants: number;
    customers: number;
    virtualAccounts: number;
    transactions: number;
    totalConfirmedInflowKobo: number;
    totalPayoutsKobo: number;
  };
  reconciliation: { matched: number; discrepancy: number; manualReview: number; pending: number };
  orphansAndStuck: {
    unattributedOrphans: number;
    stuckPayouts: number;
    stuckPayoutDetails: { id: string; reference: string; merchantName: string | null; amountKobo: number; createdAt: string }[];
  };
  ledgerIntegrity: {
    walletsChecked: number;
    mismatchCount: number;
    allReconciled: boolean;
    mismatches: { walletId: string; merchantName: string | null; balanceKobo: number; ledgerSumKobo: number }[];
  };
}

export interface FeeConfig {
  inflowPercent: number;
  inflowMinKobo: number;
  inflowMaxKobo: number;
  payoutFlatFeeKobo: number;
  updatedAt: string;
}

export interface PlatformProfitSummary {
  expectedProviderBalanceKobo: number;
  actualProviderBalanceKobo: number | null;
  deltaKobo: number | null;
  totalInflowsKobo: number;
  totalOutflowsKobo: number;
  totalFeeAccrualsKobo: number;
  merchantLiabilitiesKobo: number;
  lastSyncAt: string | null;
  reconciliationStatus: string;
}

export const platformApi = {
  overview: () => api.get<{ data: PlatformOverview }>("/v1/platform/overview"),
  profit: () => api.get<{ data: PlatformProfitSummary }>("/v1/platform/profit"),
  getFees: () => api.get<{ data: FeeConfig }>("/v1/platform/fees"),
  updateFees: (feeConfig: Omit<FeeConfig, "updatedAt">) =>
    api.put<{ data: FeeConfig }>("/v1/platform/fees", feeConfig),
};

export const API_BASE_URL = API_URL;
