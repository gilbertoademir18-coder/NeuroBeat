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
  titulo: string;
  artista: string | null;
  album: string | null;
  duracaoMs: number | null;
  sumida: boolean;
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
