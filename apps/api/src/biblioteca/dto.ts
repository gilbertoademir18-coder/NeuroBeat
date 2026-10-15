import { type MusicaDto, tituloDoArquivo } from "@neurobeat/shared";
import type { Musica } from "../generated/prisma/client.ts";

/** O formato que a API devolve de uma música, onde quer que ela apareça. */
export function musicaDto(m: Musica & { raiz: { alias: string } }): MusicaDto {
  return {
    id: m.id,
    raiz: m.raiz.alias,
    caminho: m.caminho,
    titulo: tituloDoArquivo(m.caminho),
    artista: m.artista,
    album: m.album,
    duracaoMs: m.duracaoMs,
    sumida: m.sumidaEm !== null,
  };
}
