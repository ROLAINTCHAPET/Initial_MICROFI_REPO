"use client";

import { ExportButtons } from "@/components/ExportButtons";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import type { ExportColumn, ExportMeta } from "@/lib/export";

export interface OfjExportRow {
  businessDate?: string;
  agentLabel: string;
  digitalTotalXaf: number;
  physicalTotalXaf: number;
  deltaXaf: number | null;
  status: string;
}

// Server Components (SummaryView/HistoryView in page.tsx) can only hand plain, serializable data
// across the client boundary — a `columns` array of {value: (row) => ...} functions built
// server-side can't cross into a "use client" component. This wrapper takes only plain row data
// and builds the (function-valued) column definitions itself, entirely client-side.
export function OfjExportButtons({
  filenameBase,
  sheetName,
  pdfTitle,
  meta,
  rows,
  includeBusinessDate = false,
}: {
  filenameBase: string;
  sheetName: string;
  pdfTitle: string;
  meta: Omit<ExportMeta, "locale">;
  rows: OfjExportRow[];
  includeBusinessDate?: boolean;
}) {
  const dict = useDictionary();

  const columns: ExportColumn<OfjExportRow>[] = [
    ...(includeBusinessDate ? [{ header: dict.ofj.history.colBusinessDate, value: (r: OfjExportRow) => r.businessDate ?? "" }] : []),
    { header: dict.dashboard.colAgent, value: (r) => r.agentLabel },
    { header: dict.dashboard.colDigitalTotal, value: (r) => r.digitalTotalXaf },
    { header: dict.dashboard.colPhysicalTotal, value: (r) => r.physicalTotalXaf },
    { header: dict.dashboard.colDelta, value: (r) => r.deltaXaf ?? "" },
    { header: dict.dashboard.colStatus, value: (r) => r.status },
  ];

  return (
    <ExportButtons filenameBase={filenameBase} sheetName={sheetName} pdfTitle={pdfTitle} meta={meta} columns={columns} rows={rows} />
  );
}
