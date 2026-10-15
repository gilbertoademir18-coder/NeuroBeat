package com.gills87.neurobeat

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { App(controller) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            // Se o onStop vier antes da conexão terminar (tela apagando), o
            // future é cancelado e o get() lançaria CancellationException.
            future.addListener({
                if (!future.isCancelled) controller = future.get()
            }, ContextCompat.getMainExecutor(this))
        }
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }
}

/** Uma faixa pronta para o player: o arquivo local e o que mostrar dele. */
private fun item(uri: Uri, id: String, titulo: String, artista: String?): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    // A URI não atravessa do controller para a sessão; o PlaybackService a
    // repõe a partir daqui.
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(MediaMetadata.Builder().setTitle(titulo).setArtist(artista).build())
    .build()

private fun tocar(controller: MediaController?, itens: List<MediaItem>, loop: Boolean) {
    val c = controller ?: return
    if (itens.isEmpty()) return
    c.setMediaItems(itens, 0, 0L)
    c.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    c.prepare()
    c.play()
}

private fun quando(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"))
}.getOrDefault(iso)

private fun temPermissao(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
private fun App(controller: MediaController?) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()

    var permitido by remember { mutableStateOf(temPermissao(context)) }
    val pedirPermissoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitido = it[Manifest.permission.READ_MEDIA_AUDIO] == true
    }

    // Arquivos do aparelho, com impressão digital.
    var arquivos by remember { mutableStateOf<List<ArquivoLocal>?>(null) }
    var progresso by remember { mutableStateOf("") }
    LaunchedEffect(permitido) {
        if (!permitido) return@LaunchedEffect
        arquivos = withContext(Dispatchers.IO) {
            Indice.carregar(context) { feitos, total -> progresso = "Lendo as músicas do aparelho… $feitos de $total" }
        }
    }
    val porImpressao = remember(arquivos) { arquivos.orEmpty().associateBy { it.impressao } }

    // O pacote do servidor: a última cópia na hora, e a nova quando chegar.
    var pacote by remember { mutableStateOf<Pacote?>(null) }
    var statusSync by remember { mutableStateOf("") }
    var sincronizando by remember { mutableStateOf(false) }
    fun sincronizar() {
        if (sincronizando) return
        sincronizando = true
        statusSync = "Sincronizando…"
        escopo.launch {
            val resultado = withContext(Dispatchers.IO) { runCatching { Sync.baixar(context) } }
            sincronizando = false
            resultado.onSuccess {
                pacote = it
                statusSync = "Sincronizado ${quando(it.geradoEm)}"
            }.onFailure { erro ->
                val copia = pacote?.let { " Usando a cópia de ${quando(it.geradoEm)}." } ?: ""
                statusSync = "Sem conexão com o servidor.$copia (${erro.message ?: erro.javaClass.simpleName})"
            }
        }
    }
    LaunchedEffect(Unit) {
        pacote = withContext(Dispatchers.IO) { Sync.ultimaCopia(context) }
        pacote?.let { statusSync = "Cópia de ${quando(it.geradoEm)}" }
        sincronizar()
    }

    var aba by remember { mutableStateOf(0) }
    var ajustes by remember { mutableStateOf(false) }
    if (ajustes) Ajustes(onFechar = { mudou -> ajustes = false; if (mudou) sincronizar() })

    Scaffold(bottomBar = { NowPlaying(controller) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!permitido) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        pedirPermissoes.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
                    }) { Text("Permitir acesso às músicas") }
                }
                return@Column
            }

            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusSync,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { sincronizar() }, enabled = !sincronizando) { Text("Sincronizar") }
                TextButton(onClick = { ajustes = true }) { Text("⚙") }
            }

            TabRow(selectedTabIndex = aba) {
                Tab(selected = aba == 0, onClick = { aba = 0 }, text = { Text("Playlists") })
                Tab(selected = aba == 1, onClick = { aba = 1 }, text = { Text("Pastas") })
            }

            val lista = arquivos
            if (lista == null) {
                Text(progresso.ifEmpty { "Lendo as músicas do aparelho…" }, Modifier.padding(16.dp))
                return@Column
            }
            when (aba) {
                0 -> Playlists(pacote, porImpressao) { itens, loop -> tocar(controller, itens, loop) }
                else -> Pastas(lista) { itens -> tocar(controller, itens, loop = true) }
            }
        }
    }
}

@Composable
private fun Playlists(pacote: Pacote?, porImpressao: Map<String, ArquivoLocal>, tocar: (List<MediaItem>, Boolean) -> Unit) {
    if (pacote == null) {
        Text("Ainda sem playlists. Sincronize com o servidor (o Tailscale precisa estar ligado).", Modifier.padding(16.dp))
        return
    }
    if (pacote.playlists.isEmpty()) {
        Text("Nenhuma playlist no servidor. Crie uma no web.", Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(pacote.playlists, key = { it.id }) { p ->
            // Só o que tem arquivo no aparelho entra na fila; o resto é pulado.
            val itens = p.musicaIds.mapIndexedNotNull { i, id ->
                val m = pacote.musicas[id] ?: return@mapIndexedNotNull null
                val arquivo = porImpressao[m.impressao] ?: return@mapIndexedNotNull null
                item(arquivo.uri, "${p.id}:$i", m.titulo, m.artista)
            }
            val faltam = p.musicaIds.size - itens.size
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = itens.isNotEmpty()) { tocar(itens, p.loop) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(p.nome + if (p.loop) "  🔁" else "", style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append("${p.musicaIds.size} faixas")
                        if (faltam > 0) append(" · $faltam não estão no aparelho")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Pastas(arquivos: List<ArquivoLocal>, tocar: (List<MediaItem>) -> Unit) {
    val raizes = remember(arquivos) { arquivos.map { it.pasta.substringBefore('/') }.distinct().sorted() }
    var raiz by remember(raizes) { mutableStateOf(raizes.firstOrNull { it.contains("Músicas") } ?: raizes.firstOrNull()) }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        raizes.forEach { r ->
            FilterChip(selected = r == raiz, onClick = { raiz = r }, label = { Text(r.removePrefix("#MediaFlow - ")) })
        }
    }
    val naRaiz = arquivos.filter { it.pasta.substringBefore('/') == raiz }
    val pastas = naRaiz.groupBy { it.pasta.substringAfter('/', "") }.toList()
    LazyColumn(Modifier.fillMaxSize()) {
        items(pastas, key = { it.first }) { (pasta, lista) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { tocar(lista.map { item(it.uri, "a${it.id}", it.nome, pasta.ifEmpty { null }) }) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(pasta.ifEmpty { "(soltas na raiz)" }, style = MaterialTheme.typography.titleMedium)
                Text("${lista.size} faixas", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Ajustes(onFechar: (mudou: Boolean) -> Unit) {
    val context = LocalContext.current
    val atual = remember { Sync.servidor(context) }
    var url by remember { mutableStateOf(atual) }
    AlertDialog(
        onDismissRequest = { onFechar(false) },
        title = { Text("Servidor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, label = { Text("Endereço no tailnet") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = url == Sync.NOTEBOOK, onClick = { url = Sync.NOTEBOOK }, label = { Text("Notebook") })
                    FilterChip(selected = url == Sync.PC_DE_CASA, onClick = { url = Sync.PC_DE_CASA }, label = { Text("PC de casa") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Sync.definirServidor(context, url)
                onFechar(url.trim().trimEnd('/') != atual)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = { onFechar(false) }) { Text("Cancelar") } },
    )
}

@Composable
private fun NowPlaying(controller: MediaController?) {
    var title by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(false) }
    DisposableEffect(controller) {
        val c = controller
        val sync = {
            title = c?.mediaMetadata?.title?.toString().orEmpty()
            playing = c?.isPlaying == true
            loop = c?.repeatMode == Player.REPEAT_MODE_ALL
        }
        sync()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        c?.addListener(listener)
        onDispose { c?.removeListener(listener) }
    }
    if (controller == null || title.isEmpty()) return
    // O fundo vai até a borda (por trás da barra de navegação), mas os botões
    // ficam acima dela: a bottomBar do Scaffold não desconta os insets sozinha.
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { controller.seekToPrevious() }) { Text("⏮") }
                TextButton(onClick = { if (playing) controller.pause() else controller.play() }) {
                    Text(if (playing) "⏸" else "▶")
                }
                TextButton(onClick = { controller.seekToNext() }) { Text("⏭") }
                TextButton(onClick = {
                    controller.repeatMode = if (loop) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
                }) { Text(if (loop) "🔁 loop ligado" else "loop desligado") }
            }
        }
    }
}
