package com.gills87.neurobeat

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Quantas vezes cada música foi tocada, por impressão digital (a mesma
 * identidade das playlists: renomear ou mover o arquivo não zera a conta).
 *
 * Fica só no aparelho (`tocadas.json`): o celular não escreve no servidor.
 * Quem conta é o `PlaybackService`, depois de 30 s ouvindo (ou metade da
 * faixa, se ela for curta).
 */
object Tocadas {
    data class Registro(val vezes: Int, val ultimaEm: Long)

    private fun arquivo(context: Context) = File(context.filesDir, "tocadas.json")

    @Synchronized
    fun ler(context: Context): Map<String, Registro> = runCatching {
        val json = JSONObject(arquivo(context).readText())
        json.keys().asSequence().associateWith { chave ->
            val o = json.getJSONObject(chave)
            Registro(o.getInt("n"), o.getLong("u"))
        }
    }.getOrDefault(emptyMap())

    @Synchronized
    fun contar(context: Context, impressao: String) {
        val todos = ler(context).toMutableMap()
        todos[impressao] = Registro((todos[impressao]?.vezes ?: 0) + 1, System.currentTimeMillis())
        val json = JSONObject()
        todos.forEach { (chave, r) -> json.put(chave, JSONObject().put("n", r.vezes).put("u", r.ultimaEm)) }
        // Grava num temporário e troca: um processo morto no meio não corrompe a contagem.
        val destino = arquivo(context)
        val temp = File(destino.path + ".tmp")
        temp.writeText(json.toString())
        temp.renameTo(destino)
    }
}
