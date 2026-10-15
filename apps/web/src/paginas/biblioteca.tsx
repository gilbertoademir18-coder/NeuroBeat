import type { MusicaDto, RaizDto, ResultadoEscaneamento } from "@neurobeat/shared";
import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "../api.ts";

function duracao(ms: number | null): string {
  if (ms === null) return "—";
  const s = Math.round(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const ss = String(s % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${ss}` : `${m}:${ss}`;
}

function resumo(r: ResultadoEscaneamento): string {
  const partes = [
    r.novas && `${r.novas} novas`,
    r.movidas && `${r.movidas} renomeadas ou movidas`,
    r.atualizadas && `${r.atualizadas} com tags alteradas`,
    r.voltaram && `${r.voltaram} voltaram`,
    r.sumidas && `${r.sumidas} sumiram`,
  ].filter(Boolean);
  const tempo = `${(r.duracaoMs / 1000).toFixed(1)} s`;
  return partes.length ? `${partes.join(", ")} (${tempo}).` : `Nada mudou (${tempo}).`;
}

export function Biblioteca() {
  const [raizes, setRaizes] = useState<RaizDto[]>([]);
  const [musicas, setMusicas] = useState<MusicaDto[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [escaneando, setEscaneando] = useState(false);
  const [resultado, setResultado] = useState<ResultadoEscaneamento | null>(null);
  const [filtroRaiz, setFiltroRaiz] = useState("");
  const [busca, setBusca] = useState("");

  const carregar = useCallback(async () => {
    try {
      const [r, m] = await Promise.all([api<RaizDto[]>("/raizes"), api<MusicaDto[]>("/musicas")]);
      setRaizes(r);
      setMusicas(m);
      setErro(null);
    } catch (e) {
      setErro((e as Error).message);
    }
  }, []);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function escanear() {
    setEscaneando(true);
    setErro(null);
    try {
      setResultado(await api<ResultadoEscaneamento>("/biblioteca/escanear", { method: "POST" }));
      await carregar();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEscaneando(false);
    }
  }

  const visiveis = useMemo(() => {
    const termo = busca.trim().toLocaleLowerCase("pt-BR");
    return musicas.filter(
      (m) =>
        (!filtroRaiz || m.raiz === filtroRaiz) &&
        (!termo ||
          [m.titulo, m.artista, m.album, m.caminho].some((t) => t?.toLocaleLowerCase("pt-BR").includes(termo))),
    );
  }, [musicas, filtroRaiz, busca]);

  return (
    <div className="space-y-6">
      {erro && <p className="rounded-lg border border-perigo/40 bg-perigo/10 px-4 py-3 text-perigo">{erro}</p>}

      <section className="rounded-xl border border-borda bg-superficie p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">Pastas de músicas</h2>
          <button
            type="button"
            onClick={escanear}
            disabled={escaneando || raizes.length === 0}
            className="rounded-lg bg-destaque px-4 py-2 font-medium text-sobre-destaque disabled:opacity-50"
          >
            {escaneando ? "Escaneando…" : "Reescanear"}
          </button>
        </div>

        {resultado && (
          <div className="mt-3 text-sm text-tinta-2">
            <p>{resumo(resultado)}</p>
            {resultado.raizes
              .filter((r) => r.erro)
              .map((r) => (
                <p key={r.alias} className="text-perigo">
                  {r.alias}: {r.erro}
                </p>
              ))}
          </div>
        )}

        <ul className="mt-4 divide-y divide-borda">
          {raizes.map((r) => (
            <LinhaRaiz key={r.id} raiz={r} aoMudar={carregar} />
          ))}
        </ul>
        <NovaRaiz aoCriar={carregar} />
      </section>

      <section className="rounded-xl border border-borda bg-superficie p-5">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-lg font-semibold">Músicas</h2>
          <span className="text-sm text-tinta-3 tabular">{visiveis.length}</span>
          <div className="ml-auto flex flex-wrap gap-2">
            <select
              value={filtroRaiz}
              onChange={(e) => setFiltroRaiz(e.target.value)}
              className="rounded-lg border border-borda bg-fundo px-3 py-2"
            >
              <option value="">Todas as pastas</option>
              {raizes.map((r) => (
                <option key={r.id} value={r.alias}>
                  {r.alias}
                </option>
              ))}
            </select>
            <input
              type="search"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              placeholder="Buscar título, artista, pasta…"
              className="w-64 max-w-full rounded-lg border border-borda bg-fundo px-3 py-2"
            />
          </div>
        </div>

        {musicas.length === 0 ? (
          <p className="mt-4 text-tinta-2">
            Nenhuma música ainda. Cadastre uma pasta acima e clique em <strong>Reescanear</strong>.
          </p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-tinta-3">
                <tr>
                  <th className="py-2 pr-3 font-medium">Título</th>
                  <th className="py-2 pr-3 font-medium">Artista</th>
                  <th className="py-2 pr-3 font-medium">Pasta</th>
                  <th className="py-2 text-right font-medium">Duração</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-borda">
                {visiveis.map((m) => (
                  <tr key={m.id} className={m.sumida ? "text-tinta-3 line-through" : undefined}>
                    <td className="py-2 pr-3">{m.titulo}</td>
                    <td className="py-2 pr-3 text-tinta-2">{m.artista ?? "—"}</td>
                    <td className="py-2 pr-3 text-tinta-2" title={m.caminho}>
                      {m.raiz}/{m.caminho.split("/").slice(0, -1).join("/")}
                    </td>
                    <td className="py-2 text-right tabular text-tinta-2">{duracao(m.duracaoMs)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function LinhaRaiz({ raiz, aoMudar }: { raiz: RaizDto; aoMudar: () => Promise<void> }) {
  const [editando, setEditando] = useState(false);
  const [caminho, setCaminho] = useState(raiz.caminho);
  const [erro, setErro] = useState<string | null>(null);

  async function salvar() {
    try {
      await api(`/raizes/${raiz.id}`, { method: "PATCH", corpo: { caminho } });
      setEditando(false);
      setErro(null);
      await aoMudar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  return (
    <li className="py-3">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="font-medium">{raiz.alias}</span>
        <span className="text-sm text-tinta-3 tabular">
          {raiz.musicas} músicas{raiz.sumidas > 0 && `, ${raiz.sumidas} sumidas`}
        </span>
        {!raiz.existe && <span className="text-sm text-perigo">pasta não encontrada</span>}
        {!editando && (
          <button type="button" onClick={() => setEditando(true)} className="ml-auto text-sm text-destaque">
            Trocar pasta
          </button>
        )}
      </div>
      {editando ? (
        <div className="mt-2 flex flex-wrap gap-2">
          <input
            value={caminho}
            onChange={(e) => setCaminho(e.target.value)}
            className="min-w-0 flex-1 rounded-lg border border-borda bg-fundo px-3 py-2 text-sm"
          />
          <button type="button" onClick={salvar} className="rounded-lg bg-destaque px-3 py-2 text-sm text-sobre-destaque">
            Salvar
          </button>
          <button
            type="button"
            onClick={() => {
              setEditando(false);
              setCaminho(raiz.caminho);
              setErro(null);
            }}
            className="rounded-lg px-3 py-2 text-sm text-tinta-2"
          >
            Cancelar
          </button>
        </div>
      ) : (
        <p className="mt-1 break-all text-sm text-tinta-2">{raiz.caminho}</p>
      )}
      {erro && <p className="mt-1 text-sm text-perigo">{erro}</p>}
    </li>
  );
}

function NovaRaiz({ aoCriar }: { aoCriar: () => Promise<void> }) {
  const [alias, setAlias] = useState("");
  const [caminho, setCaminho] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  async function criar(e: React.FormEvent) {
    e.preventDefault();
    try {
      await api("/raizes", { method: "POST", corpo: { alias, caminho } });
      setAlias("");
      setCaminho("");
      setErro(null);
      await aoCriar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  return (
    <form onSubmit={criar} className="mt-4 border-t border-borda pt-4">
      <p className="text-sm font-medium">Adicionar pasta</p>
      <div className="mt-2 flex flex-wrap gap-2">
        <input
          value={alias}
          onChange={(e) => setAlias(e.target.value)}
          placeholder="apelido (ex.: musicas)"
          className="w-48 rounded-lg border border-borda bg-fundo px-3 py-2 text-sm"
        />
        <input
          value={caminho}
          onChange={(e) => setCaminho(e.target.value)}
          placeholder="G:\Meu Drive\#MediaFlow - Músicas"
          className="min-w-0 flex-1 rounded-lg border border-borda bg-fundo px-3 py-2 text-sm"
        />
        <button type="submit" className="rounded-lg bg-destaque px-3 py-2 text-sm text-sobre-destaque">
          Adicionar
        </button>
      </div>
      {erro && <p className="mt-1 text-sm text-perigo">{erro}</p>}
    </form>
  );
}
