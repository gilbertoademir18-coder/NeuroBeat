/**
 * O contrato entre a API e o web.
 *
 * O pacote é consumido como TypeScript puro, sem build: o `exports` aponta
 * para este arquivo, o `tsx` da API e o Vite do web compilam por conta
 * própria. Assim não existe um `dist` que fica velho depois de um `git pull`
 * (o MediaFlow tropeçou exatamente nisso).
 *
 * O app Android não importa daqui: ele é Kotlin. O que ele precisa (o formato
 * de `GET /api/sync` e a impressão digital) é descrito aqui e espelhado lá à
 * mão.
 */

/** O nome que a rota de saúde devolve, e que o ícone da bandeja confere. */
export const NOME_DO_APP = "NeuroBeat";

/** `GET /api/saude` */
export interface Saude {
  app: typeof NOME_DO_APP;
  ok: true;
}

/* -------------------------------------------------------------------------
 * Impressão digital
 *
 * Identifica a música pelo conteúdo, para sobreviver a renomes e mudanças de
 * pasta. O app Android calcula exatamente a mesma coisa: qualquer mudança
 * aqui precisa ir junto para `apps/android` (e invalida as impressões já
 * gravadas, que teriam de ser recalculadas).
 *
 *   amostra  = 64 KiB do MEIO do arquivo (ou o arquivo todo, se for menor)
 *   impressão = SHA-256( "<tamanho em bytes, decimal>:" + amostra ), em hex
 *
 * Do meio porque as tags ficam nas pontas (ID3v2 no início, ID3v1 no fim):
 * editar o nome do artista não muda a impressão. O tamanho entra no hash para
 * que dois arquivos com o mesmo miolo e tamanhos diferentes não colidam.
 * ---------------------------------------------------------------------- */

export const TAMANHO_DA_AMOSTRA = 64 * 1024;

/** Onde começa e quantos bytes tem a amostra de um arquivo de `tamanho` bytes. */
export function trechoDaAmostra(tamanho: number): { inicio: number; comprimento: number } {
  const comprimento = Math.min(TAMANHO_DA_AMOSTRA, tamanho);
  const inicio = Math.min(
    Math.max(0, Math.floor(tamanho / 2) - TAMANHO_DA_AMOSTRA / 2),
    tamanho - comprimento,
  );
  return { inicio, comprimento };
}

/* -------------------------------------------------------------------------
 * Biblioteca
 * ---------------------------------------------------------------------- */

/**
 * O título de uma música é o nome do arquivo, sem a extensão. Nunca a tag:
 * renomear um arquivo é mais fácil que editar metadados, e o nome
 * acompanha o rename na hora (o scanner não relê tags de música só movida).
 *
 * `"3 Doors Down/Changes.mp3"` → `"Changes"`;
 * `"Matt Walsh/This Has To Stop..m4a"` → `"This Has To Stop."`.
 */
export function tituloDoArquivo(caminho: string): string {
  const nome = caminho.slice(caminho.lastIndexOf("/") + 1);
  const ponto = nome.lastIndexOf(".");
  return ponto > 0 ? nome.slice(0, ponto) : nome;
}

/** `GET /api/raizes` */
export interface RaizDto {
  id: number;
  alias: string;
  caminho: string;
  /** A pasta existe agora no disco do PC? */
  existe: boolean;
  musicas: number;
  sumidas: number;
}

/** `GET /api/musicas` */
export interface MusicaDto {
  id: number;
  raiz: string;
  /** Relativo à raiz, com "/". */
  caminho: string;
  /** Nome do arquivo sem extensão (`tituloDoArquivo`), nunca a tag. */
  titulo: string;
  artista: string | null;
  album: string | null;
  duracaoMs: number | null;
  sumida: boolean;
}

/* -------------------------------------------------------------------------
 * Playlists
 * ---------------------------------------------------------------------- */

/** `GET /api/playlists` */
export interface PlaylistResumoDto {
  id: number;
  nome: string;
  /** Sem uso desde 2026-10-16: o loop é global, do botão do player no app. */
  loop: boolean;
  faixas: number;
  /** Soma das faixas com duração conhecida. */
  duracaoMs: number;
  atualizadaEm: string;
}

/** `GET /api/playlists/:id` e a resposta de toda alteração nela. */
export interface PlaylistDto {
  id: number;
  nome: string;
  /** Sem uso desde 2026-10-16: o loop é global, do botão do player no app. */
  loop: boolean;
  /** Na ordem de tocar. A mesma música pode aparecer mais de uma vez. */
  itens: { id: number; musica: MusicaDto }[];
}

/** `PUT /api/playlists/:id/itens`: a lista inteira, na ordem nova. */
export interface ItensDaPlaylist {
  musicaIds: number[];
}

/* -------------------------------------------------------------------------
 * Sincronização com o app Android
 *
 * Um pacote só, com tudo o que o celular precisa para tocar offline. O app
 * guarda a última cópia e casa cada música com um arquivo local pela
 * `impressao` (nunca pelo caminho). Espelhado à mão em
 * `apps/android/.../Sync.kt`: mudou aqui, muda lá.
 * ---------------------------------------------------------------------- */

/** `GET /api/sync` */
export interface SyncDto {
  /** Versão do formato. Sobe quando um campo muda de sentido. */
  versao: 1;
  geradoEm: string;
  musicas: {
    id: number;
    impressao: string;
    /** Nome do arquivo sem extensão (`tituloDoArquivo`), nunca a tag. */
    titulo: string;
    artista: string | null;
    duracaoMs: number | null;
    /** Só para mostrar ("musicas/3 Doors Down/Changes.mp3"). */
    caminho: string;
  }[];
  playlists: {
    id: number;
    nome: string;
    /** Sem uso desde 2026-10-16: o loop é global, do botão do player no app. */
    loop: boolean;
    /** Na ordem de tocar; pode repetir. */
    musicaIds: number[];
  }[];
  /**
   * Quantas vezes cada música já foi tocada, por impressão digital. Campo
   * acrescentado depois: quem não conhece, ignora (por isso a versão não subiu).
   */
  tocadas: Record<string, number>;
  /**
   * A última vez que cada música tocou (ISO), por impressão digital. Ordena a
   * busca do app com as mais recentes primeiro. Também acrescentado depois, do
   * mesmo jeito que `tocadas`.
   */
  ultimasTocadas: Record<string, string>;
}

/** `POST /api/tocadas`: as tocadas que o celular ainda não tinha enviado. */
export interface EnvioDeTocadas {
  tocadas: {
    /** Gerado no celular (UUID). Reenviar o mesmo id não conta de novo. */
    id: string;
    impressao: string;
    /** ISO 8601. */
    tocadaEm: string;
  }[];
}

/** Resposta de `POST /api/tocadas`. */
export interface TocadasRecebidas {
  /** Quantas eram novas (as repetidas não contam). */
  recebidas: number;
}

/** `POST /api/biblioteca/escanear` */
export interface ResultadoEscaneamento {
  raizes: { alias: string; arquivos: number; erro?: string }[];
  novas: number;
  /** Mesmo conteúdo, outro caminho: o arquivo foi renomeado ou movido. */
  movidas: number;
  /** Mesmo caminho, conteúdo diferente (normalmente tag editada). */
  atualizadas: number;
  /** Estavam sumidas e reapareceram. */
  voltaram: number;
  /** Deixaram de existir no disco agora. Não são apagadas. */
  sumidas: number;
  duracaoMs: number;
}
