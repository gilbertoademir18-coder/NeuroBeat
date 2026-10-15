/**
 * Compara o que o banco conhece com o que está no disco e diz o que mudou.
 *
 * Função pura, sem banco e sem disco, para poder ser testada caso a caso: é
 * aqui que se decide se uma música foi renomeada (e as playlists continuam
 * apontando para ela) ou se é outra música.
 */

export interface ArquivoNoDisco {
  raizId: number;
  caminho: string;
  impressao: string;
}

export interface MusicaConhecida {
  id: number;
  raizId: number;
  caminho: string;
  impressao: string;
  sumida: boolean;
}

export interface Plano {
  /** Mesmo caminho de antes. */
  mantidas: { id: number; arquivo: ArquivoNoDisco; conteudoMudou: boolean; voltou: boolean }[];
  /** Mesma impressão, outro caminho: renomeada ou movida. */
  movidas: { id: number; arquivo: ArquivoNoDisco; voltou: boolean }[];
  novas: ArquivoNoDisco[];
  /** Ids que estavam no disco e deixaram de estar. */
  sumidas: number[];
}

/**
 * @param raizesEscaneadas As raízes que foram de fato lidas. Uma raiz cuja
 *   pasta não existe (renomeada e ainda não atualizada no web) fica de fora:
 *   as músicas dela não são dadas como sumidas por isso.
 */
export function reconciliar(
  conhecidas: MusicaConhecida[],
  noDisco: ArquivoNoDisco[],
  raizesEscaneadas: ReadonlySet<number>,
): Plano {
  const chave = (raizId: number, caminho: string) => `${raizId}\u0000${caminho}`;
  const porCaminho = new Map(conhecidas.map((m) => [chave(m.raizId, m.caminho), m]));
  const usadas = new Set<number>();
  const plano: Plano = { mantidas: [], movidas: [], novas: [], sumidas: [] };

  // 1. O caso comum: o arquivo continua onde estava.
  const semDono: ArquivoNoDisco[] = [];
  for (const arquivo of noDisco) {
    const musica = porCaminho.get(chave(arquivo.raizId, arquivo.caminho));
    if (!musica) {
      semDono.push(arquivo);
      continue;
    }
    usadas.add(musica.id);
    plano.mantidas.push({
      id: musica.id,
      arquivo,
      conteudoMudou: musica.impressao !== arquivo.impressao,
      voltou: musica.sumida,
    });
  }

  // 2. Arquivos em caminho novo: procura uma música sem arquivo com a mesma
  //    impressão. Candidatas são as que não apareceram nas raízes lidas agora
  //    e as que já estavam sumidas (de qualquer raiz).
  const candidatas = new Map<string, MusicaConhecida[]>();
  for (const m of conhecidas) {
    if (usadas.has(m.id)) continue;
    if (!raizesEscaneadas.has(m.raizId) && !m.sumida) continue;
    const lista = candidatas.get(m.impressao) ?? [];
    lista.push(m);
    candidatas.set(m.impressao, lista);
  }
  const arquivosPorImpressao = new Map<string, number>();
  for (const a of semDono) {
    arquivosPorImpressao.set(a.impressao, (arquivosPorImpressao.get(a.impressao) ?? 0) + 1);
  }

  for (const arquivo of semDono) {
    const lista = candidatas.get(arquivo.impressao);
    // Só religa sozinho quando não há dúvida: uma candidata e um arquivo.
    // Com cópias do mesmo arquivo, qualquer escolha poderia trocar músicas de
    // lugar nas playlists; aí entra como nova e a antiga fica para religar à
    // mão no web.
    if (lista?.length === 1 && arquivosPorImpressao.get(arquivo.impressao) === 1) {
      const musica = lista[0]!;
      usadas.add(musica.id);
      plano.movidas.push({ id: musica.id, arquivo, voltou: musica.sumida });
    } else {
      plano.novas.push(arquivo);
    }
  }

  // 3. O que estava nas raízes lidas e não apareceu.
  for (const m of conhecidas) {
    if (!usadas.has(m.id) && !m.sumida && raizesEscaneadas.has(m.raizId)) plano.sumidas.push(m.id);
  }

  return plano;
}
