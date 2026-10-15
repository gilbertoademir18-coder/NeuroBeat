import { type SyncDto, tituloDoArquivo } from "@neurobeat/shared";
import type { FastifyInstance } from "fastify";
import { prisma } from "../lib/prisma.ts";

/**
 * O pacote que o app Android baixa. Vai a biblioteca inteira, sumidas
 * inclusive: uma música que sumiu do PC pode ainda estar no celular, e a
 * playlist que aponta para ela continua tocando lá.
 */
export async function rotasSync(app: FastifyInstance) {
  app.get("/", async (): Promise<SyncDto> => {
    const [musicas, playlists] = await Promise.all([
      prisma.musica.findMany({
        include: { raiz: { select: { alias: true } } },
        orderBy: { id: "asc" },
      }),
      prisma.playlist.findMany({
        orderBy: { nome: "asc" },
        include: { itens: { orderBy: { posicao: "asc" }, select: { musicaId: true } } },
      }),
    ]);
    return {
      versao: 1,
      geradoEm: new Date().toISOString(),
      musicas: musicas.map((m) => ({
        id: m.id,
        impressao: m.impressao,
        titulo: tituloDoArquivo(m.caminho),
        artista: m.artista,
        duracaoMs: m.duracaoMs,
        caminho: `${m.raiz.alias}/${m.caminho}`,
      })),
      playlists: playlists.map((p) => ({
        id: p.id,
        nome: p.nome,
        loop: p.loop,
        musicaIds: p.itens.map((i) => i.musicaId),
      })),
    };
  });
}
