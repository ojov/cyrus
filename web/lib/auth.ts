"use client";

export interface MerchantSession {
  token: string;
  merchantId: string;
  businessName: string;
  businessEmail: string;
}

export function saveSession(session: MerchantSession) {
  const maxAge = 60 * 60 * 8; // 8 hours, matches backend token expiry
  document.cookie = `cyrus_token=${encodeURIComponent(session.token)}; path=/; max-age=${maxAge}; SameSite=Lax`;
  localStorage.setItem("cyrus_merchant", JSON.stringify(session));
}

export function getSession(): MerchantSession | null {
  try {
    const raw = localStorage.getItem("cyrus_merchant");
    return raw ? (JSON.parse(raw) as MerchantSession) : null;
  } catch {
    return null;
  }
}

export function clearSession() {
  document.cookie = "cyrus_token=; path=/; max-age=0";
  localStorage.removeItem("cyrus_merchant");
}
