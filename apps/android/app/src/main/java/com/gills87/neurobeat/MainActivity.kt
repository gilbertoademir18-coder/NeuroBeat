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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
private fun item(uri: Uri, id: String, titulo: String, artista: String?, impressao: String): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    // A URI não atravessa do controller para a sessão; o PlaybackService a
    // repõe a partir daqui.
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(titulo)
            .setArtist(artista)
            // O serviço conta as tocadas por ela.
            .setExtras(Bundle().apply { putString(PlaybackService.IMPRESSAO, impressao) })
            .build(),
    )
    .build()

/** Chave do "de onde veio" nos extras da faixa: "Playlist: CTRL", "Pasta: Naruto", "Música". */
private const val ORIGEM = "origem"

/**
 * Troca a fila e toca. Não mexe no loop: ele é global (o botão do player,
 * guardado pelo PlaybackService) e continua como estava, venha a faixa de uma
 * playlist, de uma pasta ou da busca.
 */
private fun tocar(controller: MediaController?, itens: List<MediaItem>, origem: String) {
    val c = controller ?: return
    if (itens.isEmpty()) return
    // A origem vai na própria faixa, que mora no serviço: reabrir o app com a
    // música tocando continua mostrando de onde ela veio.
    val marcados = itens.map {
        it.buildUpon()
            // Acrescenta aos extras que já vêm (a impressão), sem trocá-los.
            .setMediaMetadata(
                it.mediaMetadata.buildUpon()
                    .setExtras(Bundle(it.mediaMetadata.extras ?: Bundle()).apply { putString(ORIGEM, origem) })
                    .build(),
            )
            .build()
    }
    c.setMediaItems(marcados, 0, 0L)
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
            val resultado = withContext(Dispatchers.IO) {
                // Manda as tocadas antes de baixar, para o pacote já vir com elas.
                // Se falhar, elas ficam na fila e o download segue.
                runCatching { Sync.enviarTocadas(context) }
                runCatching { Sync.baixar(context) }
            }
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

    val capa = rememberCapa(controller)
    Fundo(capa)
    // Tudo por cima do fundo é transparente, para a capa aparecer atrás.
    Scaffold(containerColor = Color.Transparent, bottomBar = { NowPlaying(controller, capa) }) { padding ->
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
                    label = { Text("Misturar áudio") },
                )
            }

            // A página da playlist/pasta fica por cima das abas, que continuam
            // compostas por baixo: ao voltar, a busca e a rolagem estão onde estavam.
            // Como a página é transparente (para a capa aparecer), as abas ficam
            // invisíveis enquanto ela está aberta.
            // As abas ficam sempre à mostra, também com a página aberta; tocar
            // numa aba (mesmo a atual) fecha a página e volta para a lista dela.
            TabRow(selectedTabIndex = aba, containerColor = Color.Transparent) {
                listOf("Playlists", "Pastas", "Busca", "Top").forEachIndexed { i, nome ->
                    Tab(selected = aba == i, onClick = { aba = i; aberta = null }, text = { Text(nome) })
                }
            }
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().alpha(if (aberta == null) 1f else 0f)) {
                    val lista = arquivos
                    if (lista == null) {
                        Text(progresso.ifEmpty { "Lendo as músicas do aparelho…" }, Modifier.padding(16.dp))
                        return@Column
                    }
                    // Abrir a playlist/pasta já começa a tocar tudo.
                    val abrir = { l: Lista -> aberta = l; tocarTudo(controller, l) }
                    when (aba) {
                        0 -> Playlists(pacote, porImpressao, abrir)
                        1 -> Pastas(lista, abrir)
                        2 -> Busca(lista, pacote) { itens -> tocar(controller, itens, origem = "Música") }
                        else -> MaisTocadas(porImpressao, pacote) { itens, origem -> tocar(controller, itens, origem) }
                    }
                }
                aberta?.let { l ->
                    // O Surface, mesmo transparente, segura os toques: nada chega às abas por baixo.
                    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                        Conteudo(
                            l,
                            voltar = { aberta = null },
                            tocarTudo = { tocarTudo(controller, l) },
                            tocarUma = { tocar(controller, listOf(it), origem = "Música") },
                        )
                    }
                }
            }
        }
    }
}

private fun tocarTudo(controller: MediaController?, lista: Lista) =
    tocar(controller, lista.faixas.mapNotNull { it.midia }, origem = "${lista.tipo}: ${lista.titulo}")

/** Uma faixa da página de conteúdo. Sem `midia`, o arquivo não está no aparelho. */
private data class Faixa(val titulo: String, val detalhe: String?, val midia: MediaItem?)

/** O que a página mostra: uma playlist ou uma pasta, já resolvida em faixas. */
private data class Lista(val tipo: String, val titulo: String, val faixas: List<Faixa>)

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
                Faixa(m.titulo, m.artista, arquivo?.let { item(it.uri, "${p.id}:$i", m.titulo, m.artista, it.impressao) })
            }
            val faltam = p.musicaIds.size - faixas.count { it.midia != null }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { abrir(Lista("Playlist", p.nome, faixas)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(p.nome, style = MaterialTheme.typography.titleMedium)
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
                        val faixas = lista.map { Faixa(it.nome, null, item(it.uri, "a${it.id}", it.nome, pasta.ifEmpty { null }, it.impressao)) }
                        abrir(Lista("Pasta", pasta.ifEmpty { "(soltas na raiz)" }, faixas))
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

/**
 * As mais tocadas: o total do servidor (no pacote do sync) mais o que ainda
 * está na fila do aparelho (ver `Tocadas`). Recalcula cada vez que a aba abre
 * ou chega um pacote novo. Música que não está no aparelho não aparece, mas a
 * conta dela continua no banco.
 */
@Composable
private fun MaisTocadas(porImpressao: Map<String, ArquivoLocal>, pacote: Pacote?, tocar: (List<MediaItem>, String) -> Unit) {
    val context = LocalContext.current
    var ranking by remember { mutableStateOf<List<Pair<ArquivoLocal, Int>>?>(null) }
    LaunchedEffect(porImpressao, pacote) {
        val totais = withContext(Dispatchers.IO) { Tocadas.totais(context, pacote?.tocadas.orEmpty()) }
        ranking = totais.mapNotNull { (impressao, vezes) -> porImpressao[impressao]?.let { it to vezes } }
            .sortedWith(compareByDescending<Pair<ArquivoLocal, Int>> { it.second }.thenBy { it.first.nome.lowercase() })
            .take(100)
    }
    val lista = ranking ?: return
    if (lista.isEmpty()) {
        Text("Nenhuma música contada ainda. Conta depois de 30 segundos ouvindo.", Modifier.padding(16.dp))
        return
    }
    fun midia(a: ArquivoLocal) = item(a.uri, "t${a.id}", a.nome, a.pasta.removePrefix("#MediaFlow - "), a.impressao)
    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = { tocar(lista.map { midia(it.first) }, "Mais tocadas") },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("▶  Tocar as mais tocadas") }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(lista, key = { _, (a, _) -> a.id }) { i, (a, vezes) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { tocar(listOf(midia(a)), "Música") }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}",
                        Modifier.padding(end = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(a.nome, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            a.pasta.removePrefix("#MediaFlow - "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        if (vezes == 1) "1 vez" else "$vezes vezes",
                        Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                HorizontalDivider()
            }
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

/** Há quanto tempo: "agora", "há 12 min", "há 3 h", "ontem", "há 5 dias", "02/09". */
private fun haQuanto(quando: Instant, agora: Instant = Instant.now()): String {
    val minutos = Duration.between(quando, agora).toMinutes()
    val dias = ChronoUnit.DAYS.between(
        quando.atZone(ZoneId.systemDefault()).toLocalDate(),
        agora.atZone(ZoneId.systemDefault()).toLocalDate(),
    )
    return when {
        minutos < 1 -> "agora"
        minutos < 60 -> "há $minutos min"
        dias == 0L -> "há ${minutos / 60} h"
        dias == 1L -> "ontem"
        dias < 7 -> "há $dias dias"
        else -> quando.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM"))
    }
}

/**
 * Todas as músicas do aparelho, as tocadas por último primeiro e o resto em
 * ordem alfabética. Sem nada digitado, mostra a lista inteira (~950 linhas: a
 * LazyColumn só desenha o que está na tela); digitando, filtra e mantém a
 * mesma ordem, então o que se ouviu por último aparece no topo.
 *
 * A ordem é calculada ao entrar na aba e a cada sync, e não a cada tocada:
 * reordenar a lista com o dedo em cima dela seria pior que esperar.
 */
@Composable
private fun Busca(arquivos: List<ArquivoLocal>, pacote: Pacote?, tocar: (List<MediaItem>) -> Unit) {
    val context = LocalContext.current
    var consulta by remember { mutableStateOf("") }
    var ultimas by remember { mutableStateOf<Map<String, Instant>>(emptyMap()) }
    LaunchedEffect(pacote) {
        ultimas = withContext(Dispatchers.IO) { Tocadas.ultimas(context, pacote?.ultimasTocadas.orEmpty()) }
    }
    val ordenados = remember(arquivos, ultimas) {
        arquivos.sortedWith(
            compareByDescending<ArquivoLocal> { ultimas[it.impressao] }
                .thenBy { it.pasta.lowercase() }
                .thenBy { it.nome.lowercase() },
        )
    }
    // Procura no nome e na pasta: "naruto" acha as faixas da pasta Naruto.
    val chaves = remember(ordenados) { ordenados.map { semAcento("${it.pasta}/${it.nome}") } }
    val termos = remember(consulta) { termosDe(consulta) }
    val achados = remember(ordenados, termos) {
        if (termos.isEmpty()) ordenados
        else ordenados.filterIndexed { i, _ -> termos.all { chaves[i].contains(it) } }
    }
    CampoBusca(consulta, { consulta = it }, "Nome da música ou da pasta")
    Text(
        if (termos.isEmpty()) "${achados.size} músicas · as tocadas por último primeiro" else "${achados.size} encontradas",
        Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall,
    )
    val agora = remember(ultimas) { Instant.now() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(achados, key = { it.id }) { a ->
            val pasta = a.pasta.removePrefix("#MediaFlow - ")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { tocar(listOf(item(a.uri, "b${a.id}", a.nome, pasta, a.impressao))) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.nome, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(pasta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ultimas[a.impressao]?.let { quando ->
                    Text(
                        haQuanto(quando, agora),
                        Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}


/** 83_000 → "1:23"; com hora, "1:02:03". */
private fun tempo(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun NowPlaying(controller: MediaController?, capa: ImageBitmap?) {
    var title by remember { mutableStateOf("") }
    var origem by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(false) }
    DisposableEffect(controller) {
        val c = controller
        val sync = {
            title = c?.mediaMetadata?.title?.toString().orEmpty()
            origem = c?.currentMediaItem?.mediaMetadata?.extras?.getString(ORIGEM).orEmpty()
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
    // A posição não gera evento do player enquanto anda: é lida a cada meio
    // segundo, e só enquanto toca (parado, basta ler uma vez).
    var posicao by remember { mutableStateOf(0L) }
    var duracao by remember { mutableStateOf(0L) }
    LaunchedEffect(controller, playing, title) {
        val c = controller ?: return@LaunchedEffect
        do {
            posicao = c.currentPosition
            duracao = c.duration.takeIf { it > 0 } ?: 0L
            delay(500)
        } while (playing)
    }
    // Enquanto o dedo arrasta, a barra segue o dedo; o salto só acontece ao soltar.
    var arrastando by remember { mutableStateOf<Float?>(null) }
    if (controller == null || title.isEmpty()) return
    // O fundo vai até a borda (por trás da barra de navegação), mas os botões
    // ficam acima dela: a bottomBar do Scaffold não desconta os insets sozinha.
    // Translúcida: a capa borrada do fundo aparece através da barra.
    Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (capa != null) {
                    Image(
                        capa,
                        null,
                        Modifier.padding(end = 12.dp).size(48.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(Modifier.weight(1f)) {
                    if (origem.isNotEmpty()) {
                        Text(
                            origem,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                }
            }
            if (duracao > 0) {
                val fracao = arrastando ?: (posicao.toFloat() / duracao).coerceIn(0f, 1f)
                Slider(
                    value = fracao,
                    onValueChange = { arrastando = it },
                    onValueChangeFinished = {
                        arrastando?.let { f ->
                            posicao = (f * duracao).toLong()
                            controller.seekTo(posicao)
                        }
                        arrastando = null
                    },
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        tempo(arrastando?.let { (it * duracao).toLong() } ?: posicao),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(tempo(duracao), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
