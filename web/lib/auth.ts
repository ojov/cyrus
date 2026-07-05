"use client";

export interface MerchantSession {
  token: string;
  merchantId: string;
  businessName: string;
  businessEmail: string;
}

const STORAGE_KEY = "cyrus_merchant";

export function saveSession(session: MerchantSession) {
  const maxAge = 60 * 60 * 8; // 8h, matches backend token expiry
  // Secure can be set from client JS (only sends the cookie over HTTPS); HttpOnly cannot —
  // browsers only honor that flag on a Set-Cookie response header from the server, so as
  // long as the token is issued to client JS in a JSON body, it's readable by any XSS on
  // the page. Closing that gap fully requires the backend to set the cookie itself.
  const secure = typeof location !== "undefined" && location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `cyrus_token=${encodeURIComponent(session.token)}; path=/; max-age=${maxAge}; SameSite=Lax${secure}`;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function getSession(): MerchantSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as MerchantSession) : null;
  } catch {
    return null;
  }
}

export function clearSession() {
  document.cookie = "cyrus_token=; path=/; max-age=0";
  localStorage.removeItem(STORAGE_KEY);
}
