import { AuthGuard } from "@/components/dashboard/auth-guard";
import { DashboardSidebar } from "@/components/dashboard/sidebar";
import { ThemeToggle } from "@/components/theme-toggle";
import WalletChip from "@/components/dashboard/wallet-chip";
import ProfileDropdown from "@/components/dashboard/profile-dropdown";
import { StatsProvider } from "@/components/dashboard/stats-context";
import { TokenRefreshProvider } from "@/components/dashboard/token-refresh-provider";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <TokenRefreshProvider>
      <AuthGuard>
        <StatsProvider>
          <div className="flex h-screen overflow-hidden">
            <DashboardSidebar />
            <div className="flex min-w-0 flex-1 flex-col">
              <header className="flex h-14 shrink-0 items-center justify-end gap-3 border-b border-border px-6 md:px-8">
                <WalletChip />
                <div className="md:hidden">
                  <ProfileDropdown />
                </div>
                <ThemeToggle />
              </header>
              <main className="flex-1 overflow-y-auto p-6 md:p-8">{children}</main>
            </div>
          </div>
        </StatsProvider>
      </AuthGuard>
    </TokenRefreshProvider>
  );
}
