package com.gills87.neurobeat

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.text.Normalizer

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val root: String,
    /** Primeira subpasta dentro da raiz, ou "" se o arquivo está direto na raiz. */
    val folder: String,
    val durationMs: Long,
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(folder.ifEmpty { root })
                .setAlbumTitle(root)
                .build(),
        )
        .build()
}

/**
 * Lê as músicas pelo MediaStore, que já indexa as pastas do Autosync.
 * Teste mínimo: as raízes estão fixas aqui. No app de verdade, o mapeamento
 * alias → pasta vem dos ajustes.
 */
object Library {
    private const val AUTOSYNC = "Google Drive Autosync/"
    val ROOTS = listOf("#MediaFlow - Músicas", "#MediaFlow - Audios")

    fun load(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
        )
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("$AUTOSYNC%"),
            null,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                // NFC dos dois lados: o "ú" de "Músicas" pode vir decomposto do disco.
                val segments = nfc(c.getString(pathCol)).removePrefix(AUTOSYNC).trimEnd('/').split('/')
                val root = segments.first()
                if (root !in ROOTS) continue
                val id = c.getLong(idCol)
                tracks += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(nameCol).substringBeforeLast('.'),
                    root = root,
                    folder = segments.getOrElse(1) { "" },
                    durationMs = c.getLong(durCol),
                )
            }
        }
        return tracks.sortedWith(compareBy({ it.root }, { it.folder.lowercase() }, { it.title.lowercase() }))
    }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
}
