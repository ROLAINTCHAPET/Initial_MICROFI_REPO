"use client";

import { ExportButtons } from "@/components/ExportButtons";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import type { ExportColumn, ExportMeta } from "@/lib/export";

export interface CollectionRejectionExportRow {
  agentLabel: string;
  reason: string;
  requestedAt: string;
  status: string;
  decisionReason: string;
}

// Same RSC-boundary constraint as VarianceExportButtons/OfjExportButtons: the Server Component
// page can only hand this client component plain, already-resolved row data — the (function-
// valued) column definitions are built here, entirely client-side.
export function CollectionRejectionsExportButtons({
  filenameBase,
  meta,
  rows,
}: {
  filenameBase: string;
  meta: Omit<ExportMeta, "locale">;
  rows: CollectionRejectionExportRow[];
}) {
  const dict = useDictionary();

  const columns: ExportColumn<CollectionRejectionExportRow>[] = [
    { header: dict.collectionRejections.colAgent, value: (r) => r.agentLabel },
    { header: dict.collectionRejections.colReason, value: (r) => r.reason },
    { header: dict.collectionRejections.colRequestedAt, value: (r) => r.requestedAt },
    { header: dict.collectionRejections.colStatus, value: (r) => r.status },
    { header: dict.collectionRejections.colDecision, value: (r) => r.decisionReason },
  ];

  return (
    <ExportButtons
      filenameBase={filenameBase}
      sheetName={dict.sidebar.collectionRejections}
      pdfTitle={dict.sidebar.collectionRejections}
      meta={meta}
      columns={columns}
      rows={rows}
    />
  );
}
