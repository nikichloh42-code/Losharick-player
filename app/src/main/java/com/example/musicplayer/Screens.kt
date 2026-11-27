package com.example.musicplayer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- поиск сверху

@Composable
fun TopSearch(q: String, onQ: (String) -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = q,
            onValueChange = onQ,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ),
            placeholder = {
                Text(
                    tr(
                        "Search songs, artists, albums…",
                        "Buscar canciones, artistas, álbumes…",
                        "Поиск песен, исполнителей, альбомов…"
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (q.isNotEmpty()) IconButton(onClick = { onQ("") }) { Icon(Icons.Filled.Close, null) }
            }
        )
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, null) }
    }
}

// ---------------------------------------------------------------- вкладка «Песни»

@Composable
fun SongsTab(songs: List<Song>, h: PlayerHolder, act: Actions, onRescan: () -> Unit) {
    val by = Store.sortBy
    val asc = Store.sortAsc
    val ov = Store.overrides
    val sorted = remember(songs, by, asc, ov) {
        val l = when (by) {
            0 -> songs.sortedBy { it.name.lowercase() }
            1 -> songs.sortedBy { it.who.lowercase() }
            2 -> songs.sortedBy { it.album.lowercase() }
            3 -> songs.sortedBy { it.added }
            else -> songs.sortedBy { it.duration }
        }
        if (asc) l else l.reversed()
    }
    val labels = listOf(
        tr("Title", "Título", "Название"),
        tr("Artist", "Artista", "Исполнитель"),
        tr("Album", "Álbum", "Альбом"),
        tr("Date added", "Fecha añadida", "Дата добавления"),
        tr("Duration", "Duración", "Длительность")
    )
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${sorted.size} " + tr("songs", "canciones", "песен") + " • " + labels[by.coerceIn(0, 4)],
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = { act.shuffle(sorted) }) { Icon(Icons.Filled.Shuffle, null) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Sort, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    labels.forEachIndexed { i, l ->
                        DropdownMenuItem(
                            text = { Text(l) },
                            trailingIcon = { if (by == i) Icon(Icons.Filled.Check, null) },
                            onClick = { Store.sortBy = i; Store.saveSettings(); menu = false }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (asc) tr("Ascending ↑", "Ascendente ↑", "По возрастанию ↑")
                                else tr("Descending ↓", "Descendente ↓", "По убыванию ↓")
                            )
                        },
                        onClick = { Store.sortAsc = !asc; Store.saveSettings(); menu = false }
                    )
                }
            }
            IconButton(onClick = onRescan) { Icon(Icons.Filled.Refresh, null) }
        }
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("No music found", "No se encontró música", "Музыка не найдена"))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(sorted, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, h, act, onClick = { act.play(sorted, i) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- «Альбомы» / «Исполнители»

@Composable
fun AlbumsTab(songs: List<Song>, act: Actions) {
    val ov = Store.overrides
    val albums = remember(songs, ov) { albumsOf(songs) }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        items(albums, key = { it.id }) { a -> AlbumCard(a) { act.open(Detail("album", a.id.toString())) } }
    }
}

@Composable
fun ArtistsTab(songs: List<Song>, act: Actions) {
    val ov = Store.overrides
    val artists = remember(songs, ov) { artistsOf(songs) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { a -> ArtistRow(a) { act.open(Detail("artist", a.name)) } }
    }
}

// ---------------------------------------------------------------- «Плейлисты»

@Composable
fun PlaylistsTab(
    songs: List<Song>,
    byId: Map<Long, Song>,
    act: Actions,
    onNew: () -> Unit,
    onDelete: (String) -> Unit
) {
    val rev = Store.statsRev
    val pls = remember(songs, rev, Store.playlists, Store.likes) { playlistsList(songs, byId) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Playlists", "Listas", "Плейлисты"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onNew) {
                Icon(Icons.Filled.Add, null)
                Text(tr("New", "Nueva", "Новый"))
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(pls, key = { it.key }) { p ->
                PlaylistRow(
                    p,
                    onClick = { act.open(Detail("playlist", p.key)) },
                    onDelete = if (p.key.startsWith("u:")) ({ onDelete(p.name) }) else null
                )
            }
        }
    }
}

// ---------------------------------------------------------------- «Статистика»

@Composable
fun StatBlock(title: String, list: List<Pair<String, Long>>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (list.isEmpty()) {
                Text(tr("Nothing yet — play some music", "Aún nada — reproduce música", "Пока пусто — включи музыку"))
            }
            val top = list.maxOfOrNull { it.second } ?: 1L
            list.forEachIndexed { i, (n, v) ->
                Column {
                    Row {
                        Text("${i + 1}. $n", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmtHours(v), style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(
                        progress = { (v.toFloat() / top.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatsTab(songs: List<Song>) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            Store.statsRev++
        }
    }
    val rev = Store.statsRev
    val ov = Store.overrides
    val total = Store.ms.values.sum()
    val albums = remember(rev, songs) {
        songs.groupBy { it.albumId }
            .map { (_, l) -> l[0].album to l.sumOf { Store.ms[it.id] ?: 0L } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
    }
    val artists = remember(rev, songs, ov) {
        songs.groupBy { it.who }
            .map { (k, l) -> k to l.sumOf { Store.ms[it.id] ?: 0L } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
    }
    val tops = remember(rev, songs, ov) {
        songs.map { it.name to (Store.ms[it.id] ?: 0L) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        tr("Total listening time", "Tiempo total de escucha", "Всего прослушано"),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        fmtHours(total), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        item { StatBlock(tr("Top 5 albums", "Top 5 álbumes", "Топ-5 альбомов"), albums) }
        item { StatBlock(tr("Top 5 artists", "Top 5 artistas", "Топ-5 исполнителей"), artists) }
        item { StatBlock(tr("Top 5 songs", "Top 5 canciones", "Топ-5 песен"), tops) }
    }
}

// ---------------------------------------------------------------- результаты поиска

@Composable
fun SearchResults(q: String, songs: List<Song>, byId: Map<Long, Song>, h: PlayerHolder, act: Actions) {
    var filter by remember { mutableIntStateOf(0) }   // 0 всё, 1 песни, 2 исполнители, 3 альбомы, 4 плейлисты
    val n = q.trim().lowercase()
    val ov = Store.overrides
    val rev = Store.statsRev
    val foundSongs = remember(n, songs, ov) {
        songs.filter { it.name.lowercase().contains(n) || it.who.lowercase().contains(n) || it.album.lowercase().contains(n) }
    }
    val foundArtists = remember(n, songs, ov) { artistsOf(songs).filter { it.name.lowercase().contains(n) } }
    val foundAlbums = remember(n, songs, ov) {
        albumsOf(songs).filter { it.name.lowercase().contains(n) || it.artist.lowercase().contains(n) }
    }
    val foundPls = remember(n, songs, rev, Store.playlists) {
        playlistsList(songs, byId).filter { it.name.lowercase().contains(n) }
    }
    val chips = listOf(
        tr("All", "Todo", "Всё"), tr("Songs", "Canciones", "Песни"), tr("Artists", "Artistas", "Исполнители"),
        tr("Albums", "Álbumes", "Альбомы"), tr("Playlists", "Listas", "Плейлисты")
    )
    val lim = if (filter == 0) 5 else Int.MAX_VALUE
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips.size) { i ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(chips[i]) })
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if ((filter == 0 || filter == 1) && foundSongs.isNotEmpty()) {
                item { Header(chips[1]) }
                itemsIndexed(foundSongs.take(lim)) { i, s -> SongRow(s, h, act, onClick = { act.play(foundSongs, i) }) }
            }
            if ((filter == 0 || filter == 2) && foundArtists.isNotEmpty()) {
                item { Header(chips[2]) }
                items(foundArtists.take(lim)) { a -> ArtistRow(a) { act.open(Detail("artist", a.name)) } }
            }
            if ((filter == 0 || filter == 3) && foundAlbums.isNotEmpty()) {
                item { Header(chips[3]) }
                items(foundAlbums.take(lim)) { a -> AlbumRow(a) { act.open(Detail("album", a.id.toString())) } }
            }
            if ((filter == 0 || filter == 4) && foundPls.isNotEmpty()) {
                item { Header(chips[4]) }
                items(foundPls.take(lim)) { p -> PlaylistRow(p, { act.open(Detail("playlist", p.key)) }) }
            }
            if (foundSongs.isEmpty() && foundArtists.isEmpty() && foundAlbums.isEmpty() && foundPls.isEmpty()) {
                item {
                    Text(
                        tr("Nothing found", "Nada encontrado", "Ничего не найдено"),
                        Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(t: String) {
    Text(
        t, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
    )
}

// ---------------------------------------------------------------- страница альбома / исполнителя / плейлиста

@Composable
fun DetailScreen(
    d: Detail,
    songs: List<Song>,
    byId: Map<Long, Song>,
    h: PlayerHolder,
    act: Actions,
    onBack: () -> Unit
) {
    val rev = Store.statsRev
    val ov = Store.overrides
    val list = remember(d, songs, byId, rev, ov, Store.likes, Store.playlists) { resolve(d, songs, byId) }
    val title = detailTitle(d, songs)
    val userPl = d.kind == "playlist" && d.key.startsWith("u:")
    Screen {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { act.play(list, 0) }, enabled = list.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Text(tr("Play", "Reproducir", "Играть"))
                }
                OutlinedButton(onClick = { act.shuffle(list) }, enabled = list.isNotEmpty()) {
                    Icon(Icons.Filled.Shuffle, null)
                    Text(tr("Shuffle", "Aleatorio", "Вперемешку"))
                }
            }
            Text(
                "${list.size} " + tr("songs", "canciones", "песен"),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list) { i, s ->
                    SongRow(
                        s, h, act,
                        onClick = { act.play(list, i) },
                        onRemove = if (userPl) ({ Store.removeFromPlaylist(d.key.removePrefix("u:"), s.id) }) else null
                    )
                }
            }
        }
    }
}
