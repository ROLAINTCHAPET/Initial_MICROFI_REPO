"use client";

import { useEffect, useState } from "react";
import { Badge } from "@/components/Badge";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { CollectionResponse } from "@/lib/types";

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export function AgentCollectionsPanel({ agentId }: { agentId: string }) {
  const dict = useDictionary();
  const [date, setDate] = useState(todayIso());
  const [collections, setCollections] = useState<CollectionResponse[] | null>(null);
  const [error, setError] = useState(false);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);

  const requestKey = `${agentId}:${date}`;
  const loading = loadedKey !== requestKey;

  useEffect(() => {
    let cancelled = false;
    fetch(`/api/agents/${agentId}/collections?date=${date}`)
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
  }, [agentId, date, requestKey]);

  const total = collections?.reduce((sum, c) => sum + c.amountXaf, 0) ?? 0;

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-6 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
        <h2 className="text-h2 text-primary flex items-center gap-2">
          <Icon name="history" className="size-5 text-primary-fixed-dim" />
          {dict.agents.collections.title}
        </h2>
        <label className="flex items-center gap-2 text-sm text-on-surface-variant">
          {dict.agents.collections.dateLabel}
          <input
            type="date"
            value={date}
            max={todayIso()}
            onChange={(e) => setDate(e.target.value)}
            className="border border-outline-variant rounded-[var(--radius-sm)] px-2 py-1 text-sm text-on-surface bg-surface"
          />
        </label>
      </div>

      {loading && <p className="text-sm text-on-surface-variant">{dict.agents.collections.loading}</p>}
      {!loading && error && <p className="text-sm text-error">{dict.agents.collections.error}</p>}
      {!loading && !error && collections && collections.length === 0 && (
        <p className="text-sm text-on-surface-variant">{dict.agents.collections.empty}</p>
      )}

      {!loading && !error && collections && collections.length > 0 && (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-on-surface-variant uppercase tracking-wider border-b border-outline-variant">
                  <th className="py-2 pr-4 font-medium">{dict.agents.collections.colClient}</th>
                  <th className="py-2 pr-4 font-medium">{dict.agents.collections.colAmount}</th>
                  <th className="py-2 pr-4 font-medium">{dict.agents.collections.colTime}</th>
                  <th className="py-2 font-medium">{dict.agents.collections.colStatus}</th>
                </tr>
              </thead>
              <tbody>
                {collections.map((c) => (
                  <tr key={c.id} className="border-b border-outline-variant/50 last:border-0">
                    <td className="py-2 pr-4 text-primary">{c.clientName ?? dict.agents.collections.unknownClient}</td>
                    <td className="py-2 pr-4 font-mono text-on-surface">{c.amountXaf.toLocaleString()} XAF</td>
                    <td className="py-2 pr-4 text-on-surface-variant">{new Date(c.collectedAt).toLocaleTimeString()}</td>
                    <td className="py-2">
                      {c.reconciledAt ? (
                        <Badge status="SYNCED" label={dict.agents.collections.reconciled} />
                      ) : (
                        <Badge status="PENDING" label={dict.agents.collections.notReconciled} />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 pt-4 border-t border-outline-variant text-sm font-semibold text-primary">
            {t(dict.agents.collections.totalLabel, { value: total.toLocaleString() })}
          </div>
        </>
      )}
    </div>
  );
}
