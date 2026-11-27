package com.example.musicplayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

private fun cancelText() = tr("Cancel", "Cancelar", "Отмена")

@Composable
fun EditDialog(s: Song, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val old = Store.overrides[s.id]
    var title by remember { mutableStateOf(s.name) }
    var artist by remember { mutableStateOf(s.who) }
    var cover by remember { mutableStateOf(old?.cover) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val f = File(ctx.filesDir, "covers/${s.id}_${System.currentTimeMillis()}.jpg")
            if (importImage(ctx, uri, f, 1200)) cover = f.absolutePath
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Edit song", "Editar canción", "Изменить песню")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(s, Modifier.size(96.dp), 256, 8.dp, cover)
                    Column(Modifier.padding(start = 12.dp)) {
                        Button(onClick = { pick.launch("image/*") }) {
                            Text(tr("Change cover", "Cambiar portada", "Сменить обложку"))
                        }
                        if (cover != null) {
                            TextButton(onClick = { cover = null }) {
                                Text(tr("Original cover", "Portada original", "Исходная обложка"))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    title, { title = it }, singleLine = true,
                    label = { Text(tr("Title", "Título", "Название")) }
                )
                OutlinedTextField(
                    artist, { artist = it }, singleLine = true,
                    label = { Text(tr("Artist", "Artista", "Исполнитель")) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = title.trim().takeIf { it.isNotEmpty() && it != s.title }
                val a = artist.trim().takeIf { it.isNotEmpty() && it != s.artist }
                val o = Over(t, a, cover)
                if (old?.cover != null && old.cover != cover) File(old.cover).delete()
                Store.setOverride(s.id, if (t == null && a == null && cover == null) null else o)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    old?.cover?.let { File(it).delete() }
                    Store.setOverride(s.id, null)
                    onDismiss()
                }) { Text(tr("Reset all", "Restablecer", "Сбросить всё")) }
                TextButton(onClick = onDismiss) { Text(cancelText()) }
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(s: Song, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add to playlist", "Añadir a lista", "Добавить в плейлист")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Store.playlists.keys.toList().forEach { n ->
                    Text(
                        n,
                        Modifier.fillMaxWidth()
                            .clickable { Store.addToPlaylist(n, s.id); onDismiss() }
                            .padding(vertical = 12.dp)
                    )
                }
                OutlinedTextField(
                    name, { name = it }, singleLine = true,
                    label = { Text(tr("New playlist", "Nueva lista", "Новый плейлист")) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    Store.createPlaylist(name, s.id)
                    onDismiss()
                }
            }) { Text(tr("Create", "Crear", "Создать")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelText()) } }
    )
}

@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("New playlist", "Nueva lista", "Новый плейлист")) },
        text = {
            OutlinedTextField(
                name, { name = it }, singleLine = true,
                label = { Text(tr("Name", "Nombre", "Название")) }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (Store.createPlaylist(name)) onDismiss() }) {
                Text(tr("Create", "Crear", "Создать"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelText()) } }
    )
}

@Composable
fun DeletePlaylistDialog(name: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Delete playlist?", "¿Eliminar lista?", "Удалить плейлист?")) },
        text = { Text(name) },
        confirmButton = {
            TextButton(onClick = { Store.deletePlaylist(name); onDismiss() }) {
                Text(tr("Delete", "Eliminar", "Удалить"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelText()) } }
    )
}
