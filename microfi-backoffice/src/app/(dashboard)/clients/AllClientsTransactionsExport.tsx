"use client";

import { useState } from "react";
import { Icon } from "@/components/Icon";
import { ExportButtons } from "@/components/ExportButtons";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { CollectionResponse } from "@/lib/types";
import type { ExportColumn } from "@/lib/export";

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function isoDaysAgo(days: number) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

/**
 * Bulk counterpart to ClientTransactionsPanel (single client, on /clients/[id]) — every
 * collection for every client in the branch currently selected on this page, within a chosen
 * period. Fetched on demand (not on page load) since a whole branch's history can be large and
 * this is an export tool, not a browsing table.
 */
export function AllClientsTransactionsExport({
  branchId,
  scope,
  agentNamesById,
  generatedBy,
}: {
  branchId: string;
  scope: string;
  agentNamesById: Record<string, string>;
  generatedBy: string;
}) {
  const dict = useDictionary();
  const [from, setFrom] = useState(isoDaysAgo(30));
  const [to, setTo] = useState(todayIso());
  const [collections, setCollections] = useState<CollectionResponse[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  async function loadAndReset() {
    setLoading(true);
    setError(false);
    try {
      const res = await fetch(`/api/clients/collections?branchId=${branchId}&from=${from}T00:00:00Z&to=${to}T23:59:59Z`);
      if (!res.ok) throw new Error();
      const data: CollectionResponse[] = await res.json();
      setCollections(data);
    } catch {
      setError(true);
      setCollections(null);
    } finally {
      setLoading(false);
    }
  }

  const agentLabel = (agentId: string) => agentNamesById[agentId] ?? dict.clients.transactions.unknownAgent;

  const columns: ExportColumn<CollectionResponse>[] = [
    { header: dict.clients.transactions.colAccountNumber, value: (c) => c.clientMfiMemberNo ?? "" },
    { header: dict.clients.transactions.colClient, value: (c) => c.clientName ?? "" },
    { header: dict.clients.transactions.colAgent, value: (c) => agentLabel(c.agentId) },
    { header: dict.clients.transactions.colAmount, value: (c) => c.amountXaf },
    { header: dict.clients.transactions.colDate, value: (c) => new Date(c.collectedAt).toISOString() },
    {
      header: dict.clients.transactions.colStatus,
      value: (c) => (c.reconciledAt ? dict.clients.transactions.reconciled : dict.clients.transactions.notReconciled),
    },
    { header: dict.clients.transactions.colTerminal, value: (c) => c.terminalId ?? "" },
  ];

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-6 shadow-sm flex flex-col gap-4">
      <div className="flex items-center gap-2">
        <Icon name="reports" className="size-5 text-primary-fixed-dim" />
        <div>
          <h2 className="text-h2 text-primary">{dict.clients.transactions.allClientsTitle}</h2>
          <p className="text-xs text-on-surface-variant mt-0.5">{dict.clients.transactions.allClientsSubtitle}</p>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <label className="flex items-center gap-2 text-sm text-on-surface-variant">
          {dict.export.from}
          <input
            type="date"
            value={from}
            max={to}
            onChange={(e) => {
              setFrom(e.target.value);
              setCollections(null);
            }}
            className="border border-outline-variant rounded-[var(--radius-sm)] px-2 py-1 text-sm text-on-surface bg-surface"
          />
        </label>
        <label className="flex items-center gap-2 text-sm text-on-surface-variant">
          {dict.export.to}
          <input
            type="date"
            value={to}
            min={from}
            max={todayIso()}
            onChange={(e) => {
              setTo(e.target.value);
              setCollections(null);
            }}
            className="border border-outline-variant rounded-[var(--radius-sm)] px-2 py-1 text-sm text-on-surface bg-surface"
          />
        </label>

        {collections === null ? (
          <button
            type="button"
            onClick={loadAndReset}
            disabled={loading}
            className="inline-flex items-center gap-2 h-9 px-4 rounded-[var(--radius-sm)] bg-primary text-on-primary text-sm font-semibold cursor-pointer transition-transform duration-150 ease-out hover:scale-[1.03] active:scale-95 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {loading ? dict.clients.transactions.loading : dict.export.apply}
          </button>
        ) : (
          <ExportButtons
            filenameBase={`microfi-all-clients-transactions_${branchId}_${from}_${to}`}
            sheetName={dict.clients.transactions.exportTitleAllClients}
            pdfTitle={dict.clients.transactions.exportTitleAllClients}
            meta={{ scope, from, to, generatedBy }}
            columns={columns}
            rows={collections}
            csv
          />
        )}
      </div>

      {error && <p className="text-sm text-error">{dict.clients.transactions.error}</p>}
      {collections !== null && collections.length === 0 && (
        <p className="text-sm text-on-surface-variant">{dict.clients.transactions.empty}</p>
      )}
      {collections !== null && collections.length > 0 && (
        <p className="text-sm text-on-surface-variant">
          {t(dict.clients.transactions.resultSummary, {
            count: collections.length,
            value: collections.reduce((sum, c) => sum + c.amountXaf, 0).toLocaleString(),
          })}
        </p>
      )}
    </div>
  );
}
