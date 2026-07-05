import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Cyrus — Customer Payment Identity Infrastructure",
  description:
    "Dedicated virtual account infrastructure for Nigerian fintechs. Persistent customer payment identities through a clean developer API.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="h-full antialiased" suppressHydrationWarning>
      <body className="min-h-full bg-background text-foreground">{children}</body>
    </html>
  );
}
