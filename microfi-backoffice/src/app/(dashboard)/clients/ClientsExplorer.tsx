"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Badge } from "@/components/Badge";
import { Icon } from "@/components/Icon";
import { ExportButtons } from "@/components/ExportButtons";
import type { ClientResponse } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import type { ExportColumn } from "@/lib/export";

export function ClientsExplorer({
  clients,
  scope,
  generatedBy,
}: {
  clients: ClientResponse[];
  scope: string;
  generatedBy: string;
}) {
  const dict = useDictionary();
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return clients;
    return clients.filter((c) => `${c.fullName} ${c.phone} ${c.mfiMemberNo}`.toLowerCase().includes(q));
  }, [clients, query]);

  const columns: ExportColumn<ClientResponse>[] = [
    { header: dict.clients.colName, value: (c) => c.fullName },
    { header: dict.clients.colMemberNo, value: (c) => c.mfiMemberNo },
    { header: dict.clients.colPhone, value: (c) => c.phone },
    { header: dict.clients.colStatus, value: (c) => dict.common.status[c.status] },
  ];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div className="relative max-w-sm w-full">
          <Icon name="search" className="absolute left-3 top-1/2 -translate-y-1/2 size-5 text-outline pointer-events-none" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            type="text"
            placeholder={dict.clients.searchPlaceholder}
            className="w-full h-11 pl-10 pr-4 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm focus:outline-none focus:border-primary transition-colors"
          />
        </div>
        <ExportButtons
          filenameBase={`microfi-clients_${scope}`}
          sheetName={dict.clients.exportTitle}
          pdfTitle={dict.clients.exportTitle}
          meta={{ scope, generatedBy }}
          columns={columns}
          rows={filtered}
        />
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
        <div className="divide-y divide-outline-variant">
          {filtered.map((client) => (
            <Link
              key={client.id}
              href={`/clients/${client.id}`}
              className="card-interactive flex items-center justify-between gap-4 p-4 cursor-pointer"
            >
              <div className="min-w-0">
                <p className="font-semibold text-on-surface truncate">{client.fullName}</p>
                <p className="text-xs text-text-slate mt-0.5">
                  {client.mfiMemberNo} · {client.phone}
                </p>
              </div>
              <div className="flex items-center gap-3 shrink-0">
                <Badge status={client.status} />
                <Icon name="chevron-right" className="size-5 text-outline shrink-0" />
              </div>
            </Link>
          ))}
          {filtered.length === 0 && (
            <div className="p-10 flex flex-col items-center gap-2 text-center text-sm text-text-slate">
              <Icon name="info" className="size-6 text-outline-variant" />
              {clients.length === 0 ? dict.clients.noClients : dict.clients.noMatches}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
