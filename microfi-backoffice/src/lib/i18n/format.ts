/** Fills `{placeholders}` in a dictionary string — e.g. t(dict.header.sosTooltipUnresolved, { count: 3 }). */
export function t(template: string, vars: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (match, key) => (key in vars ? String(vars[key]) : match));
}
