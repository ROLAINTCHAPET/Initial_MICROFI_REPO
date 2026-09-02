"use client";

import { ExportButtons } from "@/components/ExportButtons";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import type { ExportColumn, ExportMeta } from "@/lib/export";

export interface RegistrationExportRow {
  name: string;
  role: string;
  branch: string;
  status: string;
  submittedAt: string;
  reason: string;
}

// Same RSC-boundary constraint as OfjExportButtons: a Server Component can hand this client
// component only plain, already-resolved strings — never a Map or a column array of functions.
export function RegistrationsExportButtons({
  filenameBase,
  meta,
  rows,
}: {
  filenameBase: string;
  meta: Omit<ExportMeta, "locale">;
  rows: RegistrationExportRow[];
}) {
  const dict = useDictionary();

  const columns: ExportColumn<RegistrationExportRow>[] = [
    { header: dict.registrations.export.colName, value: (r) => r.name },
    { header: dict.registrations.export.colRole, value: (r) => r.role },
    { header: dict.registrations.export.colBranch, value: (r) => r.branch },
    { header: dict.registrations.export.colStatus, value: (r) => r.status },
    { header: dict.registrations.export.colSubmittedAt, value: (r) => r.submittedAt },
    { header: dict.registrations.export.colReason, value: (r) => r.reason },
  ];

  return (
    <ExportButtons
      filenameBase={filenameBase}
      sheetName={dict.registrations.export.title}
      pdfTitle={dict.registrations.export.title}
      meta={meta}
      columns={columns}
      rows={rows}
    />
  );
}
