"use client";

import { useRouter } from "next/navigation";
import type { BranchResponse } from "@/lib/types";

export function TeamBranchSelector({ branches, selectedBranchId }: { branches: BranchResponse[]; selectedBranchId: string }) {
  const router = useRouter();
  return (
    <select
      value={selectedBranchId}
      onChange={(e) => router.push(`/team?branchId=${e.target.value}`)}
      className="min-h-12 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm cursor-pointer focus:outline-none focus:border-primary transition-colors"
    >
      {branches.map((b) => (
        <option key={b.id} value={b.id}>
          {b.name} ({b.code})
        </option>
      ))}
    </select>
  );
}
