"use client";

import { authApi } from "@/lib/api";

/**
 * Non-sensitive profile info only, for display (sidebar greeting, etc). The actual session is an
 * httpOnly cookie set by the backend on login/register — this app never sees or stores the JWT
 * itself, so this data is cosmetic and safe to keep in localStorage.
 */
export interface MerchantSession {
  merchantId: string;
  businessName: string;
  businessEmail: string;
}

const STORAGE_KEY = "cyrus_merchant";

export function saveSession(session: MerchantSession) {
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

/** Clears the httpOnly session cookie (backend call) and the local display-profile cache. */
export async function logout() {
  try {
    await authApi.logout();
  } finally {
    localStorage.removeItem(STORAGE_KEY);
  }
}
