// Compact XAF amount formatting: never renders a fractional M value.
// Whole millions show as e.g. "2M XAF"; anything else drops to whole thousands, e.g. "400K XAF".
export function formatCompactXaf(valueXaf: number): string {
  if (valueXaf >= 1_000_000 && valueXaf % 1_000_000 === 0) {
    return `${valueXaf / 1_000_000}M XAF`;
  }
  if (valueXaf >= 1000) {
    return `${Math.round(valueXaf / 1000)}K XAF`;
  }
  return `${valueXaf.toLocaleString()} XAF`;
}
