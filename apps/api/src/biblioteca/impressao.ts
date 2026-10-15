import { createHash } from "node:crypto";
import { open } from "node:fs/promises";
import { trechoDaAmostra } from "@neurobeat/shared";

/** A impressão digital de um arquivo. A especificação mora em `packages/shared`. */
export async function calcularImpressao(caminho: string, tamanho: number): Promise<string> {
  const { inicio, comprimento } = trechoDaAmostra(tamanho);
  const amostra = Buffer.alloc(comprimento);
  const arquivo = await open(caminho, "r");
  try {
    let lidos = 0;
    while (lidos < comprimento) {
      const { bytesRead } = await arquivo.read(amostra, lidos, comprimento - lidos, inicio + lidos);
      if (bytesRead === 0) break;
      lidos += bytesRead;
    }
  } finally {
    await arquivo.close();
  }
  return createHash("sha256").update(`${tamanho}:`).update(amostra).digest("hex");
}
