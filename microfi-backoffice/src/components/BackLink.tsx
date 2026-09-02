import Link from "next/link";
import { Icon } from "@/components/Icon";

// Same visual as the back link already used on Registrations (new/detail) — an explicit,
// unambiguous way back, distinct from the breadcrumb trail (which shows context, not action).
export function BackLink({ href, label }: { href: string; label: string }) {
  return (
    <Link href={href} className="text-sm text-primary hover:underline underline-offset-2 font-medium flex items-center gap-1 w-fit mb-3">
      <Icon name="arrow-upward" className="size-4 -rotate-90" />
      {label}
    </Link>
  );
}
