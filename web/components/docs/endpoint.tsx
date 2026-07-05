import type { ReactNode } from "react";

/** Presentational HTTP method + path row used across the API reference pages. */
export function Endpoint({ method, path, tag }: { method: string; path: string; tag?: ReactNode }) {
  return (
    <div className="my-2 flex flex-wrap items-center gap-2">
      <span className="method">{method}</span>
      <span className="path">{path}</span>
      {tag}
    </div>
  );
}
