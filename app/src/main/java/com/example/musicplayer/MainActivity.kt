package com.example.musicplayer

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var holder: PlayerHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holder = PlayerHolder(applicationContext)
        holder.connect()
        setContent { AppTheme { Root(holder) } }
    }

    override fun onDestroy() {
        holder.release()
        super.onDestroy()
    }
}

sealed class Dlg {
    data class AddTo(val s: Song) : Dlg()
    data class Edit(val s: Song) : Dlg()
    data class DelPl(val name: String) : Dlg()
    object NewPl : Dlg()
}

@Composable
fun Root(h: PlayerHolder) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            permLauncher.launch(perm)
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---- библиотека
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var scanRev by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, scanRev) {
        if (granted) songs = withContext(Dispatchers.IO) { Library.scan(ctx) }
    }
    val byId = remember(songs) { songs.associateBy { it.id } }

    // позиция воспроизведения обновляется 2 раза в секунду
    LaunchedEffect(h.ready) {
        while (h.ready) {
            h.poll()
            delay(500)
        }
    }

    // ---- навигация
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Dlg?>(null) }

    BackHandler(enabled = showQueue || showPlayer || showSettings || detail != null || query.isNotEmpty()) {
        when {
            showQueue -> showQueue = false
            showPlayer -> showPlayer = false
            showSettings -> showSettings = false
            detail != null -> detail = null
            else -> query = ""
        }
    }

    val act = Actions(
        play = { l, i -> h.play(l, i) },
        shuffle = { l -> h.shuffleAll(l) },
        playNext = { s -> h.playNext(s) },
        enqueue = { s -> h.enqueue(s) },
        addToPl = { s -> dialog = Dlg.AddTo(s) },
        edit = { s -> dialog = Dlg.Edit(s) },
        open = { d -> detail = d }
    )

    val current = byId[h.currentId]
    val tabs = listOf(
        tr("Songs", "Canciones", "Песни") to Icons.Filled.MusicNote,
        tr("Albums", "Álbumes", "Альбомы") to Icons.Filled.Album,
        tr("Artists", "Artistas", "Артисты") to Icons.Filled.Person,
        tr("Playlists", "Listas", "Плейлисты") to Icons.AutoMirrored.Filled.QueueMusic,
        tr("Stats", "Estadísticas", "Статистика") to Icons.Filled.BarChart
    )

    Box(Modifier.fillMaxSize()) {
        // ---------------- главный экран
        Screen {
            Column(Modifier.fillMaxSize()) {
                TopSearch(query, { query = it }, { showSettings = true })
                Box(Modifier.weight(1f)) {
                    if (!granted) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                tr(
                                    "Allow access to your music files so the player can find your songs.",
                                    "Permite el acceso a tus archivos de música para que el reproductor encuentre tus canciones.",
                                    "Разреши доступ к музыке, чтобы плеер нашёл твои песни."
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { permLauncher.launch(perm) }) {
                                Text(tr("Allow", "Permitir", "Разрешить"))
                            }
                            TextButton(onClick = {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", ctx.packageName, null))
                                )
                            }) { Text(tr("Open app settings", "Abrir ajustes", "Открыть настройки приложения")) }
                        }
                    } else if (query.isNotBlank()) {
                        SearchResults(query, songs, byId, h, act)
                    } else {
                        when (tab) {
                            0 -> SongsTab(songs, h, act) { scanRev++ }
                            1 -> AlbumsTab(songs, act)
                            2 -> ArtistsTab(songs, act)
                            3 -> PlaylistsTab(
                                songs, byId, act,
                                onNew = { dialog = Dlg.NewPl },
                                onDelete = { dialog = Dlg.DelPl(it) }
                            )
                            else -> StatsTab(songs)
                        }
                    }
                }
                MiniPlayer(h, current) { showPlayer = true }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (Store.wallOn) 0.85f else 1f)
                ) {
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i; query = "" },
                            icon = { Icon(icon, null) },
                            label = { Text(label, maxLines = 1, fontSize = 11.sp, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }

        // ---------------- поверх главного экрана
        detail?.let { d ->
            DetailScreen(d, songs, byId, h, act) { detail = null }
        }
        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false }, onRescan = { scanRev++ })
        }
        if (showPlayer && current != null) {
            FullPlayer(h, current, act, onClose = { showPlayer = false }, onQueue = { showQueue = true })
        }
        if (showQueue) {
            QueueScreen(h, byId) { showQueue = false }
        }
    }

    // ---------------- диалоги
    when (val d = dialog) {
        is Dlg.AddTo -> AddToPlaylistDialog(d.s) { dialog = null }
        is Dlg.Edit -> EditDialog(d.s) { dialog = null }
        is Dlg.DelPl -> DeletePlaylistDialog(d.name) { dialog = null }
        Dlg.NewPl -> NewPlaylistDialog { dialog = null }
        null -> {}
    }
}
