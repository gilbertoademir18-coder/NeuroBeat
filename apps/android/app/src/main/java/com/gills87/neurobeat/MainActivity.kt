package com.gills87.neurobeat

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

@Composable
private fun App(controller: MediaController?) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it[Manifest.permission.READ_MEDIA_AUDIO] == true
    }
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(granted) {
        if (granted) tracks = withContext(Dispatchers.IO) { Library.load(context) }
    }
    var root by remember { mutableStateOf(Library.ROOTS.first()) }

    fun play(list: List<Track>) {
        val c = controller ?: return
        c.setMediaItems(list.map { it.toMediaItem() }, 0, 0L)
        c.repeatMode = Player.REPEAT_MODE_ALL
        c.prepare()
        c.play()
    }

    Scaffold(bottomBar = { NowPlaying(controller) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!granted) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        askPermissions.launch(
                            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS),
                        )
                    }) { Text("Permitir acesso às músicas") }
                }
                return@Column
            }
            val all = tracks
            if (all == null) {
                Text("Lendo a biblioteca…", Modifier.padding(16.dp))
                return@Column
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Library.ROOTS.forEach { r ->
                    FilterChip(
                        selected = r == root,
                        onClick = { root = r },
                        label = { Text("${r.removePrefix("#MediaFlow - ")} (${all.count { it.root == r }})") },
                    )
                }
            }
            val inRoot = all.filter { it.root == root }
            val folders = inRoot.groupBy { it.folder }.toList()
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    TextButton(onClick = { play(inRoot) }, Modifier.padding(horizontal = 8.dp)) {
                        Text("▶  Tocar tudo em loop (${inRoot.size})")
                    }
                }
                items(folders, key = { it.first }) { (folder, list) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { play(list) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(folder.ifEmpty { "(soltas na raiz)" }, style = MaterialTheme.typography.titleMedium)
                        Text("${list.size} faixas", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
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
