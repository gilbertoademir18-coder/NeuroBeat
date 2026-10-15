import type { PlaylistDto, PlaylistResumoDto } from "@neurobeat/shared";
import type { FastifyInstance, FastifyReply } from "fastify";
import { z } from "zod";
import { musicaDto } from "../biblioteca/dto.ts";
import { prisma } from "../lib/prisma.ts";
import { primeiroErro } from "../lib/validacao.ts";

const Id = z.object({ id: z.coerce.number().int().positive() });
const Nome = z.string().trim().min(1, "Dê um nome à playlist.").max(120, "Nome longo demais.");
const NovaPlaylist = z.object({ nome: Nome });
const Alteracao = z
  .object({ nome: Nome.optional(), loop: z.boolean().optional() })
  .refine((d) => d.nome !== undefined || d.loop !== undefined, "Nada para alterar.");
const Itens = z.object({
  musicaIds: z.array(z.number().int().positive()).max(5000, "Playlist grande demais."),
});

async function carregar(id: number): Promise<PlaylistDto | null> {
  const p = await prisma.playlist.findUnique({
    where: { id },
    include: {
      itens: { orderBy: { posicao: "asc" }, include: { musica: { include: { raiz: { select: { alias: true } } } } } },
    },
  });
  if (!p) return null;
  return {
    id: p.id,
    nome: p.nome,
    loop: p.loop,
    itens: p.itens.map((i) => ({ id: i.id, musica: musicaDto(i.musica) })),
  };
}

function naoAchou(reply: FastifyReply) {
  return reply.code(404).send({ erro: "Playlist não encontrada." });
}

export async function rotasPlaylists(app: FastifyInstance) {
  app.get("/", async (): Promise<PlaylistResumoDto[]> => {
    const playlists = await prisma.playlist.findMany({
      orderBy: { nome: "asc" },
      include: { itens: { select: { musica: { select: { duracaoMs: true } } } } },
    });
    return playlists.map((p) => ({
      id: p.id,
      nome: p.nome,
      loop: p.loop,
      faixas: p.itens.length,
      duracaoMs: p.itens.reduce((s, i) => s + (i.musica.duracaoMs ?? 0), 0),
      atualizadaEm: p.atualizadaEm.toISOString(),
    }));
  });

  app.post("/", async (req, reply) => {
    const dados = NovaPlaylist.safeParse(req.body);
    if (!dados.success) return reply.code(400).send({ erro: primeiroErro(dados.error) });
    const p = await prisma.playlist.create({ data: { nome: dados.data.nome } });
    return reply.code(201).send(await carregar(p.id));
  });

  app.get("/:id", async (req, reply) => {
    const id = Id.safeParse(req.params);
    if (!id.success) return reply.code(400).send({ erro: primeiroErro(id.error) });
    return (await carregar(id.data.id)) ?? naoAchou(reply);
  });

  app.patch("/:id", async (req, reply) => {
    const id = Id.safeParse(req.params);
    const dados = Alteracao.safeParse(req.body);
    if (!id.success) return reply.code(400).send({ erro: primeiroErro(id.error) });
    if (!dados.success) return reply.code(400).send({ erro: primeiroErro(dados.error) });
    const { count } = await prisma.playlist.updateMany({ where: { id: id.data.id }, data: dados.data });
    if (count === 0) return naoAchou(reply);
    return carregar(id.data.id);
  });

  /*
   * A lista inteira de uma vez, e não "adiciona"/"remove"/"move" separados:
   * a tela já tem a ordem que quer, e mandar ela inteira numa transação é o
   * que garante que as posições nunca fiquem com buraco ou repetidas.
   */
  app.put("/:id/itens", async (req, reply) => {
    const id = Id.safeParse(req.params);
    const dados = Itens.safeParse(req.body);
    if (!id.success) return reply.code(400).send({ erro: primeiroErro(id.error) });
    if (!dados.success) return reply.code(400).send({ erro: primeiroErro(dados.error) });
    const { musicaIds } = dados.data;

    const existem = await prisma.musica.count({ where: { id: { in: [...new Set(musicaIds)] } } });
    if (existem !== new Set(musicaIds).size) {
      return reply.code(400).send({ erro: "Alguma música da lista não existe mais. Recarregue a página." });
    }

    const achou = await prisma.$transaction(async (tx) => {
      const { count } = await tx.playlist.updateMany({ where: { id: id.data.id }, data: { atualizadaEm: new Date() } });
      if (count === 0) return false;
      await tx.playlistItem.deleteMany({ where: { playlistId: id.data.id } });
      await tx.playlistItem.createMany({
        data: musicaIds.map((musicaId, posicao) => ({ playlistId: id.data.id, musicaId, posicao })),
      });
      return true;
    });
    if (!achou) return naoAchou(reply);
    return carregar(id.data.id);
  });

  // Apaga a playlist e os itens dela. As músicas não são tocadas.
  app.delete("/:id", async (req, reply) => {
    const id = Id.safeParse(req.params);
    if (!id.success) return reply.code(400).send({ erro: primeiroErro(id.error) });
    const { count } = await prisma.playlist.deleteMany({ where: { id: id.data.id } });
    if (count === 0) return naoAchou(reply);
    return reply.code(204).send();
  });
}
