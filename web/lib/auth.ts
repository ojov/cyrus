"use client";

import { useEffect, useState } from "react";
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
  superAdmin?: boolean;
}

const STORAGE_KEY = "cyrus_merchant";
const SESSION_EVENT = "cyrus:session-changed";

export function saveSession(session: MerchantSession) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  window.dispatchEvent(new Event(SESSION_EVENT));
}

export function getSession(): MerchantSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as MerchantSession) : null;
  } catch {
    return null;
  }
}

/**
 * Reactive view of the cached session. Starts `null` on the server and on the initial client
 * render — matching, since neither has access to localStorage yet — then reads the real value in
 * an effect (after hydration) and again whenever `saveSession`/`logout` change it. Components that
 * read `getSession()` directly as a plain variable never notice a later `saveSession()` call and
 * never match server output on first paint; this hook fixes both.
 */
export function useSession(): MerchantSession | null {
  const [session, setSession] = useState<MerchantSession | null>(null);

  useEffect(() => {
    Promise.resolve().then(() => setSession(getSession()));
    function handle() {
      Promise.resolve().then(() => setSession(getSession()));
    }
    window.addEventListener(SESSION_EVENT, handle);
    return () => window.removeEventListener(SESSION_EVENT, handle);
  }, []);

  return session;
}

/** Clears the httpOnly session cookie (backend call) and the local display-profile cache. */
export async function logout() {
  try {
    await authApi.logout();
  } finally {
    localStorage.removeItem(STORAGE_KEY);
    window.dispatchEvent(new Event(SESSION_EVENT));
  }
}
