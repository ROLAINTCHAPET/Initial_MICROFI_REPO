// A temporary ceiling override expiring after an agent already collected up toward it (or an
// admin lowering the base ceiling mid-day) legitimately leaves cumulativeTodayXaf > effectiveCeilingXaf
// in the data — real state, not corruption. The bar must still stop at 100% (a wider-than-100%
// CSS width just overflows its container), and the percentage text next to it has to agree with
// what the bar shows, or it reads as a bug ("133% Utilization" next to a bar stuck at 100%).
export function ceilingUtilizationPct(cumulativeXaf: number, ceilingXaf: number): number {
  if (ceilingXaf <= 0) return 0;
  return Math.round(Math.min(cumulativeXaf / ceilingXaf, 1) * 100);
}

// Full XAF amount formatting with thousands separators — no K/M abbreviation, so a manager
// reading a dashboard figure always sees the exact amount (e.g. 2 626 000 shows as
// "2,626,000 XAF", never a rounded "2.6M XAF").
export function formatXaf(valueXaf: number): string {
  return `${valueXaf.toLocaleString("en-US")} XAF`;
}
