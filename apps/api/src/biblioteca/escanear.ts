import { readdir, stat } from "node:fs/promises";
import path from "node:path";
import type { ResultadoEscaneamento } from "@neurobeat/shared";
import { parseFile } from "music-metadata";
import { prisma } from "../lib/prisma.ts";
import { calcularImpressao } from "./impressao.ts";
import { type ArquivoNoDisco, type MusicaConhecida, reconciliar } from "./reconciliar.ts";

const EXTENSOES = new Set([".mp3", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".wma"]);

interface ArquivoLido extends ArquivoNoDisco {
  absoluto: string;
  tamanho: number;
}

/** Relativo à raiz, com "/" e em NFC: o mesmo formato em qualquer máquina. */
function caminhoRelativo(raiz: string, absoluto: string): string {
  return path.relative(raiz, absoluto).split(path.sep).join("/").normalize("NFC");
}

async function lerRaiz(raizId: number, pasta: string): Promise<ArquivoLido[]> {
  const entradas = await readdir(pasta, { recursive: true, withFileTypes: true });
  const arquivos: ArquivoLido[] = [];
  for (const e of entradas) {
    if (!e.isFile() || !EXTENSOES.has(path.extname(e.name).toLowerCase())) continue;
    const absoluto = path.join(e.parentPath, e.name);
    const relativo = caminhoRelativo(pasta, absoluto);
    // Pastas ocultas (".mediaflow" e afins) são de outros programas.
    if (relativo.split("/").some((parte) => parte.startsWith("."))) continue;
    const { size } = await stat(absoluto);
    arquivos.push({
      raizId,
      caminho: relativo,
      absoluto,
      tamanho: size,
      impressao: await calcularImpressao(absoluto, size),
    });
  }
  return arquivos;
}

async function lerTags(arquivo: ArquivoLido) {
  const semExtensao = path.basename(arquivo.caminho, path.extname(arquivo.caminho));
  try {
    const { common, format } = await parseFile(arquivo.absoluto, { skipCovers: true });
    return {
      titulo: common.title?.trim() || semExtensao,
      artista: common.artist?.trim() || null,
      album: common.album?.trim() || null,
      duracaoMs: format.duration ? Math.round(format.duration * 1000) : null,
    };
  } catch {
    // Tag ilegível não impede a música de entrar: fica com o nome do arquivo.
    return { titulo: semExtensao, artista: null, album: null, duracaoMs: null };
  }
}

let emAndamento = false;

/** Lê todas as raízes e atualiza o banco. Uma de cada vez. */
export async function escanearBiblioteca(): Promise<ResultadoEscaneamento> {
  if (emAndamento) {
    throw Object.assign(new Error("Já há um escaneamento em andamento."), { statusCode: 409 });
  }
  emAndamento = true;
  try {
    return await escanear();
  } finally {
    emAndamento = false;
  }
}

async function escanear(): Promise<ResultadoEscaneamento> {
  const inicio = Date.now();
  const raizes = await prisma.raiz.findMany({ orderBy: { alias: "asc" } });

  const resultado: ResultadoEscaneamento = {
    raizes: [],
    novas: 0,
    movidas: 0,
    atualizadas: 0,
    voltaram: 0,
    sumidas: 0,
    duracaoMs: 0,
  };
  const noDisco: ArquivoLido[] = [];
  const lidas = new Set<number>();
  for (const raiz of raizes) {
    try {
      const arquivos = await lerRaiz(raiz.id, raiz.caminho);
      noDisco.push(...arquivos);
      lidas.add(raiz.id);
      resultado.raizes.push({ alias: raiz.alias, arquivos: arquivos.length });
    } catch (erro) {
      const codigo = (erro as NodeJS.ErrnoException).code;
      resultado.raizes.push({
        alias: raiz.alias,
        arquivos: 0,
        erro: codigo === "ENOENT" ? `A pasta não existe: ${raiz.caminho}` : String(erro),
      });
    }
  }

  const conhecidas: MusicaConhecida[] = (
    await prisma.musica.findMany({ select: { id: true, raizId: true, caminho: true, impressao: true, sumidaEm: true } })
  ).map((m) => ({ ...m, sumida: m.sumidaEm !== null }));

  const plano = reconciliar(conhecidas, noDisco, lidas);
  const agora = new Date();
  const lido = (a: ArquivoNoDisco) => a as ArquivoLido;

  for (const m of plano.mantidas) {
    if (m.conteudoMudou) {
      const a = lido(m.arquivo);
      await prisma.musica.update({
        where: { id: m.id },
        data: { impressao: a.impressao, tamanho: a.tamanho, ...(await lerTags(a)), vistaEm: agora, sumidaEm: null },
      });
      resultado.atualizadas++;
    } else if (m.voltou) {
      await prisma.musica.update({ where: { id: m.id }, data: { vistaEm: agora, sumidaEm: null } });
    }
    if (m.voltou) resultado.voltaram++;
  }
  // As que não mudaram nada, de uma vez só.
  const intactas = plano.mantidas.filter((m) => !m.conteudoMudou && !m.voltou).map((m) => m.id);
  if (intactas.length > 0) {
    await prisma.musica.updateMany({ where: { id: { in: intactas } }, data: { vistaEm: agora } });
  }

  for (const m of plano.movidas) {
    await prisma.musica.update({
      where: { id: m.id },
      data: { raizId: m.arquivo.raizId, caminho: m.arquivo.caminho, vistaEm: agora, sumidaEm: null },
    });
    resultado.movidas++;
    if (m.voltou) resultado.voltaram++;
  }

  for (const a of plano.novas.map(lido)) {
    await prisma.musica.create({
      data: {
        raizId: a.raizId,
        caminho: a.caminho,
        tamanho: a.tamanho,
        impressao: a.impressao,
        ...(await lerTags(a)),
        vistaEm: agora,
      },
    });
    resultado.novas++;
  }

  if (plano.sumidas.length > 0) {
    await prisma.musica.updateMany({ where: { id: { in: plano.sumidas } }, data: { sumidaEm: agora } });
    resultado.sumidas = plano.sumidas.length;
  }

  resultado.duracaoMs = Date.now() - inicio;
  return resultado;
}
