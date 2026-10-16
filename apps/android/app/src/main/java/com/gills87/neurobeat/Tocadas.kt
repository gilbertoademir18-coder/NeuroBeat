package com.gills87.neurobeat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * As tocadas que ainda não chegaram ao servidor.
 *
 * Quem conta é o `PlaybackService` (depois de 30 s ouvindo, ou metade da faixa
 * se for curta). Cada tocada vira um evento com id próprio numa fila
 * (`tocadas-pendentes.json`), que o `Sync.enviarTocadas` manda para o banco e
 * esvazia só depois da confirmação. O total de cada música é o do servidor
 * (no pacote do sync) mais o que ainda está aqui na fila.
 */
object Tocadas {
    data class Pendente(val id: String, val impressao: String, val tocadaEm: String)

    private fun arquivo(context: Context) = File(context.filesDir, "tocadas-pendentes.json")

    @Synchronized
    fun pendentes(context: Context): List<Pendente> {
        migrarContagemAntiga(context)
        return runCatching {
            val a = JSONArray(arquivo(context).readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Pendente(o.getString("id"), o.getString("impressao"), o.getString("tocadaEm"))
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun registrar(context: Context, impressao: String) =
        gravar(context, pendentes(context) + Pendente(UUID.randomUUID().toString(), impressao, Instant.now().toString()))

    /** Tira da fila as que o servidor confirmou. As que chegaram depois do envio ficam. */
    @Synchronized
    fun confirmar(context: Context, ids: Set<String>) = gravar(context, pendentes(context).filterNot { it.id in ids })

    /** Total por impressão: o do servidor somado ao que ainda não foi enviado. */
    fun totais(context: Context, doServidor: Map<String, Int>): Map<String, Int> {
        val total = doServidor.toMutableMap()
        pendentes(context).forEach { total[it.impressao] = (total[it.impressao] ?: 0) + 1 }
        return total
    }

    /**
     * A última vez que cada música tocou: a do servidor, ou a da fila local se
     * for mais recente (o que se ouviu sem rede também conta).
     */
    fun ultimas(context: Context, doServidor: Map<String, String>): Map<String, Instant> {
        val ultima = doServidor.mapNotNull { (impressao, iso) ->
            runCatching { Instant.parse(iso) }.getOrNull()?.let { impressao to it }
        }.toMap().toMutableMap()
        pendentes(context).forEach { p ->
            val quando = runCatching { Instant.parse(p.tocadaEm) }.getOrNull() ?: return@forEach
            if (ultima[p.impressao]?.isBefore(quando) != false) ultima[p.impressao] = quando
        }
        return ultima
    }

    private fun gravar(context: Context, lista: List<Pendente>) {
        val a = JSONArray()
        lista.forEach { a.put(JSONObject().put("id", it.id).put("impressao", it.impressao).put("tocadaEm", it.tocadaEm)) }
        // Grava num temporário e troca: um processo morto no meio não corrompe a fila.
        val destino = arquivo(context)
        val temp = File(destino.path + ".tmp")
        temp.writeText(a.toString())
        temp.renameTo(destino)
    }

    /**
     * A primeira versão guardava só o total por música (`tocadas.json`), sem
     * servidor. Cada tocada contada lá vira um evento na fila, com a data da
     * última vez, e o arquivo antigo sai.
     */
    private fun migrarContagemAntiga(context: Context) {
        val antigo = File(context.filesDir, "tocadas.json")
        if (!antigo.exists()) return
        runCatching {
            val json = JSONObject(antigo.readText())
            val atuais = runCatching { arquivo(context).readText() }.getOrNull()?.let { JSONArray(it) } ?: JSONArray()
            json.keys().forEach { impressao ->
                val o = json.getJSONObject(impressao)
                val quando = Instant.ofEpochMilli(o.getLong("u")).toString()
                repeat(o.getInt("n")) {
                    atuais.put(JSONObject().put("id", UUID.randomUUID().toString()).put("impressao", impressao).put("tocadaEm", quando))
                }
            }
            val temp = File(arquivo(context).path + ".tmp")
            temp.writeText(atuais.toString())
            temp.renameTo(arquivo(context))
        }
        antigo.delete()
    }
}
