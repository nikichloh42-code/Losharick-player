package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Пользовательские правки песни: название, исполнитель, путь к обложке. null = не менялось. */
data class Over(val title: String?, val artist: String?, val cover: String?)

object Store {
    private lateinit var sp: SharedPreferences

    // ---- настройки (Compose-состояние, UI обновляется сам) ----
    var lang by mutableStateOf("en")
    var theme by mutableIntStateOf(0)          // 0 система, 1 светлая, 2 тёмная, 3 AMOLED
    var accent by mutableIntStateOf(0)
    var scale by mutableFloatStateOf(1f)       // масштаб всего интерфейса
    var wallOn by mutableStateOf(false)
    var wallRev by mutableIntStateOf(0)
    var dim by mutableFloatStateOf(0.4f)       // затемнение обоев 0..0.9
    var blur by mutableFloatStateOf(0f)        // размытие обоев 0..25
    var sortBy by mutableIntStateOf(0)
    var sortAsc by mutableStateOf(true)
    var eqOn by mutableStateOf(false)
    var eqLevels by mutableStateOf(listOf<Int>())

    // ---- данные ----
    var likes by mutableStateOf(setOf<Long>())
    var overrides by mutableStateOf(mapOf<Long, Over>())
    var playlists by mutableStateOf(mapOf<String, List<Long>>())
    var statsRev by mutableIntStateOf(0)

    val recent = ArrayList<Long>()             // новые в начале
    val plays = HashMap<Long, Int>()           // сколько раз запускалась песня
    val ms = HashMap<Long, Long>()             // сколько миллисекунд слушали песню
    private var lastSave = 0L

    private fun longs(s: String): List<Long> {
        val a = JSONArray(s)
        return List(a.length()) { a.getLong(it) }
    }

    private fun ints(s: String): List<Int> {
        val a = JSONArray(s)
        return List(a.length()) { a.getInt(it) }
    }

    fun init(c: Context) {
        sp = c.applicationContext.getSharedPreferences("mp", Context.MODE_PRIVATE)
        lang = sp.getString("lang", null) ?: when (Locale.getDefault().language) {
            "ru" -> "ru"
            "es" -> "es"
            else -> "en"
        }
        theme = sp.getInt("theme", 0)
        accent = sp.getInt("accent", 0)
        scale = sp.getFloat("scale", 1f)
        wallOn = sp.getBoolean("wallOn", false)
        dim = sp.getFloat("dim", 0.4f)
        blur = sp.getFloat("blur", 0f)
        sortBy = sp.getInt("sortBy", 0)
        sortAsc = sp.getBoolean("sortAsc", true)
        eqOn = sp.getBoolean("eqOn", false)
        try {
            eqLevels = ints(sp.getString("eq", "[]")!!)
            likes = longs(sp.getString("likes", "[]")!!).toSet()
            recent.addAll(longs(sp.getString("recent", "[]")!!))
            val p = JSONObject(sp.getString("plays", "{}")!!)
            p.keys().forEach { plays[it.toLong()] = p.getInt(it) }
            val m = JSONObject(sp.getString("ms", "{}")!!)
            m.keys().forEach { ms[it.toLong()] = m.getLong(it) }
            val o = JSONObject(sp.getString("over", "{}")!!)
            val ov = LinkedHashMap<Long, Over>()
            o.keys().forEach { k ->
                val j = o.getJSONObject(k)
                ov[k.toLong()] = Over(
                    j.optString("t", "").ifEmpty { null },
                    j.optString("a", "").ifEmpty { null },
                    j.optString("c", "").ifEmpty { null }
                )
            }
            overrides = ov
            val pl = JSONArray(sp.getString("pl", "[]")!!)
            val map = LinkedHashMap<String, List<Long>>()
            for (i in 0 until pl.length()) {
                val j = pl.getJSONObject(i)
                map[j.getString("n")] = longs(j.getJSONArray("ids").toString())
            }
            playlists = map
        } catch (_: Exception) {
        }
    }

    fun saveSettings() {
        sp.edit()
            .putString("lang", lang)
            .putInt("theme", theme)
            .putInt("accent", accent)
            .putFloat("scale", scale)
            .putBoolean("wallOn", wallOn)
            .putFloat("dim", dim)
            .putFloat("blur", blur)
            .putInt("sortBy", sortBy)
            .putBoolean("sortAsc", sortAsc)
            .putBoolean("eqOn", eqOn)
            .putString("eq", JSONArray(eqLevels).toString())
            .apply()
    }

    private fun saveData() {
        val o = JSONObject()
        overrides.forEach { (id, v) ->
            o.put(
                id.toString(),
                JSONObject().put("t", v.title ?: "").put("a", v.artist ?: "").put("c", v.cover ?: "")
            )
        }
        val pl = JSONArray()
        playlists.forEach { (n, ids) -> pl.put(JSONObject().put("n", n).put("ids", JSONArray(ids))) }
        sp.edit()
            .putString("likes", JSONArray(likes.toList()).toString())
            .putString("over", o.toString())
            .putString("pl", pl.toString())
            .apply()
    }

    fun saveStats() {
        lastSave = System.currentTimeMillis()
        val p = JSONObject()
        plays.forEach { (k, v) -> p.put(k.toString(), v) }
        val m = JSONObject()
        ms.forEach { (k, v) -> m.put(k.toString(), v) }
        sp.edit()
            .putString("recent", JSONArray(recent).toString())
            .putString("plays", p.toString())
            .putString("ms", m.toString())
            .apply()
        statsRev++
    }

    // ---- действия ----
    fun toggleLike(id: Long) {
        likes = if (id in likes) likes - id else likes + id
        saveData()
    }

    fun setOverride(id: Long, o: Over?) {
        overrides = if (o == null) overrides - id else overrides + (id to o)
        saveData()
    }

    fun createPlaylist(n: String, id: Long? = null): Boolean {
        val name = n.trim()
        if (name.isEmpty()) return false
        if (!playlists.containsKey(name)) playlists = playlists + (name to emptyList())
        if (id != null) addToPlaylist(name, id)
        saveData()
        return true
    }

    fun addToPlaylist(name: String, id: Long) {
        val l = playlists[name] ?: return
        if (id !in l) playlists = playlists + (name to (l + id))
        saveData()
    }

    fun removeFromPlaylist(name: String, id: Long) {
        val l = playlists[name] ?: return
        playlists = playlists + (name to l.filter { it != id })
        saveData()
    }

    fun deletePlaylist(name: String) {
        playlists = playlists - name
        saveData()
    }

    /** Вызывается, когда песня начала играть. */
    fun onPlayStart(id: Long) {
        recent.remove(id)
        recent.add(0, id)
        while (recent.size > 100) recent.removeAt(recent.size - 1)
        plays[id] = (plays[id] ?: 0) + 1
        saveStats()
    }

    /** Вызывается каждую секунду, пока играет музыка. */
    fun addListen(id: Long, d: Long) {
        ms[id] = (ms[id] ?: 0L) + d
        if (System.currentTimeMillis() - lastSave > 15000) saveStats()
    }
}
