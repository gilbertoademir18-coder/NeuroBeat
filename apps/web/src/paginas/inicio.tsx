import type { Saude } from "@neurobeat/shared";
import { useEffect, useState } from "react";
import { api } from "../api.ts";

/** Tela provisória: só confirma que o web fala com a API. */
export function Inicio() {
  const [estado, setEstado] = useState<"carregando" | "ok" | string>("carregando");

  useEffect(() => {
    api<Saude>("/saude")
      .then(() => setEstado("ok"))
      .catch((erro: Error) => setEstado(erro.message));
  }, []);

  return (
    <section className="rounded-xl border border-borda bg-superficie p-6">
      <h1 className="text-xl font-semibold">Biblioteca e playlists</h1>
      <p className="mt-2 text-tinta-2">
        {estado === "carregando" && "Conectando à API…"}
        {estado === "ok" && "API no ar. As telas de biblioteca e playlists chegam nas próximas etapas."}
        {estado !== "carregando" && estado !== "ok" && <span className="text-perigo">{estado}</span>}
      </p>
    </section>
  );
}
