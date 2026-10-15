package com.gills87.neurobeat

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * A capa embutida no arquivo (APIC no MP3, PICTURE no FLAC, covr no M4A).
 * Medido em 2026-10-15 no Drive: 761 de 809 em Músicas e 102 de 142 em Audios
 * têm capa, a maior com ~600 KB.
 */

/** A capa da faixa atual, ou null se não houver faixa ou o arquivo não tiver capa. */
@Composable
fun rememberCapa(controller: MediaController?): ImageBitmap? {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    DisposableEffect(controller) {
        val c = controller
        val sync = { uri = c?.currentMediaItem?.requestMetadata?.mediaUri }
        sync()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        c?.addListener(listener)
        onDispose { c?.removeListener(listener) }
    }
    var capa by remember { mutableStateOf<ImageBitmap?>(null) }
    // Chaveado pela URI: eventos do player (posição, pausa) não releem o arquivo.
    LaunchedEffect(uri) {
        capa = uri?.let { withContext(Dispatchers.IO) { lerCapa(context, it) } }
    }
    return capa
}

private fun lerCapa(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val bytes = MediaMetadataRetriever().use { r ->
        r.setDataSource(context, uri)
        r.embeddedPicture
    } ?: return null
    // Reduz na decodificação: o fundo é borrado, e uma capa de 3000 px em
    // tamanho cheio seria memória jogada fora.
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
    var amostra = 1
    while (limites.outWidth / (amostra * 2) >= 512 && limites.outHeight / (amostra * 2) >= 512) amostra *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = amostra })
        ?.asImageBitmap()
}.getOrNull()

/** A capa borrada atrás de tudo, com um véu da cor de fundo para o texto continuar legível. */
@Composable
fun Fundo(capa: ImageBitmap?) {
    Crossfade(capa, animationSpec = tween(600), label = "fundo") { atual ->
        if (atual != null) {
            Box(Modifier.fillMaxSize()) {
                Image(atual, null, Modifier.fillMaxSize().blur(48.dp), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)))
            }
        }
    }
}
