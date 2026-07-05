import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Cyrus — Customer Payment Identity Infrastructure",
  description:
    "Dedicated virtual account infrastructure for Nigerian fintechs. Persistent customer payment identities through a clean developer API.",
};

// Runs before hydration so dark-mode users don't see a flash of the light theme —
// ThemeToggle's own effect (post-mount) can't run early enough to prevent that flash.
const THEME_INIT_SCRIPT = `(function(){try{var s=localStorage.getItem('cyrus-theme');var d=s?s==='dark':window.matchMedia('(prefers-color-scheme: dark)').matches;if(d)document.documentElement.classList.add('dark')}catch(e){}})();`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="h-full antialiased" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body className="min-h-full bg-background text-foreground">{children}</body>
    </html>
  );
}
