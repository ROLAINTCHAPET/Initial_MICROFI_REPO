import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { api } from "@/lib/api";
import { Sidebar } from "@/components/Sidebar";
import { Header } from "@/components/Header";
import { PageHeaderProvider } from "@/components/PageHeaderContext";
import { MobileNavProvider } from "@/components/MobileNavContext";
import { SosAlertListener } from "@/components/SosAlertListener";
import { CollectionRejectionAlertListener } from "@/components/CollectionRejectionAlertListener";
import { OfjExportAlertListener } from "@/components/OfjExportAlertListener";
import { MisconductReportAlertListener } from "@/components/MisconductReportAlertListener";
import type { AgentMisconductReportResponse, CollectionRejectionRequestResponse, SosResponse } from "@/lib/types";

export default async function DashboardLayout({ children }: { children: ReactNode }) {
  const session = await getSession();
  if (!session) {
    // Defense-in-depth: proxy.ts already redirects unauthenticated requests before this renders.
    redirect("/login");
  }

  // Rejection-request review is ADMIN/BRANCH_MANAGER only (BRANCH_CASHIER can't approve/deny one
  // anyway) — skip the fetch entirely rather than let a cashier's dashboard load 403 on it.
  const canReviewRejections = session.role === "ADMIN" || session.role === "BRANCH_MANAGER";
  // The scheduled closing-time export alert has a wider audience than rejection review — per the
  // explicit ask, the branch cashier sees it too, not just admin/manager.
  const canSeeOfjExportAlerts = session.role === "ADMIN" || session.role === "BRANCH_MANAGER" || session.role === "BRANCH_CASHIER";
  const [unresolvedSos, pendingRejections, pendingMisconductReports] = await Promise.all([
    api.get<SosResponse[]>("/admin/sos-events?unresolvedOnly=true").catch(() => []),
    canReviewRejections
      ? api.get<CollectionRejectionRequestResponse[]>("/admin/collection-rejection-requests?status=PENDING").catch(() => [])
      : Promise.resolve([]),
    canReviewRejections
      ? api.get<AgentMisconductReportResponse[]>("/admin/misconduct-reports?unresolvedOnly=true").catch(() => [])
      : Promise.resolve([]),
  ]);

  return (
    <PageHeaderProvider>
      <MobileNavProvider>
        <div className="h-full font-body text-body text-on-surface antialiased overflow-hidden">
          <Header
            login={session.sub}
            role={session.role}
            unresolvedSosCount={unresolvedSos.length}
            pendingRejectionCount={pendingRejections.length}
            pendingMisconductCount={pendingMisconductReports.length}
          />
          <Sidebar role={session.role} />
          <SosAlertListener />
          {canReviewRejections && <CollectionRejectionAlertListener />}
          {canReviewRejections && <MisconductReportAlertListener />}
          {canSeeOfjExportAlerts && <OfjExportAlertListener />}
          <main className="md:ml-64 mt-20 p-4 md:p-6 h-[calc(100vh-80px)] overflow-y-auto relative z-10 flex-col flex">
            {children}
          </main>
        </div>
      </MobileNavProvider>
    </PageHeaderProvider>
  );
}
