package com.grm.tvcanales

import android.net.Uri
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_URL = "url"
    }

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private lateinit var surface: SurfaceView
    private lateinit var loading: ProgressBar
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        surface = findViewById(R.id.surface)
        loading = findViewById(R.id.loading)
        status = findViewById(R.id.status)

        title = intent.getStringExtra(EXTRA_NAME)
        status.text = intent.getStringExtra(EXTRA_NAME).orEmpty()
    }

    override fun onStart() {
        super.onStart()
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        startPlayback(url)
    }

    private fun startPlayback(url: String) {
        val options = arrayListOf(
            "--network-caching=${Config.NETWORK_CACHING_MS}",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--avcodec-hw=any",        // usa hardware si puede, si no software
            "--deinterlace=1",          // canales SD entrelazados (como MPEG-2 480i)
            "--deinterlace-mode=yadif",
            "--http-user-agent=VLC/3.0.20 LibVLC/3.0.20"
        )
        val vlc = LibVLC(this, options).also { libVlc = it }
        val mp = MediaPlayer(vlc).also { player = it }

        mp.setEventListener { event ->
            runOnUiThread {
                when (event.type) {
                    MediaPlayer.Event.Playing -> loading.visibility = View.GONE
                    MediaPlayer.Event.Buffering ->
                        loading.visibility = if (event.buffering < 100f) View.VISIBLE else View.GONE
                    MediaPlayer.Event.EncounteredError -> {
                        loading.visibility = View.GONE
                        status.text = getString(R.string.error_reproduccion)
                    }
                }
            }
        }

        mp.vlcVout.setVideoView(surface)
        mp.vlcVout.attachViews()
        surface.post { mp.vlcVout.setWindowSize(surface.width, surface.height) }

        val media = Media(vlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false) // HW si hay; si falla, cae a software
        mp.media = media
        media.release()
        mp.play()
    }

    override fun onStop() {
        super.onStop()
        player?.let {
            it.stop()
            it.vlcVout.detachViews()
            it.release()
        }
        libVlc?.release()
        player = null
        libVlc = null
    }
}
