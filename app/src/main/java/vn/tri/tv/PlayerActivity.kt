package vn.tri.tv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlayerActivity : Activity() {

    companion object {
        /** Danh sách đang phát (một hàng trên màn hình chính), để bấm lên/xuống đổi kênh. */
        var queue: List<Channel> = emptyList()
    }

    private lateinit var playerView: PlayerView
    private lateinit var osd: TextView
    private var player: ExoPlayer? = null
    private val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    private var index = 0
    private val main = Handler(Looper.getMainLooper())
    private val hideOsd = Runnable { osd.visibility = View.GONE }

    private val current get() = queue.getOrNull(index)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        osd = findViewById(R.id.osd)
        index = savedInstanceState?.getInt("index") ?: intent.getIntExtra("index", 0)
        if (queue.isEmpty()) { finish(); return }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("index", index)
    }

    override fun onStart() {
        super.onStart()
        if (queue.isEmpty()) return
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                showOsd("Không phát được: ${error.errorCodeName}", 6000)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) current?.let { Store.setPosition(this@PlayerActivity, it.url, 0) }
            }
        })
        playerView.player = p
        player = p
        playCurrent()
    }

    override fun onStop() {
        savePosition()
        player?.release()
        player = null
        playerView.player = null
        super.onStop()
    }

    private fun playCurrent() {
        val ch = current ?: return
        val p = player ?: return
        http.setUserAgent(ch.userAgent.ifBlank { "TriTV/0.1" })
        http.setDefaultRequestProperties(if (ch.referer.isBlank()) emptyMap() else mapOf("Referer" to ch.referer))

        val b = MediaItem.Builder().setUri(ch.url)
        if (ch.url.contains(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        p.setMediaItem(b.build(), Store.position(this, ch.url))
        p.prepare()
        p.playWhenReady = true

        Store.addRecent(this, ch)
        showOsd(if (queue.size > 1) "${ch.name}   (${index + 1}/${queue.size})" else ch.name)
    }

    private fun savePosition() {
        val p = player ?: return
        val ch = current ?: return
        // Chỉ nhớ vị trí với phim (có độ dài, tua được); kênh live thì bỏ qua.
        if (!p.isCurrentMediaItemLive && p.isCurrentMediaItemSeekable && p.duration != C.TIME_UNSET) {
            val pos = p.currentPosition
            val nearEnd = p.duration - pos < 30_000
            Store.setPosition(this, ch.url, if (pos < 10_000 || nearEnd) 0 else pos)
        }
    }

    private fun switchBy(delta: Int) {
        if (queue.size < 2) return
        savePosition()
        index = (index + delta + queue.size) % queue.size
        playCurrent()
    }

    private fun showOsd(text: String, ms: Long = 2500) {
        osd.text = text
        osd.visibility = View.VISIBLE
        main.removeCallbacks(hideOsd)
        main.postDelayed(hideOsd, ms)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> { switchBy(-1); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> { switchBy(1); return true }
            }
        }
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    @Deprecated("Activity API")
    override fun onBackPressed() {
        if (playerView.isControllerFullyVisible) playerView.hideController()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
