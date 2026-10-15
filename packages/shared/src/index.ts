/**
 * O contrato entre a API e o web.
 *
 * O pacote é consumido como TypeScript puro, sem build: o `exports` aponta
 * para este arquivo, o `tsx` da API e o Vite do web compilam por conta
 * própria. Assim não existe um `dist` que fica velho depois de um `git pull`
 * (o MediaFlow tropeçou exatamente nisso).
 *
 * O app Android não importa daqui: ele é Kotlin. O formato JSON que ele lê
 * (`GET /api/sync`) é descrito aqui e espelhado lá à mão.
 */

/** O nome que a rota de saúde devolve, e que o ícone da bandeja confere. */
export const NOME_DO_APP = "NeuroBeat";

/** `GET /api/saude` */
export interface Saude {
  app: typeof NOME_DO_APP;
  ok: true;
}
