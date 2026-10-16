import type { PlaylistDto, PlaylistResumoDto } from "@neurobeat/shared";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router";
import { api } from "../api.ts";
import { duracao } from "../formato.ts";

export function Playlists() {
  const [playlists, setPlaylists] = useState<PlaylistResumoDto[] | null>(null);
  const [nome, setNome] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const navegar = useNavigate();

  useEffect(() => {
    api<PlaylistResumoDto[]>("/playlists")
      .then(setPlaylists)
      .catch((e: Error) => setErro(e.message));
  }, []);

  async function criar(e: React.FormEvent) {
    e.preventDefault();
    try {
      const nova = await api<PlaylistDto>("/playlists", { method: "POST", corpo: { nome } });
      // Playlist nova está vazia: o próximo passo é sempre pôr música nela.
      navegar(`/playlists/${nova.id}`);
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  return (
    <div className="space-y-6">
      {erro && <p className="rounded-lg border border-perigo/40 bg-perigo/10 px-4 py-3 text-perigo">{erro}</p>}

      <form onSubmit={criar} className="flex flex-wrap gap-2 rounded-xl border border-borda bg-superficie p-5">
        <input
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          placeholder="Nome da playlist nova"
          className="min-w-0 flex-1 rounded-lg border border-borda bg-fundo px-3 py-2"
        />
        <button type="submit" className="rounded-lg bg-destaque px-4 py-2 font-medium text-sobre-destaque">
          Criar
        </button>
      </form>

      {playlists?.length === 0 && <p className="text-tinta-2">Nenhuma playlist ainda. Crie a primeira acima.</p>}

      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {playlists?.map((p) => (
          <li key={p.id}>
            <Link
              to={`/playlists/${p.id}`}
              className="block rounded-xl border border-borda bg-superficie p-4 hover:border-destaque"
            >
              <div className="flex items-center gap-2">
                <span className="truncate font-medium">{p.nome}</span>
              </div>
              <p className="mt-1 text-sm text-tinta-2 tabular">
                {p.faixas} {p.faixas === 1 ? "faixa" : "faixas"} · {duracao(p.duracaoMs)}
              </p>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
