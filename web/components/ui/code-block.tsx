import type { ReactNode } from "react";

// Minimal token highlighter for the HTTP/JSON samples (matches the design's colors).
// Groups: 1 comment · 2 json-key · 3 string · 4 http-method · 5 number.
// The (?<!:) guard keeps `https://` from being treated as a comment.
const TOKENS =
  /(#[^\n]*|(?<!:)\/\/[^\n]*)|("(?:[^"\\]|\\.)*")(?=\s*:)|("(?:[^"\\]|\\.)*")|\b(POST|GET|PUT|PATCH|DELETE)\b|\b(\d+)\b/g;

export function Code({ children }: { children: string }) {
  const out: ReactNode[] = [];
  let last = 0;
  let key = 0;
  for (const m of children.matchAll(TOKENS)) {
    const idx = m.index ?? 0;
    if (idx > last) out.push(children.slice(last, idx));
    const cls = m[1] ? "tok-c" : m[2] ? "tok-k" : m[3] ? "tok-s" : m[4] ? "tok-m" : "tok-s";
    out.push(
      <span key={key++} className={cls}>
        {m[0]}
      </span>,
    );
    last = idx + m[0].length;
  }
  if (last < children.length) out.push(children.slice(last));
  return <pre className="code-block">{out}</pre>;
}
