import type { z } from "zod";

/** A primeira falha de uma validação, no formato que a tela mostra. */
export function primeiroErro(erro: z.ZodError | undefined): string {
  const problema = erro?.issues[0];
  if (!problema) return "Requisição inválida.";
  const campo = problema.path.join(".");
  return campo ? `${campo}: ${problema.message}` : problema.message;
}
