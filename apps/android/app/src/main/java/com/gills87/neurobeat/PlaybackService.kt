package com.gills87.neurobeat

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

/**
 * Serviço em primeiro plano que segura o player. O Media3 cuida da notificação
 * e dos controles da tela de bloqueio a partir da MediaSession.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    // Guardado num campo: o SharedPreferences só segura o listener por
    // referência fraca, e um lambda solto seria coletado.
    private val aoMudarPreferencia = SharedPreferences.OnSharedPreferenceChangeListener { prefs, chave ->
        if (chave == TOCAR_JUNTO) aplicarFoco(prefs)
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // Fone desconectado pausa, em vez de sair no alto-falante.
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        session = MediaSession.Builder(this, player).setCallback(SessionCallback).build()
        player.addListener(contador)
        val prefs = preferencias(this)
        // O loop é global: vale para qualquer fila (trocar de playlist, pasta
        // ou música não mexe nele) e sobrevive a fechar o app. O botão do
        // player muda o repeatMode, e o listener abaixo guarda.
        player.repeatMode = if (prefs.getBoolean(LOOP, false)) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                preferencias(this@PlaybackService).edit().putBoolean(LOOP, repeatMode != Player.REPEAT_MODE_OFF).apply()
            }
        })
        aplicarFoco(prefs)
        prefs.registerOnSharedPreferenceChangeListener(aoMudarPreferencia)
        usarIconeDaMarcaNaNotificacao()
    }

    // Contagem das mais tocadas: cada vez que uma faixa começa (inclusive a
    // volta do loop), ela conta uma vez, depois de 30 s ouvindo, ou metade da
    // faixa se for curta. Pular no começo não conta.
    private val relogio = Handler(Looper.getMainLooper())
    private val gravador = Executors.newSingleThreadExecutor()
    private var jaContou = false

    private val conferir = object : Runnable {
        override fun run() {
            val p = session?.player ?: return
            val impressao = p.currentMediaItem?.mediaMetadata?.extras?.getString(IMPRESSAO)
            if (!jaContou && impressao != null) {
                val duracao = p.duration
                val limite = if (duracao > 0) minOf(30_000L, duracao / 2) else 30_000L
                if (p.currentPosition >= limite) {
                    jaContou = true
                    gravador.execute {
                        Tocadas.registrar(this@PlaybackService, impressao)
                        // Sem rede, fica na fila para o próximo envio (este ou o do sync).
                        runCatching { Sync.enviarTocadas(this@PlaybackService) }
                    }
                }
            }
            if (!jaContou) relogio.postDelayed(this, 2_000)
        }
    }

    private val contador = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            jaContou = false
            relogio.removeCallbacks(conferir)
            if (session?.player?.isPlaying == true) relogio.post(conferir)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            relogio.removeCallbacks(conferir)
            if (isPlaying) relogio.post(conferir)
        }
    }

    /**
     * "Misturar áudio" ligado = não pedir o foco de áudio. Sem foco, o Android não
     * manda pausar quando outro app (o YouTube) começa a tocar, e o NeuroBeat
     * também não pausa os outros. O preço: uma ligação não pausa a música.
     */
    private fun aplicarFoco(prefs: SharedPreferences) {
        val atributos = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        session?.player?.let { (it as ExoPlayer).setAudioAttributes(atributos, /* handleAudioFocus = */ !tocarJunto(prefs)) }
    }

    // O Media3 põe um ícone genérico de nota musical na notificação; aqui vai
    // a linha de batimento do NeuroBeat.
    @OptIn(UnstableApi::class)
    private fun usarIconeDaMarcaNaNotificacao() {
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notificacao) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    // Tirar o app dos recentes não para a música; só encerra se nada estiver tocando.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        preferencias(this).unregisterOnSharedPreferenceChangeListener(aoMudarPreferencia)
        relogio.removeCallbacks(conferir)
        gravador.shutdown()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        /** Preferência compartilhada entre a tela (que muda) e o serviço (que aplica). */
        const val TOCAR_JUNTO = "tocarJunto"

        /** O loop global, ligado ou não. Só o serviço lê e grava. */
        const val LOOP = "loop"

        /** Chave da impressão digital nos extras da faixa, para contar as tocadas. */
        const val IMPRESSAO = "impressao"

        fun preferencias(context: Context): SharedPreferences =
            context.getSharedPreferences("neurobeat", Context.MODE_PRIVATE)

        fun tocarJunto(prefs: SharedPreferences) = prefs.getBoolean(TOCAR_JUNTO, true)
    }

    private object SessionCallback : MediaSession.Callback {
        // A URI não atravessa do controller para a sessão; ela vem no
        // requestMetadata e é reposta aqui.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { it.buildUpon().setUri(it.requestMetadata.mediaUri).build() }.toMutableList(),
        )
    }
}
