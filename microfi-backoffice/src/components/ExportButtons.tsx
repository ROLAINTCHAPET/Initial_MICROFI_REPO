"use client";

import { useState } from "react";
import { Button } from "./Button";
import { Icon } from "./Icon";
import { useDictionary, useLocale } from "@/lib/i18n/I18nProvider";
import { exportToExcel, exportToPdf, type ExportColumn, type ExportMeta } from "@/lib/export";

interface ExportButtonsProps<T> {
  filenameBase: string;
  sheetName: string;
  pdfTitle: string;
  meta: Omit<ExportMeta, "locale">;
  columns: ExportColumn<T>[];
  rows: T[];
}

/**
 * Every export surface in the app renders exactly these two explicit buttons — the user must be
 * able to choose PDF or Excel, never a single generic "Export" that silently picks one. Callers
 * never need to know the current locale themselves: this is the one place that reads it and
 * stamps it onto the generated document, so the export always follows the platform's own
 * language rather than the browser's.
 */
export function ExportButtons<T>({ filenameBase, sheetName, pdfTitle, meta, columns, rows }: ExportButtonsProps<T>) {
  const dict = useDictionary();
  const locale = useLocale();
  const [generatingPdf, setGeneratingPdf] = useState(false);
  const fullMeta: ExportMeta = { ...meta, locale };

  return (
    <div className="flex items-center gap-2">
      <Button
        type="button"
        variant="ghost"
        disabled={rows.length === 0}
        onClick={() => exportToExcel(filenameBase, sheetName, fullMeta, columns, rows)}
      >
        <Icon name="reports" className="size-4" />
        {dict.export.excel}
      </Button>
      <Button
        type="button"
        variant="ghost"
        loading={generatingPdf}
        disabled={rows.length === 0}
        onClick={() => {
          setGeneratingPdf(true);
          try {
            exportToPdf(filenameBase, pdfTitle, fullMeta, columns, rows);
          } finally {
            setGeneratingPdf(false);
          }
        }}
      >
        <Icon name="reports" className="size-4" />
        {dict.export.pdf}
      </Button>
    </div>
  );
}
