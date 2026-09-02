"use client";

import { ExportButtons } from "@/components/ExportButtons";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import type { ExportColumn, ExportMeta } from "@/lib/export";

export interface VarianceExportRow {
  agentLabel: string;
  amountXaf: number;
  status: string;
  recordedAt: string;
  writtenOffReason: string;
}

// Same RSC-boundary constraint as OfjExportButtons/RegistrationsExportButtons: the Server
// Component page can only hand this client component plain, already-resolved row data — the
// (function-valued) column definitions are built here, entirely client-side.
export function VarianceExportButtons({
  filenameBase,
  meta,
  rows,
}: {
  filenameBase: string;
  meta: Omit<ExportMeta, "locale">;
  rows: VarianceExportRow[];
}) {
  const dict = useDictionary();

  const columns: ExportColumn<VarianceExportRow>[] = [
    { header: dict.dashboard.colAgent, value: (r) => r.agentLabel },
    { header: dict.ofj.variance.colAmount, value: (r) => r.amountXaf },
    { header: dict.dashboard.colStatus, value: (r) => r.status },
    { header: dict.ofj.variance.colRecorded, value: (r) => r.recordedAt },
    { header: dict.ofj.variance.colWrittenOffReason, value: (r) => r.writtenOffReason },
  ];

  return (
    <ExportButtons
      filenameBase={filenameBase}
      sheetName={dict.ofj.variance.exportTitle}
      pdfTitle={dict.ofj.variance.exportTitle}
      meta={meta}
      columns={columns}
      rows={rows}
    />
  );
}
