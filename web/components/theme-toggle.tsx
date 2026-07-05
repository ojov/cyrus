"use client";

import { useEffect } from "react";
import { IconMoon, IconSun } from "@/components/icons";

/**
 * Applies the persisted / system theme on mount (external-system sync, no React
 * state) and toggles the `.dark` class on <html>. The icon is CSS-driven via the
 * `dark:` variant, so there is no theme state to hydrate.
 */
export function ThemeToggle() {
  useEffect(() => {
    const saved = localStorage.getItem("cyrus-theme");
    const isDark = saved ? saved === "dark" : window.matchMedia("(prefers-color-scheme: dark)").matches;
    document.documentElement.classList.toggle("dark", isDark);
  }, []);

  function toggle() {
    const isDark = document.documentElement.classList.toggle("dark");
    localStorage.setItem("cyrus-theme", isDark ? "dark" : "light");
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label="Toggle theme"
      className="grid size-8 place-items-center rounded-md border border-border text-muted-foreground transition hover:bg-accent hover:text-foreground"
    >
      <IconMoon className="size-4 dark:hidden" />
      <IconSun className="hidden size-4 dark:block" />
    </button>
  );
}
