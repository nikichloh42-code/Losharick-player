package com.example.musicplayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(o) })
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onRescan: () -> Unit) {
    val ctx = LocalContext.current
    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && importImage(ctx, uri, File(ctx.filesDir, "wallpaper.jpg"), 1600)) {
            Store.wallRev++
            Store.wallOn = true
            Store.saveSettings()
        }
    }
    Screen {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(tr("Settings", "Ajustes", "Настройки"), style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- язык
                Section(tr("Language", "Idioma", "Язык")) {
                    val codes = listOf("en", "es", "ru")
                    Chips(listOf("English", "Español", "Русский"), codes.indexOf(Store.lang).coerceAtLeast(0)) {
                        Store.lang = codes[it]
                        Store.saveSettings()
                    }
                }

                // ---- тема
                Section(tr("Theme", "Tema", "Тема")) {
                    Chips(
                        listOf(
                            tr("System", "Sistema", "Системная"), tr("Light", "Claro", "Светлая"),
                            tr("Dark", "Oscuro", "Тёмная"), "AMOLED"
                        ), Store.theme
                    ) { Store.theme = it; Store.saveSettings() }
                    Text(tr("Accent color", "Color de acento", "Цвет акцента"))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Accents.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(c)
                                    .border(
                                        if (Store.accent == i) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                                        else BorderStroke(0.dp, c), CircleShape
                                    )
                                    .clickable { Store.accent = i; Store.saveSettings() }
                            )
                        }
                    }
                }

                // ---- масштаб интерфейса
                Section(tr("Interface scale", "Escala de la interfaz", "Масштаб интерфейса")) {
                    var sc by remember { mutableFloatStateOf(Store.scale) }
                    Text("x" + "%.2f".format(sc))
                    Slider(
                        value = sc, onValueChange = { sc = it }, valueRange = 0.7f..1.5f,
                        onValueChangeFinished = {
                            sc = (sc * 20).roundToInt() / 20f
                            Store.scale = sc
                            Store.saveSettings()
                        }
                    )
                    TextButton(onClick = { sc = 1f; Store.scale = 1f; Store.saveSettings() }) {
                        Text(tr("Reset", "Restablecer", "Сбросить"))
                    }
                }

                // ---- обои
                Section(tr("Wallpaper", "Fondo", "Обои")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickWall.launch("image/*") }) {
                            Icon(Icons.Filled.Image, null)
                            Text(tr("Choose image", "Elegir imagen", "Выбрать картинку"))
                        }
                        if (Store.wallOn) {
                            TextButton(onClick = {
                                File(ctx.filesDir, "wallpaper.jpg").delete()
                                Store.wallOn = false
                                Store.wallRev++
                                Store.saveSettings()
                            }) { Text(tr("Remove", "Quitar", "Убрать")) }
                        }
                    }
                    if (Store.wallOn) {
                        Text(tr("Dimming", "Oscurecimiento", "Затемнение") + ": " + (Store.dim * 100).roundToInt() + "%")
                        Slider(
                            value = Store.dim, onValueChange = { Store.dim = it },
                            valueRange = 0f..0.9f, onValueChangeFinished = { Store.saveSettings() }
                        )
                        Text(tr("Blur", "Desenfoque", "Размытие") + ": " + Store.blur.roundToInt())
                        Slider(
                            value = Store.blur, onValueChange = { Store.blur = it },
                            valueRange = 0f..25f, onValueChangeFinished = { Store.saveSettings() }
                        )
                    }
                }

                // ---- эквалайзер
                Section(tr("Equalizer", "Ecualizador", "Эквалайзер")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Enabled", "Activado", "Включён"), Modifier.weight(1f))
                        Switch(checked = Store.eqOn, onCheckedChange = {
                            Store.eqOn = it
                            Eq.push()
                            Store.saveSettings()
                        })
                    }
                    if (Eq.freqs.isEmpty()) {
                        Text(
                            tr(
                                "Start playing any song first — then the bands will appear here.",
                                "Primero reproduce una canción — luego aparecerán las bandas.",
                                "Сначала включи любую песню — тогда здесь появятся полосы."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Eq.freqs.forEachIndexed { i, f ->
                            val lv = Store.eqLevels.getOrElse(i) { 0 }
                            val label = if (f >= 1000) "${f / 1000} kHz" else "$f Hz"
                            Text("$label    " + "%+.1f".format(lv / 100f) + " dB", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = lv.toFloat(),
                                onValueChange = { v ->
                                    val l = Store.eqLevels.toMutableList()
                                    while (l.size <= i) l.add(0)
                                    l[i] = v.roundToInt()
                                    Store.eqLevels = l
                                    Eq.push()
                                },
                                valueRange = Eq.minL.toFloat()..Eq.maxL.toFloat(),
                                onValueChangeFinished = { Store.saveSettings() }
                            )
                        }
                        TextButton(onClick = {
                            Store.eqLevels = List(Eq.freqs.size) { 0 }
                            Eq.push()
                            Store.saveSettings()
                        }) { Text(tr("Reset", "Restablecer", "Сбросить")) }
                    }
                }

                // ---- библиотека
                Section(tr("Library", "Biblioteca", "Библиотека")) {
                    OutlinedButton(onClick = onRescan) {
                        Icon(Icons.Filled.Refresh, null)
                        Text(tr("Rescan music", "Buscar música de nuevo", "Пересканировать музыку"))
                    }
                }
            }
        }
    }
}
