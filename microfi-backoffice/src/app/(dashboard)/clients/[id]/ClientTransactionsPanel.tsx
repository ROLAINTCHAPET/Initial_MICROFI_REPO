"use client";

import { useEffect, useState } from "react";
import { Badge } from "@/components/Badge";
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

export function ClientTransactionsPanel({
  clientId,
  clientLabel,
  agentNamesById,
  generatedBy,
}: {
  clientId: string;
  clientLabel: string;
  agentNamesById: Record<string, string>;
  generatedBy: string;
}) {
  const dict = useDictionary();
  const [from, setFrom] = useState(isoDaysAgo(30));
  const [to, setTo] = useState(todayIso());
  const [collections, setCollections] = useState<CollectionResponse[] | null>(null);
  const [error, setError] = useState(false);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);

  const requestKey = `${clientId}:${from}:${to}`;
  const loading = loadedKey !== requestKey;

  useEffect(() => {
    let cancelled = false;
    fetch(`/api/clients/${clientId}/collections?from=${from}T00:00:00Z&to=${to}T23:59:59Z`)
      .then((r) => (r.ok ? r.json() : Promise.reject(r)))
      .then((data: CollectionResponse[]) => {
        if (cancelled) return;
        setCollections(data);
        setError(false);
        setLoadedKey(requestKey);
      })
      .catch(() => {
        if (cancelled) return;
        setError(true);
        setLoadedKey(requestKey);
      });
    return () => {
      cancelled = true;
    };
  }, [clientId, from, to, requestKey]);

  const total = collections?.reduce((sum, c) => sum + c.amountXaf, 0) ?? 0;
  const agentLabel = (agentId: string) => agentNamesById[agentId] ?? dict.clients.transactions.unknownAgent;

  const columns: ExportColumn<CollectionResponse>[] = [
    { header: dict.clients.transactions.colAgent, value: (c) => agentLabel(c.agentId) },
    { header: dict.clients.transactions.colAmount, value: (c) => c.amountXaf },
    { header: dict.clients.transactions.colDate, value: (c) => new Date(c.collectedAt).toLocaleString() },
    {
      header: dict.clients.transactions.colStatus,
      value: (c) => (c.reconciledAt ? dict.clients.transactions.reconciled : dict.clients.transactions.notReconciled),
    },
    { header: dict.clients.transactions.colTerminal, value: (c) => c.terminalId ?? "" },
  ];

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-6 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
        <h2 className="text-h2 text-primary flex items-center gap-2">
          <Icon name="history" className="size-5 text-primary-fixed-dim" />
          {dict.clients.detail.transactionsTitle}
        </h2>
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex items-center gap-2 text-sm text-on-surface-variant">
            {dict.export.from}
            <input
              type="date"
              value={from}
              max={to}
              onChange={(e) => setFrom(e.target.value)}
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
              onChange={(e) => setTo(e.target.value)}
              className="border border-outline-variant rounded-[var(--radius-sm)] px-2 py-1 text-sm text-on-surface bg-surface"
            />
          </label>
          <ExportButtons
            filenameBase={`microfi-client-transactions_${clientId}_${from}_${to}`}
            sheetName={dict.clients.transactions.exportTitle}
            pdfTitle={dict.clients.transactions.exportTitle}
            meta={{ scope: clientLabel, from, to, generatedBy }}
            columns={columns}
            rows={collections ?? []}
          />
        </div>
      </div>

      {loading && <p className="text-sm text-on-surface-variant">{dict.clients.transactions.loading}</p>}
      {!loading && error && <p className="text-sm text-error">{dict.clients.transactions.error}</p>}
      {!loading && !error && collections && collections.length === 0 && (
        <p className="text-sm text-on-surface-variant">{dict.clients.transactions.empty}</p>
      )}

      {!loading && !error && collections && collections.length > 0 && (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-on-surface-variant uppercase tracking-wider border-b border-outline-variant">
                  <th className="py-2 pr-4 font-medium">{dict.clients.transactions.colAgent}</th>
                  <th className="py-2 pr-4 font-medium">{dict.clients.transactions.colAmount}</th>
                  <th className="py-2 pr-4 font-medium">{dict.clients.transactions.colDate}</th>
                  <th className="py-2 pr-4 font-medium">{dict.clients.transactions.colStatus}</th>
                  <th className="py-2 font-medium">{dict.clients.transactions.colTerminal}</th>
                </tr>
              </thead>
              <tbody>
                {collections.map((c) => (
                  <tr key={c.id} className="border-b border-outline-variant/50 last:border-0">
                    <td className="py-2 pr-4 text-primary">{agentLabel(c.agentId)}</td>
                    <td className="py-2 pr-4 font-mono text-on-surface">{c.amountXaf.toLocaleString()} XAF</td>
                    <td className="py-2 pr-4 text-on-surface-variant">
                      {new Date(c.collectedAt).toLocaleString(undefined, {
                        day: "2-digit",
                        month: "2-digit",
                        year: "numeric",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                    <td className="py-2 pr-4">
                      {c.reconciledAt ? (
                        <Badge status="SYNCED" label={dict.clients.transactions.reconciled} />
                      ) : (
                        <Badge status="PENDING" label={dict.clients.transactions.notReconciled} />
                      )}
                    </td>
                    <td className="py-2 font-mono text-xs text-on-surface-variant">{c.terminalId ?? "N/A"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 pt-4 border-t border-outline-variant text-sm font-semibold text-primary">
            {t(dict.clients.transactions.totalLabel, { value: total.toLocaleString() })}
          </div>
        </>
      )}
    </div>
  );
}
