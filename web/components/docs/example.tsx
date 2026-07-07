import { Code } from "@/components/ui/code-block";

/** Static request/response example shown on callable reference pages — not an interactive API console. */
export function Example({
  method,
  path,
  body,
  response,
}: {
  method: string;
  path: string;
  body?: string;
  response: string;
}) {
  return (
    <aside className="self-start overflow-hidden rounded-xl border border-border bg-card lg:sticky lg:top-6">
      <div className="flex items-center gap-2 border-b border-border bg-muted px-3.5 py-2.5 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
        Example
      </div>
      <div className="flex flex-col gap-2.5 p-3.5">
        <div className="flex flex-wrap items-center gap-2">
          <span className="method">{method}</span>
          <span className="path">{path}</span>
        </div>
        {body && (
          <>
            <label className="text-xs font-semibold text-muted-foreground">Request body</label>
            <Code>{body}</Code>
          </>
        )}
        <label className="text-xs font-semibold text-muted-foreground">Response</label>
        <Code>{response}</Code>
      </div>
    </aside>
  );
}
