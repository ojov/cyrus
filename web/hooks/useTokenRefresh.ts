'use client';

import { useEffect, useRef } from 'react';
import { authApi } from '@/lib/api';

const REFRESH_INTERVAL_MS = 14 * 60 * 1000; // 14 minutes (token is 15 min)
const INITIAL_DELAY_MS = 60 * 1000; // 1 minute delay before first refresh
const LOCK_KEY = 'cyrus_token_refresh_lock';
const LOCK_TTL_MS = 30 * 1000; // 30 second lock TTL

/**
 * Proactively refreshes the JWT before it expires.
 * Uses localStorage-based locking to coordinate across tabs — only one tab refreshes at a time.
 * The refresh endpoint rotates the refresh token, so concurrent calls would fail.
 */
export function useTokenRefresh() {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const refresh = async () => {
      try {
        await authApi.refresh();
      } catch {
        // Refresh failed — the request interceptor will handle redirect to /login
      }
    };

    const acquireLock = (): boolean => {
      const now = Date.now();
      const stored = localStorage.getItem(LOCK_KEY);
      
      if (stored) {
        const lockTime = parseInt(stored, 10);
        // If lock is still valid (not expired), another tab holds it
        if (now - lockTime < LOCK_TTL_MS) {
          return false;
        }
      }
      
      // Acquire the lock
      localStorage.setItem(LOCK_KEY, String(now));
      return true;
    };

    const releaseLock = () => {
      localStorage.removeItem(LOCK_KEY);
    };

    const refreshWithLock = async () => {
      if (!acquireLock()) {
        return; // Another tab is handling the refresh
      }
      
      try {
        await refresh();
      } finally {
        releaseLock();
      }
    };

    // Delay initial refresh to avoid multi-tab collision on mount
    const initialTimeout = setTimeout(() => {
      refreshWithLock();
      intervalRef.current = setInterval(refreshWithLock, REFRESH_INTERVAL_MS);
    }, INITIAL_DELAY_MS);

    return () => {
      clearTimeout(initialTimeout);
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);
}
