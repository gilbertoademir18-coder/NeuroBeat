import type { TocadasRecebidas } from "@neurobeat/shared";
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { prisma } from "../lib/prisma.ts";
import { primeiroErro } from "../lib/validacao.ts";

const Envio = z.object({
  tocadas: z
    .array(
      z.object({
        id: z.string().uuid(),
        impressao: z.string().min(1).max(200),
        tocadaEm: z.string().datetime({ offset: true }),
      }),
    )
    .max(5000, "Tocadas demais num envio só."),
});

/**
 * O celular conta as tocadas e manda para cá quando tem rede. O id vem dele,
 * então um envio que caiu no meio e foi repetido não conta em dobro.
 */
export async function rotasTocadas(app: FastifyInstance) {
  app.post("/", async (req, reply): Promise<TocadasRecebidas> => {
    const dados = Envio.safeParse(req.body);
    if (!dados.success) return reply.code(400).send({ erro: primeiroErro(dados.error) });
    const { count } = await prisma.tocada.createMany({
      data: dados.data.tocadas.map((t) => ({ id: t.id, impressao: t.impressao, tocadaEm: new Date(t.tocadaEm) })),
      skipDuplicates: true,
    });
    return { recebidas: count };
  });
}
