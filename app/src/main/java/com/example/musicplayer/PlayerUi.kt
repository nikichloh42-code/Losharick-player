package com.example.musicplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun modeIcon(m: Int) = when (m) {
    1 -> Icons.Filled.Shuffle
    2 -> Icons.Filled.RepeatOne
    else -> Icons.Filled.FormatListNumbered
}

private fun modeName(m: Int) = when (m) {
    1 -> tr("Shuffle", "Aleatorio", "Вперемешку")
    2 -> tr("Repeat one", "Repetir una", "Повтор одной")
    else -> tr("In order", "En orden", "По очереди")
}

// ---------------------------------------------------------------- мини-плеер

@Composable
fun MiniPlayer(h: PlayerHolder, s: Song?, onOpen: () -> Unit) {
    if (s == null) return
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (Store.wallOn) 0.85f else 1f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Column {
            LinearProgressIndicator(
                progress = { if (h.duration > 0) (h.position.toFloat() / h.duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(s, Modifier.size(44.dp), 128, 6.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        s.who, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { h.toggle() }) {
                    Icon(if (h.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                }
                IconButton(onClick = { h.next() }) { Icon(Icons.Filled.SkipNext, null) }
            }
        }
    }
}

// ---------------------------------------------------------------- полноэкранный плеер

@Composable
fun FullPlayer(h: PlayerHolder, s: Song?, act: Actions, onClose: () -> Unit, onQueue: () -> Unit) {
    Screen {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                Text(
                    tr("Now playing", "Reproduciendo", "Сейчас играет"),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }
            }

            // обложка: почти на всю ширину экрана, скругление совсем небольшое
            Cover(s, Modifier.fillMaxWidth().aspectRatio(1f), 1024, 12.dp)

            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        s?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )
                    Text(
                        s?.who ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                if (s != null) {
                    val liked = s.id in Store.likes
                    IconButton(onClick = { Store.toggleLike(s.id) }) {
                        Icon(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { act.edit(s) }) { Icon(Icons.Filled.Edit, null) }
                }
            }

            var drag by remember { mutableStateOf<Float?>(null) }
            val dur = if (h.duration > 0) h.duration else (s?.duration ?: 0L)
            val frac = if (dur > 0) (h.position.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Slider(
                value = drag ?: frac,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { h.seek((it * dur).toLong()) }
                    drag = null
                }
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Text(fmtTime(((drag ?: frac) * dur).toLong()), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(fmtTime(dur), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { h.setMode((h.mode() + 1) % 3) }) {
                    Icon(modeIcon(h.mode()), modeName(h.mode()), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { h.prev() }, Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, null, Modifier.size(36.dp))
                }
                FilledIconButton(onClick = { h.toggle() }, Modifier.size(72.dp)) {
                    Icon(if (h.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(40.dp))
                }
                IconButton(onClick = { h.next() }, Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, null, Modifier.size(36.dp))
                }
                IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }
            }
            Text(
                modeName(h.mode()), Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ---------------------------------------------------------------- очередь

@Composable
fun QueueScreen(h: PlayerHolder, byId: Map<Long, Song>, onBack: () -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { state.scrollToItem((h.index - 1).coerceAtLeast(0)) }
    Screen {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(
                    tr("Queue", "Cola", "Очередь") + " (${h.queue.size})",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = { h.clear() }) { Text(tr("Clear", "Vaciar", "Очистить")) }
            }
            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (m in 0..2) {
                    FilterChip(
                        selected = h.mode() == m,
                        onClick = { h.setMode(m) },
                        label = { Text(modeName(m), maxLines = 1) },
                        leadingIcon = { Icon(modeIcon(m), null, Modifier.size(18.dp)) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                itemsIndexed(h.queue) { i, id ->
                    val s = byId[id]
                    val cur = i == h.index
                    if (s != null) {
                        Row(
                            Modifier.fillMaxWidth().clickable { h.jump(i) }.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (cur) {
                                Icon(
                                    Icons.Filled.GraphicEq, null, Modifier.width(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    "${i + 1}", Modifier.width(24.dp), style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Cover(s, Modifier.padding(start = 8.dp).size(44.dp), 128, 6.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(
                                    s.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    s.who, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { h.move(i, i - 1) }, Modifier.size(36.dp), enabled = i > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, null)
                            }
                            IconButton(
                                onClick = { h.move(i, i + 1) }, Modifier.size(36.dp),
                                enabled = i < h.queue.lastIndex
                            ) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                            IconButton(onClick = { h.remove(i) }, Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, null)
                            }
                        }
                    }
                }
            }
        }
    }
}
