/** The Cyrus mark: an indigo rounded-square badge, a white open "C" ring, and an amber dot docking into the gap. */
export function Logo({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 200 200" className={className} role="img" aria-label="Cyrus">
      <rect x="40" y="20" width="160" height="160" rx="32" fill="var(--primary)" />
      <path
        d="M163.3,125 A50,50 0 1 1 163.3,75"
        fill="none"
        stroke="#FFFFFF"
        strokeWidth="14"
        strokeLinecap="round"
      />
      <circle cx="170" cy="100" r="12" fill="#F59E0B" />
    </svg>
  );
}
