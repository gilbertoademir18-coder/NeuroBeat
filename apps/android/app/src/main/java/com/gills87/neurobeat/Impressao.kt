package com.gills87.neurobeat

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * A impressão digital de um arquivo de áudio — a mesma que a API calcula no
 * PC. A especificação mora em `packages/shared/src/index.ts`:
 *
 *   amostra   = 64 KiB do MEIO do arquivo (ou o arquivo todo, se for menor)
 *   impressão = SHA-256("<tamanho>:" + amostra), em hex minúsculo
 *
 * Mudou lá, muda aqui — senão nenhuma música do celular casa com as do PC.
 */
object Impressao {
    private const val AMOSTRA = 64 * 1024

    /** Onde começa e quantos bytes tem a amostra. Igual ao `trechoDaAmostra` do shared. */
    fun trecho(tamanho: Long): Pair<Long, Int> {
        val comprimento = minOf(AMOSTRA.toLong(), tamanho).toInt()
        val inicio = minOf(maxOf(0L, tamanho / 2 - AMOSTRA / 2), tamanho - comprimento)
        return inicio to comprimento
    }

    /** O tamanho real do arquivo e a impressão, ou null se não deu para ler. */
    fun calcular(context: Context, uri: Uri): Pair<Long, String>? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        val tamanho = pfd.statSize
        // O AutoCloseInputStream fecha o descritor junto com ele.
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { entrada ->
            if (tamanho < 0) return null
            val (inicio, comprimento) = trecho(tamanho)
            val amostra = ByteBuffer.allocate(comprimento)
            val canal = entrada.channel
            while (amostra.hasRemaining()) {
                if (canal.read(amostra, inicio + amostra.position()) <= 0) break
            }
            val md = MessageDigest.getInstance("SHA-256")
            md.update("$tamanho:".toByteArray(Charsets.UTF_8))
            md.update(amostra.array(), 0, amostra.position())
            return tamanho to md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
