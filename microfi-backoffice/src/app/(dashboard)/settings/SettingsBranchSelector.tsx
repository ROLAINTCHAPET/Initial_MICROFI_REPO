"use client";

import { useRouter } from "next/navigation";
import type { BranchResponse } from "@/lib/types";

export function SettingsBranchSelector({ branches, selectedBranchId }: { branches: BranchResponse[]; selectedBranchId: string }) {
  const router = useRouter();
  return (
    <select
      value={selectedBranchId}
      onChange={(e) => router.push(`/settings?branchId=${e.target.value}`)}
      className="min-h-12 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm cursor-pointer focus:outline-none focus:border-primary transition-colors w-full sm:w-auto min-w-0 max-w-full"
    >
      {branches.map((b) => (
        <option key={b.id} value={b.id}>
          {b.name} ({b.code})
        </option>
      ))}
    </select>
  );
}
