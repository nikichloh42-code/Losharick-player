package com.example.musicplayer

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** Простой эквалайзер поверх аудио-сессии ExoPlayer */
object Eq {
    private var eq: Equalizer? = null
    var freqs by mutableStateOf(listOf<Int>())   // центральные частоты, Гц
    var minL by mutableIntStateOf(-1500)         // миллибелы
    var maxL by mutableIntStateOf(1500)

    fun attach(session: Int) {
        try {
            eq?.release()
        } catch (_: Exception) {
        }
        try {
            val e = Equalizer(0, session)
            eq = e
            val r = e.bandLevelRange
            minL = r[0].toInt()
            maxL = r[1].toInt()
            freqs = List(e.numberOfBands.toInt()) { e.getCenterFreq(it.toShort()) / 1000 }
            if (Store.eqLevels.size != freqs.size) Store.eqLevels = List(freqs.size) { 0 }
            push()
        } catch (_: Exception) {
            eq = null
        }
    }

    fun push() {
        val e = eq ?: return
        try {
            e.enabled = Store.eqOn
            val lv = Store.eqLevels
            for (i in 0 until e.numberOfBands.toInt()) {
                val v = lv.getOrElse(i) { 0 }.coerceIn(minL, maxL)
                e.setBandLevel(i.toShort(), v.toShort())
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            eq?.release()
        } catch (_: Exception) {
        }
        eq = null
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())

    // раз в секунду добавляем время прослушивания в статистику
    private val ticker = object : Runnable {
        override fun run() {
            val p = session?.player
            if (p != null && p.isPlaying) {
                p.currentMediaItem?.mediaId?.toLongOrNull()?.let { Store.addListen(it, 1000L) }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.toLongOrNull()?.let { Store.onPlayStart(it) }
            }
        })
        session = MediaSession.Builder(this, player).build()
        Eq.attach(player.audioSessionId)
        handler.postDelayed(ticker, 1000L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        Eq.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** Мост между интерфейсом и сервисом воспроизведения */
class PlayerHolder(private val ctx: Context) : Player.Listener {
    var c: MediaController? = null
    var ready by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var currentId by mutableLongStateOf(-1L)
    var queue by mutableStateOf(listOf<Long>())
    var index by mutableIntStateOf(-1)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffle by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            try {
                val mc = f.get()
                c = mc
                mc.addListener(this)
                ready = true
                sync()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        c = null
        ready = false
    }

    override fun onEvents(player: Player, events: Player.Events) {
        sync()
    }

    fun sync() {
        val p = c ?: return
        playing = p.isPlaying
        repeat = p.repeatMode
        shuffle = p.shuffleModeEnabled
        index = p.currentMediaItemIndex
        currentId = p.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        val n = p.mediaItemCount
        queue = List(n) { p.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
        poll()
    }

    fun poll() {
        val p = c ?: return
        position = p.currentPosition
        val d = p.duration
        duration = if (d != C.TIME_UNSET && d > 0) d else 0L
    }

    private fun item(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.name)
                .setArtist(s.who)
                .setAlbumTitle(s.album)
                .setArtworkUri(
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.albumId)
                )
                .build()
        )
        .build()

    fun play(list: List<Song>, start: Int) {
        val p = c ?: return
        if (list.isEmpty()) return
        p.setMediaItems(list.map { item(it) }, start.coerceIn(0, list.lastIndex), 0L)
        p.prepare()
        p.play()
    }

    fun shuffleAll(list: List<Song>) {
        val p = c ?: return
        if (list.isEmpty()) return
        p.shuffleModeEnabled = true
        p.repeatMode = Player.REPEAT_MODE_ALL
        play(list, list.indices.random())
    }

    fun enqueue(s: Song) {
        val p = c ?: return
        if (p.mediaItemCount == 0) play(listOf(s), 0) else p.addMediaItem(item(s))
    }

    fun playNext(s: Song) {
        val p = c ?: return
        if (p.mediaItemCount == 0) play(listOf(s), 0)
        else p.addMediaItem(p.currentMediaItemIndex + 1, item(s))
    }

    fun toggle() {
        val p = c ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            p.play()
        }
    }

    fun next() {
        c?.seekToNextMediaItem()
    }

    fun prev() {
        c?.seekToPrevious()
    }

    fun seek(ms: Long) {
        c?.seekTo(ms)
        poll()
    }

    fun jump(i: Int) {
        val p = c ?: return
        p.seekTo(i, 0L)
        p.play()
    }

    fun move(from: Int, to: Int) {
        c?.moveMediaItem(from, to)
    }

    fun remove(i: Int) {
        c?.removeMediaItem(i)
    }

    fun clear() {
        c?.clearMediaItems()
    }

    // 0 = по очереди, 1 = вперемешку, 2 = повтор одной песни
    fun mode(): Int = if (repeat == Player.REPEAT_MODE_ONE) 2 else if (shuffle) 1 else 0

    fun setMode(m: Int) {
        val p = c ?: return
        when (m) {
            0 -> {
                p.shuffleModeEnabled = false
                p.repeatMode = Player.REPEAT_MODE_ALL
            }
            1 -> {
                p.shuffleModeEnabled = true
                p.repeatMode = Player.REPEAT_MODE_ALL
            }
            else -> {
                p.shuffleModeEnabled = false
                p.repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }
}
