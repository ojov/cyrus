'use client';

import { useTokenRefresh } from '@/hooks/useTokenRefresh';

/**
 * Client component that wraps the dashboard and sets up proactive token refresh.
 * The actual refresh logic is in the useTokenRefresh hook.
 */
export function TokenRefreshProvider({ children }: { children: React.ReactNode }) {
  useTokenRefresh();
  return <>{children}</>;
}
