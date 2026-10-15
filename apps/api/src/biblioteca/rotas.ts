import { existsSync, statSync } from "node:fs";
import path from "node:path";
import type { MusicaDto, RaizDto } from "@neurobeat/shared";
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { prisma } from "../lib/prisma.ts";
import { primeiroErro } from "../lib/validacao.ts";
import { escanearBiblioteca } from "./escanear.ts";

function ehPasta(caminho: string): boolean {
  try {
    return statSync(caminho).isDirectory();
  } catch {
    return false;
  }
}

const Caminho = z
  .string()
  .trim()
  .min(1, "Informe a pasta.")
  .refine((c) => path.isAbsolute(c), "Use o caminho completo, como G:\\Meu Drive\\Músicas.")
  .refine(ehPasta, "Essa pasta não existe neste computador.");

const NovaRaiz = z.object({
  alias: z
    .string()
    .trim()
    .regex(/^[a-z0-9-]+$/, "Use só letras minúsculas, números e hífen (ex.: musicas)."),
  caminho: Caminho,
});

const Id = z.object({ id: z.coerce.number().int().positive() });

export async function rotasBiblioteca(app: FastifyInstance) {
  app.get("/raizes", async (): Promise<RaizDto[]> => {
    const raizes = await prisma.raiz.findMany({ orderBy: { alias: "asc" } });
    const contagens = await prisma.musica.groupBy({
      by: ["raizId", "sumidaEm"],
      _count: true,
    });
    return raizes.map((r) => {
      const daRaiz = contagens.filter((c) => c.raizId === r.id);
      return {
        id: r.id,
        alias: r.alias,
        caminho: r.caminho,
        existe: existsSync(r.caminho),
        musicas: daRaiz.filter((c) => c.sumidaEm === null).reduce((s, c) => s + c._count, 0),
        sumidas: daRaiz.filter((c) => c.sumidaEm !== null).reduce((s, c) => s + c._count, 0),
      };
    });
  });

  app.post("/raizes", async (req, reply) => {
    const dados = NovaRaiz.safeParse(req.body);
    if (!dados.success) return reply.code(400).send({ erro: primeiroErro(dados.error) });
    if (await prisma.raiz.findUnique({ where: { alias: dados.data.alias } })) {
      return reply.code(409).send({ erro: `Já existe uma raiz "${dados.data.alias}".` });
    }
    const raiz = await prisma.raiz.create({ data: dados.data });
    return reply.code(201).send(raiz);
  });

  // Renomeou a pasta no Drive? É só trocar o caminho aqui: o alias, e com
  // ele as músicas e as playlists, continuam os mesmos.
  app.patch("/raizes/:id", async (req, reply) => {
    const id = Id.safeParse(req.params);
    const dados = z.object({ caminho: Caminho }).safeParse(req.body);
    if (!id.success) return reply.code(400).send({ erro: primeiroErro(id.error) });
    if (!dados.success) return reply.code(400).send({ erro: primeiroErro(dados.error) });
    const raiz = await prisma.raiz.update({ where: { id: id.data.id }, data: { caminho: dados.data.caminho } });
    return raiz;
  });

  app.post("/biblioteca/escanear", async () => escanearBiblioteca());

  app.get("/musicas", async (): Promise<MusicaDto[]> => {
    const musicas = await prisma.musica.findMany({
      include: { raiz: { select: { alias: true } } },
      orderBy: [{ raiz: { alias: "asc" } }, { caminho: "asc" }],
    });
    return musicas.map((m) => ({
      id: m.id,
      raiz: m.raiz.alias,
      caminho: m.caminho,
      titulo: m.titulo ?? m.caminho,
      artista: m.artista,
      album: m.album,
      duracaoMs: m.duracaoMs,
      sumida: m.sumidaEm !== null,
    }));
  });
}
