/** `ms` → "3:07" ou "1:02:45". `null` (duração desconhecida) → "—". */
export function duracao(ms: number | null): string {
  if (ms === null) return "—";
  const s = Math.round(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const ss = String(s % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${ss}` : `${m}:${ss}`;
}
