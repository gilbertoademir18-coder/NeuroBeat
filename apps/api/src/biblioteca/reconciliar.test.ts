import { describe, expect, it } from "vitest";
import { type ArquivoNoDisco, type MusicaConhecida, reconciliar } from "./reconciliar.ts";

const MUSICAS = 1;
const AUDIOS = 2;

function conhecida(id: number, caminho: string, impressao: string, extra: Partial<MusicaConhecida> = {}) {
  return { id, raizId: MUSICAS, caminho, impressao, sumida: false, ...extra };
}
function arquivo(caminho: string, impressao: string, raizId = MUSICAS): ArquivoNoDisco {
  return { raizId, caminho, impressao };
}
const TODAS = new Set([MUSICAS, AUDIOS]);

describe("reconciliar", () => {
  it("nada mudou", () => {
    const plano = reconciliar([conhecida(1, "Queen/a.mp3", "x")], [arquivo("Queen/a.mp3", "x")], TODAS);
    expect(plano.mantidas).toEqual([{ id: 1, arquivo: arquivo("Queen/a.mp3", "x"), conteudoMudou: false, voltou: false }]);
    expect(plano.movidas).toEqual([]);
    expect(plano.novas).toEqual([]);
    expect(plano.sumidas).toEqual([]);
  });

  it("arquivo renomeado continua sendo a mesma música", () => {
    const plano = reconciliar([conhecida(1, "Queen/a.mp3", "x")], [arquivo("Queen/01 - a.mp3", "x")], TODAS);
    expect(plano.movidas).toEqual([{ id: 1, arquivo: arquivo("Queen/01 - a.mp3", "x"), voltou: false }]);
    expect(plano.novas).toEqual([]);
    expect(plano.sumidas).toEqual([]);
  });

  it("pasta renomeada leva todas as faixas junto", () => {
    const plano = reconciliar(
      [conhecida(1, "3 Doors Down/a.mp3", "x"), conhecida(2, "3 Doors Down/b.mp3", "y")],
      [arquivo("3DD/a.mp3", "x"), arquivo("3DD/b.mp3", "y")],
      TODAS,
    );
    expect(plano.movidas.map((m) => [m.id, m.arquivo.caminho])).toEqual([
      [1, "3DD/a.mp3"],
      [2, "3DD/b.mp3"],
    ]);
    expect(plano.sumidas).toEqual([]);
  });

  it("move entre raízes", () => {
    const plano = reconciliar([conhecida(1, "a.mp3", "x")], [arquivo("a.mp3", "x", AUDIOS)], TODAS);
    expect(plano.movidas).toEqual([{ id: 1, arquivo: arquivo("a.mp3", "x", AUDIOS), voltou: false }]);
  });

  it("tag editada no mesmo caminho atualiza a impressão", () => {
    const plano = reconciliar([conhecida(1, "a.mp3", "x")], [arquivo("a.mp3", "x2")], TODAS);
    expect(plano.mantidas[0]?.conteudoMudou).toBe(true);
    expect(plano.novas).toEqual([]);
  });

  it("arquivo apagado vira sumida, e não some do banco", () => {
    const plano = reconciliar([conhecida(1, "a.mp3", "x")], [], TODAS);
    expect(plano.sumidas).toEqual([1]);
  });

  it("sumida que volta no mesmo caminho", () => {
    const plano = reconciliar([conhecida(1, "a.mp3", "x", { sumida: true })], [arquivo("a.mp3", "x")], TODAS);
    expect(plano.mantidas[0]?.voltou).toBe(true);
    expect(plano.sumidas).toEqual([]);
  });

  it("sumida que volta em outro caminho é religada", () => {
    const plano = reconciliar([conhecida(1, "a.mp3", "x", { sumida: true })], [arquivo("novo/a.mp3", "x")], TODAS);
    expect(plano.movidas).toEqual([{ id: 1, arquivo: arquivo("novo/a.mp3", "x"), voltou: true }]);
  });

  it("já sumida não é contada de novo", () => {
    const plano = reconciliar([conhecida(1, "a.mp3", "x", { sumida: true })], [], TODAS);
    expect(plano.sumidas).toEqual([]);
  });

  it("raiz que não foi lida não perde as músicas", () => {
    const plano = reconciliar([conhecida(1, "a.mp3", "x")], [], new Set([AUDIOS]));
    expect(plano.sumidas).toEqual([]);
  });

  it("música de raiz não lida não é roubada por arquivo igual em outra raiz", () => {
    const plano = reconciliar([conhecida(1, "a.mp3", "x")], [arquivo("a.mp3", "x", AUDIOS)], new Set([AUDIOS]));
    expect(plano.movidas).toEqual([]);
    expect(plano.novas).toEqual([arquivo("a.mp3", "x", AUDIOS)]);
  });

  it("cópias do mesmo arquivo não são religadas no chute", () => {
    const plano = reconciliar(
      [conhecida(1, "a.mp3", "x")],
      [arquivo("copia1/a.mp3", "x"), arquivo("copia2/a.mp3", "x")],
      TODAS,
    );
    expect(plano.movidas).toEqual([]);
    expect(plano.novas).toHaveLength(2);
    expect(plano.sumidas).toEqual([1]);
  });

  it("cópia nova ao lado da original não move a original", () => {
    const plano = reconciliar([conhecida(1, "a.mp3", "x")], [arquivo("a.mp3", "x"), arquivo("b/a.mp3", "x")], TODAS);
    expect(plano.mantidas.map((m) => m.id)).toEqual([1]);
    expect(plano.novas).toEqual([arquivo("b/a.mp3", "x")]);
  });

  it("arquivo novo de verdade", () => {
    const plano = reconciliar([], [arquivo("a.mp3", "x")], TODAS);
    expect(plano.novas).toEqual([arquivo("a.mp3", "x")]);
  });
});
