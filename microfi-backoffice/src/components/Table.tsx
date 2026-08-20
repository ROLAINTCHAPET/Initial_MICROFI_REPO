import type { ReactNode } from "react";
import { Icon } from "./Icon";

export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-[var(--radius-md)] border border-outline-variant bg-surface-container-lowest">
      <table className="w-full text-left border-collapse">{children}</table>
    </div>
  );
}

export function Thead({ children }: { children: ReactNode }) {
  return (
    <thead>
      <tr className="bg-surface-container-low border-b border-outline-variant">{children}</tr>
    </thead>
  );
}

export function Th({ children }: { children?: ReactNode }) {
  return <th className="px-4 py-3 font-semibold text-xs text-on-surface-variant uppercase tracking-wider">{children}</th>;
}

export function Tbody({ children }: { children: ReactNode }) {
  return <tbody className="divide-y divide-outline-variant">{children}</tbody>;
}

export function Tr({ children, tint = false }: { children: ReactNode; tint?: boolean }) {
  return <tr className={`transition-colors ${tint ? "bg-error-container/20 hover:bg-error-container/30" : "hover:bg-surface-container-low/60"}`}>{children}</tr>;
}

export function Td({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <td className={`px-4 py-3 align-middle ${className}`}>{children}</td>;
}

export function EmptyState({ children }: { children: ReactNode }) {
  return (
    <div className="p-10 flex flex-col items-center gap-2 text-center text-sm text-text-slate">
      <Icon name="info" className="size-6 text-outline-variant" />
      {children}
    </div>
  );
}
