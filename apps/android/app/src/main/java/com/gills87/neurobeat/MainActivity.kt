package com.gills87.neurobeat

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import java.text.Normalizer
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
            NeuroBeatTema {
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
    val prefs = remember { PlaybackService.preferencias(context) }
    var tocarJunto by remember { mutableStateOf(PlaybackService.tocarJunto(prefs)) }
    var aberta by remember { mutableStateOf<Lista?>(null) }

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

            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusSync,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { sincronizar() }, enabled = !sincronizando) { Text("Sincronizar") }
                // O serviço escuta a preferência e troca o foco de áudio na hora.
                FilterChip(
                    selected = tocarJunto,
                    onClick = {
                        tocarJunto = !tocarJunto
                        prefs.edit().putBoolean(PlaybackService.TOCAR_JUNTO, tocarJunto).apply()
                    },
                    label = { Text("Tocar junto") },
                )
            }

            // A página da playlist/pasta fica por cima das abas, que continuam
            // compostas por baixo: ao voltar, a busca e a rolagem estão onde estavam.
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = aba) {
                        Tab(selected = aba == 0, onClick = { aba = 0 }, text = { Text("Playlists") })
                        Tab(selected = aba == 1, onClick = { aba = 1 }, text = { Text("Pastas") })
                        Tab(selected = aba == 2, onClick = { aba = 2 }, text = { Text("Busca") })
                    }

                    val lista = arquivos
                    if (lista == null) {
                        Text(progresso.ifEmpty { "Lendo as músicas do aparelho…" }, Modifier.padding(16.dp))
                        return@Column
                    }
                    when (aba) {
                        0 -> Playlists(pacote, porImpressao) { aberta = it }
                        1 -> Pastas(lista) { aberta = it }
                        else -> Busca(lista) { itens -> tocar(controller, itens, loop = false) }
                    }
                }
                aberta?.let { l ->
                    Surface(Modifier.fillMaxSize()) {
                        Conteudo(
                            l,
                            voltar = { aberta = null },
                            tocarTudo = { tocar(controller, l.faixas.mapNotNull { it.midia }, l.loop) },
                            tocarUma = { tocar(controller, listOf(it), loop = false) },
                        )
                    }
                }
            }
        }
    }
}

/** Uma faixa da página de conteúdo. Sem `midia`, o arquivo não está no aparelho. */
private data class Faixa(val titulo: String, val detalhe: String?, val midia: MediaItem?)

/** O que a página mostra: uma playlist ou uma pasta, já resolvida em faixas. */
private data class Lista(val titulo: String, val loop: Boolean, val faixas: List<Faixa>)

@Composable
private fun Conteudo(lista: Lista, voltar: () -> Unit, tocarTudo: () -> Unit, tocarUma: (MediaItem) -> Unit) {
    BackHandler(onBack = voltar)
    val noAparelho = lista.faixas.count { it.midia != null }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = voltar) { Text("←") }
            Column(Modifier.weight(1f)) {
                Text(lista.titulo, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append("${lista.faixas.size} faixas")
                        if (noAparelho < lista.faixas.size) append(" · ${lista.faixas.size - noAparelho} não estão no aparelho")
                        if (lista.loop) append(" · 🔁")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(
            onClick = tocarTudo,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = noAparelho > 0,
        ) { Text("▶  Tocar tudo") }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(lista.faixas) { i, f ->
                val midia = f.midia
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = midia != null) { midia?.let(tocarUma) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}",
                        Modifier.padding(end = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        val cor = if (midia != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        Text(f.titulo, style = MaterialTheme.typography.bodyLarge, color = cor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val linha2 = if (midia == null) "não está no aparelho" else f.detalhe
                        if (!linha2.isNullOrEmpty()) {
                            Text(linha2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Playlists(pacote: Pacote?, porImpressao: Map<String, ArquivoLocal>, abrir: (Lista) -> Unit) {
    if (pacote == null) {
        Text("Ainda sem playlists. Sincronize com o servidor (o Tailscale precisa estar ligado).", Modifier.padding(16.dp))
        return
    }
    if (pacote.playlists.isEmpty()) {
        Text("Nenhuma playlist no servidor. Crie uma no web.", Modifier.padding(16.dp))
        return
    }
    var consulta by remember { mutableStateOf("") }
    val termos = remember(consulta) { termosDe(consulta) }
    val visiveis = remember(pacote, termos) { pacote.playlists.filter { casa(it.nome, termos) } }
    CampoBusca(consulta, { consulta = it }, "Buscar playlist")
    if (visiveis.isEmpty()) {
        Text("Nenhuma playlist com esse nome.", Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(visiveis, key = { it.id }) { p ->
            // A faixa sem arquivo no aparelho aparece na página, mas fica fora da fila.
            val faixas = p.musicaIds.mapIndexedNotNull { i, id ->
                val m = pacote.musicas[id] ?: return@mapIndexedNotNull null
                val arquivo = porImpressao[m.impressao]
                Faixa(m.titulo, m.artista, arquivo?.let { item(it.uri, "${p.id}:$i", m.titulo, m.artista) })
            }
            val faltam = p.musicaIds.size - faixas.count { it.midia != null }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { abrir(Lista(p.nome, p.loop, faixas)) }
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
private fun Pastas(arquivos: List<ArquivoLocal>, abrir: (Lista) -> Unit) {
    val raizes = remember(arquivos) { arquivos.map { it.pasta.substringBefore('/') }.distinct().sorted() }
    var raiz by remember(raizes) { mutableStateOf(raizes.firstOrNull { it.contains("Músicas") } ?: raizes.firstOrNull()) }
    // Rola de lado: sem isso, o chip que não cabe é espremido até largura zero e
    // o texto quebra letra a letra, deixando a linha altíssima.
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        raizes.forEach { r ->
            FilterChip(selected = r == raiz, onClick = { raiz = r }, label = { Text(r.removePrefix("#MediaFlow - ")) })
        }
    }
    var consulta by remember { mutableStateOf("") }
    val termos = remember(consulta) { termosDe(consulta) }
    // Busca só dentro da raiz escolhida, pelo nome da pasta.
    val pastas = remember(arquivos, raiz, termos) {
        arquivos.filter { it.pasta.substringBefore('/') == raiz }
            .groupBy { it.pasta.substringAfter('/', "") }
            .toList()
            .filter { casa(it.first, termos) }
    }
    CampoBusca(consulta, { consulta = it }, "Buscar pasta")
    if (pastas.isEmpty() && termos.isNotEmpty()) {
        Text("Nenhuma pasta com esse nome aqui.", Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(pastas, key = { it.first }) { (pasta, lista) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val faixas = lista.map { Faixa(it.nome, null, item(it.uri, "a${it.id}", it.nome, pasta.ifEmpty { null })) }
                        // Pasta toca em loop, como antes de existir a página.
                        abrir(Lista(pasta.ifEmpty { "(soltas na raiz)" }, loop = true, faixas))
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(pasta.ifEmpty { "(soltas na raiz)" }, style = MaterialTheme.typography.titleMedium)
                Text("${lista.size} faixas", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

/** Minúsculas e sem acento, para "musica" achar "Música". */
private fun semAcento(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** As palavras da busca, já sem acento. */
private fun termosDe(consulta: String): List<String> = semAcento(consulta).split(' ').filter { it.isNotBlank() }

/** Todas as palavras precisam aparecer: "naruto blue" não acha só "Naruto". Sem palavras, tudo casa. */
private fun casa(texto: String, termos: List<String>): Boolean {
    if (termos.isEmpty()) return true
    val chave = semAcento(texto)
    return termos.all { chave.contains(it) }
}

@Composable
private fun CampoBusca(consulta: String, aoMudar: (String) -> Unit, rotulo: String) {
    OutlinedTextField(
        value = consulta,
        onValueChange = aoMudar,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        label = { Text(rotulo) },
    )
}

@Composable
private fun Busca(arquivos: List<ArquivoLocal>, tocar: (List<MediaItem>) -> Unit) {
    var consulta by remember { mutableStateOf("") }
    // Procura no nome e na pasta: "naruto" acha as faixas da pasta Naruto.
    val chaves = remember(arquivos) { arquivos.map { semAcento("${it.pasta}/${it.nome}") } }
    val termos = remember(consulta) { termosDe(consulta) }
    val achados = remember(arquivos, termos) {
        if (termos.isEmpty()) emptyList()
        else arquivos.filterIndexed { i, _ -> termos.all { chaves[i].contains(it) } }
    }
    CampoBusca(consulta, { consulta = it }, "Nome da música ou da pasta")
    if (termos.isEmpty()) return
    Text(
        "${achados.size} encontradas",
        Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall,
    )
    LazyColumn(Modifier.fillMaxSize()) {
        items(achados, key = { it.id }) { a ->
            val pasta = a.pasta.removePrefix("#MediaFlow - ")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { tocar(listOf(item(a.uri, "b${a.id}", a.nome, pasta))) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(a.nome, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pasta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider()
        }
    }
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
