"use client";

import { useEffect, useState } from "react";
import { Badge } from "@/components/Badge";
import { Icon } from "@/components/Icon";
import { Table, Thead, Th, Tbody, Tr, Td, EmptyState } from "@/components/Table";
import { ExportButtons } from "@/components/ExportButtons";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import type { AdminRole, AuditActorType, AuditCategory, AuditLogResponse, BranchResponse } from "@/lib/types";
import type { ExportColumn } from "@/lib/export";

function isoDaysAgo(days: number) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

export function AuditExplorer({
  role,
  ownBranchId,
  branches,
  generatedBy,
}: {
  role: AdminRole;
  ownBranchId: string | null;
  branches: BranchResponse[];
  generatedBy: string;
}) {
  const dict = useDictionary();
  const [branchId, setBranchId] = useState<string>(role === "ADMIN" ? "" : (ownBranchId ?? ""));
  const [category, setCategory] = useState<AuditCategory | "">("");
  const [actorType, setActorType] = useState<AuditActorType | "">("");
  const [from, setFrom] = useState(isoDaysAgo(30));
  const [to, setTo] = useState(isoDaysAgo(0));
  const [logs, setLogs] = useState<AuditLogResponse[] | null>(null);
  const [error, setError] = useState(false);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);

  const requestKey = `${branchId}:${category}:${actorType}:${from}:${to}`;
  const loading = loadedKey !== requestKey;

  useEffect(() => {
    let cancelled = false;
    const query = new URLSearchParams({
      from: `${from}T00:00:00Z`,
      to: `${to}T23:59:59Z`,
    });
    if (branchId) query.set("branchId", branchId);
    if (category) query.set("category", category);
    if (actorType) query.set("actorType", actorType);

    fetch(`/api/audit-log?${query.toString()}`)
      .then((r) => (r.ok ? r.json() : Promise.reject(r)))
      .then((data: AuditLogResponse[]) => {
        if (cancelled) return;
        setLogs(data);
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
  }, [branchId, category, actorType, from, to, requestKey]);

  // actorType alone only distinguishes the admin/agent/client family — actorRole (when present)
  // names the one specific back-office role that actually acted, precise rather than the vague
  // "Admin / Branch Manager / Cashier" bucket label. Only absent for a login that never resolved
  // to a real account (no role to know).
  const actorRoleLabel = (log: AuditLogResponse) => (log.actorRole ? dict.roles[log.actorRole] : dict.audit.actorType[log.actorType]);

  const columns: ExportColumn<AuditLogResponse>[] = [
    { header: dict.audit.table.colTime, value: (r) => new Date(r.occurredAt).toLocaleString() },
    { header: dict.audit.table.colEvent, value: (r) => r.eventType },
    { header: dict.audit.table.colCategory, value: (r) => dict.audit.category[r.category] },
    { header: dict.audit.table.colActor, value: (r) => r.actorLabel },
    { header: dict.audit.table.colActorRole, value: (r) => actorRoleLabel(r) },
    { header: dict.audit.table.colBranch, value: (r) => r.branchLabel ?? "" },
    { header: dict.audit.table.colDetails, value: (r) => r.details },
    { header: dict.audit.table.colStatus, value: (r) => r.status },
  ];

  const scopeLabel = branchId
    ? (branches.find((b) => b.id === branchId)?.name ?? branchId)
    : role === "ADMIN"
      ? dict.export.scopeAllBranches
      : (branches.find((b) => b.id === ownBranchId)?.name ?? dict.export.scopeAllBranches);

  return (
    <div className="flex flex-col gap-4">
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-4 flex flex-wrap items-end gap-4">
        {role === "ADMIN" && (
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-xs font-semibold text-on-surface-variant uppercase tracking-wide">{dict.audit.filters.branch}</span>
            <select
              value={branchId}
              onChange={(e) => setBranchId(e.target.value)}
              className="border border-outline-variant rounded-[var(--radius-sm)] px-3 py-2 text-sm text-on-surface bg-surface min-w-[180px]"
            >
              <option value="">{dict.audit.filters.allBranches}</option>
              {branches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} ({b.code})
                </option>
              ))}
            </select>
          </label>
        )}
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-xs font-semibold text-on-surface-variant uppercase tracking-wide">{dict.audit.filters.category}</span>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as AuditCategory | "")}
            className="border border-outline-variant rounded-[var(--radius-sm)] px-3 py-2 text-sm text-on-surface bg-surface min-w-[180px]"
          >
            <option value="">{dict.audit.filters.allCategories}</option>
            <option value="SECURITY">{dict.audit.category.SECURITY}</option>
            <option value="COMPLIANCE">{dict.audit.category.COMPLIANCE}</option>
            <option value="FINANCIAL">{dict.audit.category.FINANCIAL}</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-xs font-semibold text-on-surface-variant uppercase tracking-wide">{dict.audit.filters.actorType}</span>
          <select
            value={actorType}
            onChange={(e) => setActorType(e.target.value as AuditActorType | "")}
            className="border border-outline-variant rounded-[var(--radius-sm)] px-3 py-2 text-sm text-on-surface bg-surface min-w-[180px]"
          >
            <option value="">{dict.audit.filters.allActorTypes}</option>
            <option value="ADMIN">{dict.audit.actorType.ADMIN}</option>
            <option value="AGENT">{dict.audit.actorType.AGENT}</option>
            <option value="CLIENT">{dict.audit.actorType.CLIENT}</option>
            <option value="SYSTEM">{dict.audit.actorType.SYSTEM}</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-xs font-semibold text-on-surface-variant uppercase tracking-wide">{dict.audit.filters.from}</span>
          <input
            type="date"
            value={from}
            max={to}
            onChange={(e) => setFrom(e.target.value)}
            className="border border-outline-variant rounded-[var(--radius-sm)] px-3 py-2 text-sm text-on-surface bg-surface"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-xs font-semibold text-on-surface-variant uppercase tracking-wide">{dict.audit.filters.to}</span>
          <input
            type="date"
            value={to}
            min={from}
            max={isoDaysAgo(0)}
            onChange={(e) => setTo(e.target.value)}
            className="border border-outline-variant rounded-[var(--radius-sm)] px-3 py-2 text-sm text-on-surface bg-surface"
          />
        </label>
        <div className="ml-auto">
          <ExportButtons
            filenameBase={`microfi-audit-log_${from}_${to}`}
            sheetName={dict.audit.exportTitle}
            pdfTitle={dict.audit.exportTitle}
            meta={{ scope: scopeLabel, from, to, generatedBy }}
            columns={columns}
            rows={logs ?? []}
          />
        </div>
      </div>

      {loading && !error && <p className="text-sm text-on-surface-variant px-1">{dict.audit.loading}</p>}
      {!loading && error && <p className="text-sm text-error px-1">{dict.audit.error}</p>}

      {!loading && !error && logs && logs.length === 0 && <EmptyState>{dict.audit.noResults}</EmptyState>}

      {!loading && !error && logs && logs.length > 0 && (
        <Table>
          <Thead>
            <Th>{dict.audit.table.colTime}</Th>
            <Th>{dict.audit.table.colEvent}</Th>
            <Th>{dict.audit.table.colCategory}</Th>
            <Th>{dict.audit.table.colActor}</Th>
            <Th>{dict.audit.table.colBranch}</Th>
            <Th>{dict.audit.table.colDetails}</Th>
            <Th>{dict.audit.table.colStatus}</Th>
          </Thead>
          <Tbody>
            {logs.map((log) => (
              <Tr key={log.id} tint={log.status === "FAILED"}>
                <Td className="text-on-surface-variant whitespace-nowrap">{new Date(log.occurredAt).toLocaleString()}</Td>
                <Td className="font-medium text-on-surface">{log.eventType}</Td>
                <Td>
                  <span className="flex items-center gap-1.5 text-xs font-semibold text-on-surface-variant">
                    <Icon name={log.category === "SECURITY" ? "lock" : log.category === "COMPLIANCE" ? "public" : "account-balance-wallet"} className="size-4" />
                    {dict.audit.category[log.category]}
                  </span>
                </Td>
                <Td>
                  {log.actorLabel}
                  <span className="block text-xs text-on-surface-variant">{actorRoleLabel(log)}</span>
                </Td>
                <Td className="text-on-surface-variant">{log.branchLabel ?? "N/A"}</Td>
                <Td className="text-on-surface-variant max-w-[320px]">{log.details}</Td>
                <Td>
                  {log.status === "SUCCESS" ? (
                    <Badge status="ACTIVE" label={log.status} />
                  ) : (
                    <Badge status="SUSPENDED" label={log.status} />
                  )}
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}
    </div>
  );
}
