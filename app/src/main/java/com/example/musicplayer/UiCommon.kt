package com.example.musicplayer

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

data class Detail(val kind: String, val key: String)          // kind: album / artist / playlist
data class AlbumItem(val id: Long, val name: String, val artist: String, val songs: List<Song>)
data class ArtistItem(val name: String, val songs: List<Song>)
data class PL(val key: String, val name: String, val icon: ImageVector, val count: Int)

class Actions(
    val play: (List<Song>, Int) -> Unit,
    val shuffle: (List<Song>) -> Unit,
    val playNext: (Song) -> Unit,
    val enqueue: (Song) -> Unit,
    val addToPl: (Song) -> Unit,
    val edit: (Song) -> Unit,
    val open: (Detail) -> Unit
)

fun albumsOf(songs: List<Song>): List<AlbumItem> =
    songs.groupBy { it.albumId }
        .map { (id, l) -> AlbumItem(id, l[0].album, l[0].who, l) }
        .sortedBy { it.name.lowercase() }

fun artistsOf(songs: List<Song>): List<ArtistItem> =
    songs.groupBy { it.who }
        .map { (n, l) -> ArtistItem(n, l) }
        .sortedBy { it.name.lowercase() }

fun resolve(d: Detail, songs: List<Song>, byId: Map<Long, Song>): List<Song> = when (d.kind) {
    "album" -> songs.filter { it.albumId.toString() == d.key }
        .sortedWith(compareBy<Song> { it.track }.thenBy { it.title.lowercase() })
    "artist" -> songs.filter { it.who == d.key }.sortedBy { it.name.lowercase() }
    else -> when (d.key) {
        "auto:recent" -> Store.recent.toList().mapNotNull { byId[it] }
        "auto:liked" -> Store.likes.mapNotNull { byId[it] }.sortedBy { it.name.lowercase() }
        "auto:top" -> Store.plays.entries.sortedByDescending { it.value }.take(100).mapNotNull { byId[it.key] }
        else -> Store.playlists[d.key.removePrefix("u:")].orEmpty().mapNotNull { byId[it] }
    }
}

fun playlistsList(songs: List<Song>, byId: Map<Long, Song>): List<PL> {
    val keys = listOf(
        Triple("auto:recent", tr("Recently played", "Reproducidas recientemente", "Недавно игравшие"), Icons.Filled.History),
        Triple("auto:liked", tr("Liked", "Me gusta", "Понравившиеся"), Icons.Filled.Favorite),
        Triple("auto:top", tr("Most played", "Más reproducidas", "Наиболее прослушиваемые"), Icons.Filled.TrendingUp)
    )
    val out = ArrayList<PL>()
    keys.forEach { (k, n, i) -> out.add(PL(k, n, i, resolve(Detail("playlist", k), songs, byId).size)) }
    Store.playlists.forEach { (n, ids) ->
        out.add(PL("u:$n", n, Icons.AutoMirrored.Filled.QueueMusic, ids.count { byId.containsKey(it) }))
    }
    return out
}

fun detailTitle(d: Detail, songs: List<Song>): String = when (d.kind) {
    "album" -> songs.firstOrNull { it.albumId.toString() == d.key }?.album ?: ""
    "artist" -> d.key
    else -> when (d.key) {
        "auto:recent" -> tr("Recently played", "Reproducidas recientemente", "Недавно игравшие")
        "auto:liked" -> tr("Liked", "Me gusta", "Понравившиеся")
        "auto:top" -> tr("Most played", "Más reproducidas", "Наиболее прослушиваемые")
        else -> d.key.removePrefix("u:")
    }
}

// ---------------------------------------------------------------- фон / экран

@Composable
fun Wallpaper() {
    if (!Store.wallOn) return
    val ctx = LocalContext.current
    val rev = Store.wallRev
    val bmp by produceState<Bitmap?>(null, rev) {
        value = withContext(Dispatchers.IO) { Wall.get(ctx, rev) }
    }
    val b = bmp ?: return
    val blur = Store.blur
    val shown = remember(b, blur) {
        if (Build.VERSION.SDK_INT < 31 && blur > 0f) {
            // на старых Android — простое размытие через уменьшение картинки
            val d = 1f + blur / 2f
            Bitmap.createScaledBitmap(b, max(1, (b.width / d).toInt()), max(1, (b.height / d).toInt()), true)
        } else b
    }
    var m = Modifier.fillMaxSize()
    if (Build.VERSION.SDK_INT >= 31 && blur > 0f) {
        m = m.graphicsLayer { scaleX = 1.1f; scaleY = 1.1f }.blur(blur.dp)
    }
    Image(shown.asImageBitmap(), null, m, contentScale = ContentScale.Crop)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = Store.dim)))
}

@Composable
fun Screen(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Wallpaper()
        Box(Modifier.fillMaxSize().systemBarsPadding()) { content() }
    }
}

// ---------------------------------------------------------------- обложка

@Composable
fun Cover(
    song: Song?,
    modifier: Modifier = Modifier,
    px: Int = 200,
    radius: Dp = 8.dp,
    coverPath: String? = song?.let { Store.overrides[it.id]?.cover }
) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, song?.id, coverPath, px) {
        value = if (song == null) null else withContext(Dispatchers.IO) { Covers.load(ctx, song, px, coverPath) }
    }
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                Icons.Filled.MusicNote, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ---------------------------------------------------------------- строки списков

@Composable
fun SongRow(
    s: Song,
    h: PlayerHolder,
    act: Actions,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    val cur = h.currentId == s.id
    val liked = s.id in Store.likes
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(s, Modifier.size(48.dp), 128, 6.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                s.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                "${s.who} • ${fmtTime(s.duration)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        if (liked) Icon(Icons.Filled.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(tr("Play next", "Reproducir a continuación", "Играть следующей")) },
                    leadingIcon = { Icon(Icons.Filled.SkipNext, null) },
                    onClick = { menu = false; act.playNext(s) })
                DropdownMenuItem(
                    text = { Text(tr("Add to queue", "Añadir a la cola", "Добавить в очередь")) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    onClick = { menu = false; act.enqueue(s) })
                DropdownMenuItem(
                    text = {
                        Text(
                            if (liked) tr("Remove like", "Quitar me gusta", "Убрать лайк")
                            else tr("Like", "Me gusta", "Лайк")
                        )
                    },
                    leadingIcon = { Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null) },
                    onClick = { menu = false; Store.toggleLike(s.id) })
                DropdownMenuItem(
                    text = { Text(tr("Add to playlist", "Añadir a lista", "В плейлист")) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    onClick = { menu = false; act.addToPl(s) })
                DropdownMenuItem(
                    text = { Text(tr("Edit info / cover", "Editar info / portada", "Изменить данные / обложку")) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menu = false; act.edit(s) })
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text(tr("Remove from playlist", "Quitar de la lista", "Убрать из плейлиста")) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
fun AlbumRow(a: AlbumItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(a.songs.first(), Modifier.size(48.dp), 128, 6.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${a.artist} • ${a.songs.size}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun AlbumCard(a: AlbumItem, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Cover(a.songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), 400, 10.dp)
        Text(
            a.name, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Text(
            a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ArtistRow(a: ArtistItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(a.songs.first(), Modifier.size(48.dp).clip(CircleShape), 128, 24.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${a.songs.size} " + tr("songs", "canciones", "песен"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun PlaylistRow(p: PL, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) { Icon(p.icon, null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${p.count} " + tr("songs", "canciones", "песен"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null) }
    }
}
