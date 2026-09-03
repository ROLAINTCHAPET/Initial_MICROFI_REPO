import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { api } from "@/lib/api";
import { Sidebar } from "@/components/Sidebar";
import { Header } from "@/components/Header";
import { PageHeaderProvider } from "@/components/PageHeaderContext";
import { MobileNavProvider } from "@/components/MobileNavContext";
import { SosAlertListener } from "@/components/SosAlertListener";
import type { SosResponse } from "@/lib/types";

export default async function DashboardLayout({ children }: { children: ReactNode }) {
  const session = await getSession();
  if (!session) {
    // Defense-in-depth: proxy.ts already redirects unauthenticated requests before this renders.
    redirect("/login");
  }

  const unresolvedSos = await api.get<SosResponse[]>("/admin/sos-events?unresolvedOnly=true").catch(() => []);

  return (
    <PageHeaderProvider>
      <MobileNavProvider>
        <div className="h-full font-body text-body text-on-surface antialiased overflow-hidden">
          <Header login={session.sub} role={session.role} unresolvedSosCount={unresolvedSos.length} />
          <Sidebar role={session.role} />
          <SosAlertListener />
          <main className="md:ml-64 mt-20 p-4 md:p-6 h-[calc(100vh-80px)] overflow-y-auto relative z-10 flex-col flex">
            {children}
          </main>
        </div>
      </MobileNavProvider>
    </PageHeaderProvider>
  );
}
