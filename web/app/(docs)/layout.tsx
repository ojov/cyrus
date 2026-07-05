import { DocsSidebar } from "@/components/docs/docs-sidebar";
import { DocsTopbar } from "@/components/docs/docs-topbar";
import { Pager } from "@/components/docs/pager";

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen overflow-hidden">
      <DocsSidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <DocsTopbar />
        <div id="docs-scroll" className="flex-1 overflow-y-auto">
          <div className="mx-auto max-w-5xl px-6 py-8 md:px-10">
            {children}
            <Pager />
          </div>
        </div>
      </div>
    </div>
  );
}
