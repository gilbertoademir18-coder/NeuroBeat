import { existsSync } from "node:fs";
import path from "node:path";
import fastifyStatic from "@fastify/static";
import { NOME_DO_APP, type Saude } from "@neurobeat/shared";
import Fastify from "fastify";

// O .env mora na raiz do monorepo. Carregado antes de importar as rotas, que
// trazem o Prisma — e ele lê DATABASE_URL ao criar o cliente.
const RAIZ = path.resolve(import.meta.dirname, "../../..");
try {
  process.loadEnvFile(path.join(RAIZ, ".env"));
} catch {
  // Sem .env: o erro útil ("DATABASE_URL não está definida") vem do Prisma.
}

const { rotasBiblioteca } = await import("./biblioteca/rotas.ts");
const { rotasPlaylists } = await import("./playlists/rotas.ts");
const { rotasSync } = await import("./sync/rotas.ts");

// 3500 é a do app de verdade (a que o ícone da bandeja sobe). O `npm run dev`
// usa a 3501, para dar para mexer no código com o app do dia a dia no ar.
const MODO_DEV = process.argv.includes("--dev");
const PORTA = Number(process.env.PORT ?? (MODO_DEV ? 3501 : 3500));
const WEB_DIST = path.join(RAIZ, "apps/web/dist");

const app = Fastify({ logger: { level: process.env.LOG_LEVEL ?? "info" } });

// Todo erro sai no mesmo formato `{ erro }` que as validações usam — é o campo
// que o front mostra na tela. O detalhe técnico fica no log.
app.setErrorHandler((erro: Error & { statusCode?: number; code?: string }, req, reply) => {
  const status = erro.statusCode ?? 500;
  if (status < 500) return reply.code(status).send({ erro: erro.message });

  req.log.error(erro);
  // Pelo código, e não pela mensagem: o texto vem do Postgres no idioma da
  // instalação. P10xx são os erros de conexão do Prisma; ECONNREFUSED é o
  // Postgres parado.
  const semBanco =
    erro.code?.startsWith("P10") || /ECONNREFUSED|DatabaseNotReachable/.test(JSON.stringify(erro.cause ?? ""));
  return reply.code(500).send({
    erro: semBanco
      ? "Sem conexão com o banco de dados. O PostgreSQL está rodando?"
      : "Erro no servidor. Veja o log pelo ícone da bandeja.",
  });
});

// O ícone da bandeja pergunta aqui se quem ouve na porta é mesmo o NeuroBeat.
app.get("/api/saude", async (): Promise<Saude> => ({ app: NOME_DO_APP, ok: true }));

await app.register(rotasBiblioteca, { prefix: "/api" });
await app.register(rotasPlaylists, { prefix: "/api/playlists" });
await app.register(rotasSync, { prefix: "/api/sync" });

/*
 * O front pronto (`npm run build`) é servido por este mesmo processo: uma
 * porta só para o Tailscale publicar, sem CORS e sem segundo servidor para o
 * ícone vigiar.
 *
 * Em desenvolvimento (`npm run dev`) quem serve o front é o Vite, com recarga
 * instantânea, e ele repassa `/api` para cá.
 */
if (!MODO_DEV && existsSync(WEB_DIST)) {
  await app.register(fastifyStatic, { root: WEB_DIST, wildcard: false });

  app.setNotFoundHandler((req, reply) => {
    // Rota da API que não existe é 404 de verdade, em JSON. Todo o resto é
    // rota do React: devolve o index.html e o roteador do navegador resolve.
    if (req.url.startsWith("/api/")) {
      return reply.code(404).send({ erro: "Rota não encontrada." });
    }
    // Arquivo que não existe (`.js`, `.css`, `.png`...) também é 404, e não o
    // index.html: HTML servido no lugar de um JS deixa a tela branca.
    if (/^[^?]*\.[a-z0-9]+(\?|$)/i.test(req.url)) {
      return reply.code(404).send();
    }
    return reply.header("Cache-Control", "no-cache").sendFile("index.html");
  });
} else if (!MODO_DEV) {
  app.log.warn(`Front não encontrado em ${WEB_DIST} — rode "npm run build". Servindo só a API.`);
}

// Só no localhost, em dev e em produção. Quem chega de fora (celular, outros
// PCs) vem pelo `tailscale serve`, que entra por 127.0.0.1 — e o HTTPS dele é
// o que o Android exige. Com 0.0.0.0, no notebook do trabalho, a API ficaria
// aberta na rede da empresa. Diferente do Trimly e do Creativa de propósito.
await app.listen({ port: PORTA, host: "127.0.0.1" });
