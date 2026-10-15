package com.gills87.neurobeat

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/** Um arquivo de áudio do aparelho, já com a impressão digital. */
data class ArquivoLocal(
    val id: Long,
    val uri: Uri,
    /** Nome sem extensão. */
    val nome: String,
    /** Dentro do Autosync: "#MediaFlow - Músicas/3 Doors Down" (sem barra no fim). */
    val pasta: String,
    val duracaoMs: Long,
    val impressao: String,
)

/**
 * Lê os áudios do Autosync pelo MediaStore e calcula a impressão de cada um.
 *
 * Calcular é ler 64 KiB por arquivo; com ~950 faixas, uns segundos na primeira
 * vez. Por isso o resultado fica num cache (`impressoes.json`) indexado pelo
 * `_ID` do MediaStore, e só é recalculado quando o tamanho ou a data de
 * modificação mudam.
 */
object Indice {
    const val AUTOSYNC = "Google Drive Autosync/"
    private const val TAG = "NeuroBeat"

    private data class Memo(val modificado: Long, val tamanho: Long, val impressao: String)

    fun carregar(context: Context, progresso: (feitos: Int, total: Int) -> Unit): List<ArquivoLocal> {
        val colecao = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projecao = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        data class Linha(val id: Long, val pasta: String, val nome: String, val duracao: Long, val tamanho: Long, val modificado: Long)
        val linhas = mutableListOf<Linha>()
        context.contentResolver.query(
            colecao, projecao, "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("$AUTOSYNC%"), null,
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val caminho = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val nome = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val duracao = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val tamanho = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val modificado = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                linhas += Linha(
                    id = c.getLong(id),
                    // NFC: o "ú" de "Músicas" pode vir decomposto do disco.
                    pasta = Normalizer.normalize(c.getString(caminho), Normalizer.Form.NFC)
                        .removePrefix(AUTOSYNC).trimEnd('/'),
                    nome = c.getString(nome).substringBeforeLast('.'),
                    duracao = c.getLong(duracao),
                    tamanho = c.getLong(tamanho),
                    modificado = c.getLong(modificado),
                )
            }
        }

        val memos = lerMemos(context)
        val novosMemos = mutableMapOf<Long, Memo>()
        val arquivos = mutableListOf<ArquivoLocal>()
        linhas.forEachIndexed { i, l ->
            val uri = ContentUris.withAppendedId(colecao, l.id)
            val memo = memos[l.id]?.takeIf { it.modificado == l.modificado && it.tamanho == l.tamanho }
                ?: runCatching { Impressao.calcular(context, uri) }
                    .onFailure { Log.w(TAG, "Não consegui ler ${l.pasta}/${l.nome}", it) }
                    .getOrNull()
                    ?.let { (tamanho, impressao) -> Memo(l.modificado, tamanho, impressao) }
            if (memo != null) {
                novosMemos[l.id] = memo
                arquivos += ArquivoLocal(l.id, uri, l.nome, l.pasta, l.duracao, memo.impressao)
            }
            if (i % 25 == 0 || i == linhas.lastIndex) progresso(i + 1, linhas.size)
        }
        // Grava o cache inteiro de novo: o que saiu do aparelho sai dele também.
        gravarMemos(context, novosMemos)
        return arquivos.sortedWith(compareBy({ it.pasta.lowercase() }, { it.nome.lowercase() }))
    }

    private fun arquivoMemos(context: Context) = File(context.filesDir, "impressoes.json")

    private fun lerMemos(context: Context): Map<Long, Memo> = runCatching {
        val json = JSONObject(arquivoMemos(context).readText())
        json.keys().asSequence().associate { chave ->
            val o = json.getJSONObject(chave)
            chave.toLong() to Memo(o.getLong("m"), o.getLong("s"), o.getString("h"))
        }
    }.getOrDefault(emptyMap())

    private fun gravarMemos(context: Context, memos: Map<Long, Memo>) {
        val json = JSONObject()
        memos.forEach { (id, m) -> json.put(id.toString(), JSONObject().put("m", m.modificado).put("s", m.tamanho).put("h", m.impressao)) }
        val destino = arquivoMemos(context)
        val temp = File(destino.path + ".tmp")
        temp.writeText(json.toString())
        temp.renameTo(destino)
    }
}
