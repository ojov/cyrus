import type { ReactNode } from "react";

type IconProps = { className?: string };

function Svg({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export const IconDocs = ({ className }: IconProps) => (
  <Svg className={className}><rect x="4" y="4" width="16" height="16" rx="1.5" /><path d="M8 9h8M8 13h5" /></Svg>
);
export const IconMoon = ({ className }: IconProps) => (
  <Svg className={className}><path d="M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z" /></Svg>
);
export const IconSun = ({ className }: IconProps) => (
  <Svg className={className}><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4 12H2M22 12h-2M5 5l1.4 1.4M17.6 17.6L19 19M19 5l-1.4 1.4M6.4 17.6L5 19" /></Svg>
);
export const IconArrowRight = ({ className }: IconProps) => (
  <Svg className={className}><path d="M5 12h14M13 6l6 6-6 6" /></Svg>
);
export const IconArrowLeft = ({ className }: IconProps) => (
  <Svg className={className}><path d="M19 12H5M11 6l-6 6 6 6" /></Svg>
);
export const IconGrid = ({ className }: IconProps) => (
  <Svg className={className}><rect x="4" y="4" width="7" height="7" rx="1" /><rect x="13" y="4" width="7" height="4" rx="1" /><rect x="13" y="11" width="7" height="9" rx="1" /><rect x="4" y="14" width="7" height="6" rx="1" /></Svg>
);
export const IconUsers = ({ className }: IconProps) => (
  <Svg className={className}><circle cx="9" cy="8" r="3" /><path d="M4 20c0-3 2.5-5 5-5s5 2 5 5M16 7a3 3 0 0 1 0 6M18.5 20c0-2-1-3.5-2.5-4.3" /></Svg>
);
export const IconSwap = ({ className }: IconProps) => (
  <Svg className={className}><path d="M4 8h13l-3-3M20 16H7l3 3" /></Svg>
);
export const IconChecklist = ({ className }: IconProps) => (
  <Svg className={className}><path d="M20 7 9 18l-5-5" /><path d="M20 13v6H5" /></Svg>
);
export const IconKey = ({ className }: IconProps) => (
  <Svg className={className}><circle cx="7" cy="15" r="3" /><path d="M9 13 20 2M17 5l2 2M14 8l2 2" /></Svg>
);
export const IconSettings = ({ className }: IconProps) => (
  <Svg className={className}><circle cx="12" cy="12" r="3" /><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.5 5.5l2 2M16.5 16.5l2 2M18.5 5.5l-2 2M7.5 16.5l-2 2" /></Svg>
);
export const IconLogOut = ({ className }: IconProps) => (
  <Svg className={className}><path d="M9 5H5v14h4M16 12H9M14 8l4 4-4 4" /></Svg>
);
export const IconLock = ({ className }: IconProps) => (
  <Svg className={className}><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></Svg>
);
export const IconTrend = ({ className }: IconProps) => (
  <Svg className={className}><path d="M3 17l6-6 4 4 8-8M15 7h6v6" /></Svg>
);
export const IconCard = ({ className }: IconProps) => (
  <Svg className={className}><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 10h18" /></Svg>
);
export const IconCheckCircle = ({ className }: IconProps) => (
  <Svg className={className}><circle cx="12" cy="12" r="9" /><path d="M8.5 12l2.5 2.5 4.5-5" /></Svg>
);
