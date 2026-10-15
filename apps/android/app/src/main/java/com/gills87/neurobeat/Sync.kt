package com.gills87.neurobeat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/*
 * O pacote de `GET /api/sync`. Espelha o `SyncDto` de
 * `packages/shared/src/index.ts`: mudou lá, muda aqui.
 */

data class MusicaSync(
    val id: Int,
    val impressao: String,
    val titulo: String,
    val artista: String?,
    val duracaoMs: Long?,
    val caminho: String,
)

data class PlaylistSync(val id: Int, val nome: String, val loop: Boolean, val musicaIds: List<Int>)

data class Pacote(
    val geradoEm: String,
    val musicas: Map<Int, MusicaSync>,
    val playlists: List<PlaylistSync>,
    /** Tocadas já registradas no servidor, por impressão. */
    val tocadas: Map<String, Int>,
)

object Sync {
    private const val VERSAO = 1

    /** O `overdrive`, ligado o tempo todo. O único servidor. */
    private const val SERVIDOR = "https://overdrive.tail2c882a.ts.net:8447"

    private fun arquivo(context: Context) = File(context.filesDir, "sync.json")

    /** A última cópia baixada, para tocar sem rede. */
    fun ultimaCopia(context: Context): Pacote? = runCatching { ler(arquivo(context).readText()) }.getOrNull()

    /**
     * Baixa o pacote e, se ele for válido, troca a cópia guardada. Lança
     * exceção com mensagem legível se não conseguir — quem chama mostra.
     */
    fun baixar(context: Context): Pacote {
        val url = URL("$SERVIDOR/api/sync")
        val conexao = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 20_000
        }
        try {
            val status = conexao.responseCode
            if (status != 200) error("O servidor respondeu $status.")
            val texto = conexao.inputStream.bufferedReader().use { it.readText() }
            val pacote = ler(texto)
            // Só grava depois de ler: um pacote quebrado não apaga o bom.
            val temp = File(arquivo(context).path + ".tmp")
            temp.writeText(texto)
            temp.renameTo(arquivo(context))
            return pacote
        } finally {
            conexao.disconnect()
        }
    }

    private fun ler(texto: String): Pacote {
        val json = JSONObject(texto)
        val versao = json.getInt("versao")
        if (versao != VERSAO) error("Pacote na versão $versao; este app entende a $VERSAO. Atualize o app.")
        val musicas = json.getJSONArray("musicas").let { a ->
            (0 until a.length()).map { i ->
                val m = a.getJSONObject(i)
                MusicaSync(
                    id = m.getInt("id"),
                    impressao = m.getString("impressao"),
                    titulo = m.getString("titulo"),
                    artista = m.optString("artista").takeIf { !m.isNull("artista") },
                    duracaoMs = if (m.isNull("duracaoMs")) null else m.getLong("duracaoMs"),
                    caminho = m.getString("caminho"),
                )
            }
        }
        val playlists = json.getJSONArray("playlists").let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                val ids = p.getJSONArray("musicaIds")
                PlaylistSync(p.getInt("id"), p.getString("nome"), p.getBoolean("loop"), (0 until ids.length()).map(ids::getInt))
            }
        }
        // Campo acrescentado depois: uma cópia antiga guardada no aparelho não tem.
        val tocadas = json.optJSONObject("tocadas")?.let { o -> o.keys().asSequence().associateWith { o.getInt(it) } }.orEmpty()
        return Pacote(json.getString("geradoEm"), musicas.associateBy { it.id }, playlists, tocadas)
    }

    /**
     * Manda as tocadas pendentes e só as tira da fila depois que o servidor
     * confirma. Sem rede, elas esperam o próximo envio. Lança exceção em falha.
     */
    fun enviarTocadas(context: Context) {
        val pendentes = Tocadas.pendentes(context)
        if (pendentes.isEmpty()) return
        val corpo = JSONObject().put(
            "tocadas",
            JSONArray().apply {
                pendentes.forEach { put(JSONObject().put("id", it.id).put("impressao", it.impressao).put("tocadaEm", it.tocadaEm)) }
            },
        ).toString()
        val conexao = (URL("$SERVIDOR/api/tocadas").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 20_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conexao.outputStream.use { it.write(corpo.toByteArray()) }
            val status = conexao.responseCode
            if (status != 200) error("O servidor respondeu $status ao receber as tocadas.")
            Tocadas.confirmar(context, pendentes.map { it.id }.toSet())
        } finally {
            conexao.disconnect()
        }
    }
}
