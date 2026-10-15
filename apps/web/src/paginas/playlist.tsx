import type { MusicaDto, PlaylistDto } from "@neurobeat/shared";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { api } from "../api.ts";
import { useArrastavel } from "../arrastar.ts";
import { duracao } from "../formato.ts";

/**
 * Uma linha da playlist na tela. A `chave` existe porque a mesma música pode
 * aparecer duas vezes: o id da música não serve de key, e o id do item só
 * existe depois que o servidor grava.
 */
interface Linha {
  chave: string;
  musica: MusicaDto;
}

let proximaChave = 0;
const novaLinha = (musica: MusicaDto): Linha => ({ chave: `n${proximaChave++}`, musica });
const linhasDe = (p: PlaylistDto): Linha[] => p.itens.map((i) => ({ chave: `i${i.id}`, musica: i.musica }));

export function Playlist() {
  const { id } = useParams();
  const navegar = useNavigate();
  const [playlist, setPlaylist] = useState<PlaylistDto | null>(null);
  const [linhas, setLinhas] = useState<Linha[]>([]);
  const [biblioteca, setBiblioteca] = useState<MusicaDto[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [confirmandoExclusao, setConfirmandoExclusao] = useState(false);
  // A lista mais recente, para o `aoLargar` do arrasto (que é criado antes
  // do último setLinhas chegar ao render).
  const linhasRef = useRef<Linha[]>([]);
  linhasRef.current = linhas;

  useEffect(() => {
    Promise.all([api<PlaylistDto>(`/playlists/${id}`), api<MusicaDto[]>("/musicas")])
      .then(([p, m]) => {
        setPlaylist(p);
        setLinhas(linhasDe(p));
        setBiblioteca(m.filter((x) => !x.sumida));
      })
      .catch((e: Error) => setErro(e.message));
  }, [id]);

  /** Grava a ordem atual. A resposta traz os ids de item definitivos. */
  async function salvarItens(lista: Linha[]) {
    setSalvando(true);
    try {
      const p = await api<PlaylistDto>(`/playlists/${id}/itens`, {
        method: "PUT",
        corpo: { musicaIds: lista.map((l) => l.musica.id) },
      });
      setPlaylist(p);
      // Só troca as chaves se ninguém mexeu na lista enquanto salvava.
      if (linhasRef.current === lista) setLinhas(linhasDe(p));
      setErro(null);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setSalvando(false);
    }
  }

  function mudarLinhas(lista: Linha[]) {
    setLinhas(lista);
    linhasRef.current = lista;
    void salvarItens(lista);
  }

  function mover(lista: Linha[], de: number, para: number): Linha[] {
    const nova = [...lista];
    const [linha] = nova.splice(de, 1);
    nova.splice(para, 0, linha!);
    return nova;
  }

  const { arrastando, refDaLinha, alca } = useArrastavel({
    aoMover: (de, para) =>
      setLinhas((atual) => {
        const nova = mover(atual, de, para);
        linhasRef.current = nova;
        return nova;
      }),
    // Grava uma vez ao soltar, e não a cada linha que o dedo atravessa.
    aoLargar: () => void salvarItens(linhasRef.current),
  });

  async function alterar(dados: { nome?: string; loop?: boolean }) {
    try {
      setPlaylist(await api<PlaylistDto>(`/playlists/${id}`, { method: "PATCH", corpo: dados }));
      setErro(null);
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  async function excluir() {
    try {
      await api(`/playlists/${id}`, { method: "DELETE" });
      navegar("/playlists");
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  const total = linhas.reduce((s, l) => s + (l.musica.duracaoMs ?? 0), 0);

  if (!playlist) {
    return erro ? <p className="text-perigo">{erro}</p> : <p className="text-tinta-2">Carregando…</p>;
  }

  return (
    <div className="space-y-6">
      <Link to="/playlists" className="text-sm text-destaque">
        ← Playlists
      </Link>

      {erro && <p className="rounded-lg border border-perigo/40 bg-perigo/10 px-4 py-3 text-perigo">{erro}</p>}

      <section className="rounded-xl border border-borda bg-superficie p-5">
        <div className="flex flex-wrap items-center gap-3">
          <input
            key={playlist.nome}
            defaultValue={playlist.nome}
            onBlur={(e) => {
              const nome = e.target.value.trim();
              if (nome && nome !== playlist.nome) void alterar({ nome });
            }}
            onKeyDown={(e) => e.key === "Enter" && e.currentTarget.blur()}
            aria-label="Nome da playlist"
            className="min-w-0 flex-1 rounded-lg border border-transparent bg-transparent px-2 py-1 text-xl font-semibold hover:border-borda focus:border-borda"
          />
          <label className="flex cursor-pointer items-center gap-2 select-none">
            <input
              type="checkbox"
              checked={playlist.loop}
              onChange={(e) => void alterar({ loop: e.target.checked })}
              className="size-4 accent-[var(--destaque)]"
            />
            Loop
          </label>
        </div>
        <p className="mt-1 px-2 text-sm text-tinta-2 tabular">
          {linhas.length} {linhas.length === 1 ? "faixa" : "faixas"} · {duracao(total)}
          {salvando && " · salvando…"}
          {playlist.loop && " · volta para a primeira no fim"}
        </p>

        {linhas.length === 0 ? (
          <p className="mt-4 px-2 text-tinta-2">Vazia. Adicione músicas pela busca abaixo.</p>
        ) : (
          <ol className="mt-4 divide-y divide-borda">
            {linhas.map((l, i) => (
              <li
                key={l.chave}
                ref={refDaLinha(i)}
                className={`flex items-center gap-2 py-2 ${arrastando === i ? "bg-destaque-suave" : ""}`}
              >
                <span {...alca(i)} className={`${alca(i).className} px-2 text-tinta-3`} aria-hidden>
                  ⠿
                </span>
                <span className="w-7 text-right text-sm text-tinta-3 tabular">{i + 1}</span>
                <div className={`min-w-0 flex-1 ${l.musica.sumida ? "text-tinta-3 line-through" : ""}`}>
                  <p className="truncate">{l.musica.titulo}</p>
                  <p className="truncate text-sm text-tinta-2">
                    {l.musica.artista ?? l.musica.raiz}
                    {l.musica.sumida && " · arquivo sumiu da pasta"}
                  </p>
                </div>
                <span className="text-sm text-tinta-2 tabular">{duracao(l.musica.duracaoMs)}</span>
                <div className="flex">
                  <button
                    type="button"
                    onClick={() => mudarLinhas(mover(linhas, i, i - 1))}
                    disabled={i === 0}
                    aria-label="Subir"
                    className="px-2 text-tinta-2 disabled:opacity-30"
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    onClick={() => mudarLinhas(mover(linhas, i, i + 1))}
                    disabled={i === linhas.length - 1}
                    aria-label="Descer"
                    className="px-2 text-tinta-2 disabled:opacity-30"
                  >
                    ↓
                  </button>
                  <button
                    type="button"
                    onClick={() => mudarLinhas(linhas.filter((_, j) => j !== i))}
                    aria-label="Tirar da playlist"
                    className="px-2 text-tinta-2 hover:text-perigo"
                  >
                    ×
                  </button>
                </div>
              </li>
            ))}
          </ol>
        )}
      </section>

      <Adicionar biblioteca={biblioteca} naPlaylist={linhas} aoAdicionar={(m) => mudarLinhas([...linhas, ...m.map(novaLinha)])} />

      <section className="flex flex-wrap items-center gap-3">
        {confirmandoExclusao ? (
          <>
            <span className="text-sm">Excluir “{playlist.nome}”? As músicas continuam na biblioteca.</span>
            <button type="button" onClick={excluir} className="rounded-lg bg-perigo px-3 py-1.5 text-sm text-white">
              Excluir
            </button>
            <button type="button" onClick={() => setConfirmandoExclusao(false)} className="px-3 py-1.5 text-sm">
              Cancelar
            </button>
          </>
        ) : (
          <button type="button" onClick={() => setConfirmandoExclusao(true)} className="text-sm text-perigo">
            Excluir playlist
          </button>
        )}
      </section>
    </div>
  );
}

function Adicionar({
  biblioteca,
  naPlaylist,
  aoAdicionar,
}: {
  biblioteca: MusicaDto[];
  naPlaylist: Linha[];
  aoAdicionar: (musicas: MusicaDto[]) => void;
}) {
  const [busca, setBusca] = useState("");
  const jaTem = useMemo(() => new Set(naPlaylist.map((l) => l.musica.id)), [naPlaylist]);

  const achadas = useMemo(() => {
    const termo = busca.trim().toLocaleLowerCase("pt-BR");
    if (!termo) return [];
    return biblioteca.filter((m) =>
      [m.titulo, m.artista, m.album, m.caminho].some((t) => t?.toLocaleLowerCase("pt-BR").includes(termo)),
    );
  }, [biblioteca, busca]);
  const novas = achadas.filter((m) => !jaTem.has(m.id));

  return (
    <section className="rounded-xl border border-borda bg-superficie p-5">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-semibold">Adicionar músicas</h2>
        <input
          type="search"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          placeholder="Buscar título, artista, pasta…"
          className="min-w-0 flex-1 rounded-lg border border-borda bg-fundo px-3 py-2"
        />
        {novas.length > 1 && (
          <button type="button" onClick={() => aoAdicionar(novas)} className="text-sm text-destaque">
            Adicionar as {novas.length}
          </button>
        )}
      </div>

      {busca.trim() && achadas.length === 0 && <p className="mt-3 text-tinta-2">Nada encontrado.</p>}

      <ul className="mt-3 divide-y divide-borda">
        {achadas.slice(0, 100).map((m) => (
          <li key={m.id} className="flex items-center gap-3 py-2">
            <div className="min-w-0 flex-1">
              <p className="truncate">{m.titulo}</p>
              <p className="truncate text-sm text-tinta-2">
                {m.artista ?? "—"} · {m.raiz}/{m.caminho.split("/").slice(0, -1).join("/")}
              </p>
            </div>
            <span className="text-sm text-tinta-2 tabular">{duracao(m.duracaoMs)}</span>
            {jaTem.has(m.id) && <span className="text-xs text-tinta-3">já está</span>}
            <button
              type="button"
              onClick={() => aoAdicionar([m])}
              aria-label={`Adicionar ${m.titulo}`}
              className="rounded-lg border border-borda px-3 py-1 text-destaque hover:border-destaque"
            >
              +
            </button>
          </li>
        ))}
      </ul>
      {achadas.length > 100 && <p className="mt-2 text-sm text-tinta-3">Mostrando 100 de {achadas.length}. Refine a busca.</p>}
    </section>
  );
}
