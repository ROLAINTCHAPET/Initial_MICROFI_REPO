// A temporary ceiling override expiring after an agent already collected up toward it (or an
// admin lowering the base ceiling mid-day) legitimately leaves cumulativeTodayXaf > effectiveCeilingXaf
// in the data — real state, not corruption. The bar must still stop at 100% (a wider-than-100%
// CSS width just overflows its container), and the percentage text next to it has to agree with
// what the bar shows, or it reads as a bug ("133% Utilization" next to a bar stuck at 100%).
export function ceilingUtilizationPct(cumulativeXaf: number, ceilingXaf: number): number {
  if (ceilingXaf <= 0) return 0;
  return Math.round(Math.min(cumulativeXaf / ceilingXaf, 1) * 100);
}

// Compact XAF amount formatting, picking the right unit for the magnitude — never lets a
// millions-scale value render in K (e.g. 2 626 000 shows as "2.6M XAF", not "2626K XAF").
export function formatCompactXaf(valueXaf: number): string {
  if (valueXaf >= 1_000_000) {
    const millions = Math.round((valueXaf / 1_000_000) * 10) / 10;
    return `${Number.isInteger(millions) ? millions : millions.toFixed(1)}M XAF`;
  }
  if (valueXaf >= 1000) {
    return `${Math.round(valueXaf / 1000)}K XAF`;
  }
  return `${valueXaf.toLocaleString()} XAF`;
}
