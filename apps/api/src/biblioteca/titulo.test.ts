import { tituloDoArquivo } from "@neurobeat/shared";
import { describe, expect, it } from "vitest";

describe("tituloDoArquivo", () => {
  it("tira a pasta e a extensão", () => {
    expect(tituloDoArquivo("3 Doors Down/Changes.mp3")).toBe("Changes");
  });

  it("só a última extensão sai", () => {
    expect(tituloDoArquivo("Matt Walsh/This Has To Stop..m4a")).toBe("This Has To Stop.");
    expect(tituloDoArquivo("Sonic Heroes/What in Made of....wma")).toBe("What in Made of...");
    expect(tituloDoArquivo("OST/01 - Summer.Days.flac")).toBe("01 - Summer.Days");
  });

  it("arquivo direto na raiz, sem pasta", () => {
    expect(tituloDoArquivo("CTRL - 'HOLD' feat. NULL M V.m4a")).toBe("CTRL - 'HOLD' feat. NULL M V");
  });

  it("nome sem extensão, ou que começa com ponto, fica inteiro", () => {
    expect(tituloDoArquivo("pasta/sem extensao")).toBe("sem extensao");
    expect(tituloDoArquivo(".oculto")).toBe(".oculto");
  });
});
