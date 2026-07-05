import type { ReactNode } from "react";

/** Docs layout with prose on the left and a sticky panel (Try it) on the right. */
export function TwoCol({ children, aside }: { children: ReactNode; aside: ReactNode }) {
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_340px]">
      <div className="doc-prose">{children}</div>
      {aside}
    </div>
  );
}
