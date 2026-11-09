package com.koalatv.app

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var playerView: PlayerView
    private lateinit var osd: View
    private lateinit var osdNumber: TextView
    private lateinit var osdName: TextView
    private lateinit var osdInfo: TextView
    private lateinit var osdClock: TextView
    private lateinit var listPanel: View
    private lateinit var recycler: RecyclerView
    private lateinit var btnSources: TextView
    private lateinit var numberView: TextView
    private lateinit var statusView: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var adapter: ChannelAdapter

    private var channels: List<Channel> = emptyList()
    private var current = 0
    private var ready = false
    private var started = false
    private var listVisible = false
    private var videoSize = ""
    private var retries = 0
    private val digits = StringBuilder()
    private var panelWidthPx = 0

    private val hideOsdRunnable = Runnable {
        osd.animate().alpha(0f).setDuration(300).withEndAction { osd.visibility = View.GONE }.start()
    }
    private val commitDigitsRunnable = Runnable { commitDigits() }
    private val clearDigitsRunnable = Runnable { numberView.visibility = View.GONE }
    private val retryRunnable = Runnable {
        player?.prepare()
        player?.play()
    }
    private val clockRunnable = object : Runnable {
        override fun run() {
            if (osd.visibility == View.VISIBLE) updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFile(uri)
    }

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterFullscreen()

        playerView = findViewById(R.id.playerView)
        osd = findViewById(R.id.osd)
        osdNumber = findViewById(R.id.osdNumber)
        osdName = findViewById(R.id.osdName)
        osdInfo = findViewById(R.id.osdInfo)
        osdClock = findViewById(R.id.osdClock)
        listPanel = findViewById(R.id.listPanel)
        recycler = findViewById(R.id.recycler)
        btnSources = findViewById(R.id.btnSources)
        numberView = findViewById(R.id.numberView)
        statusView = findViewById(R.id.statusView)
        spinner = findViewById(R.id.spinner)

        // Ancho del panel: 300dp, o 75% de la pantalla si es mas angosta
        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        panelWidthPx = minOf((300 * density).toInt(), (screenW * 0.75f).toInt())
        listPanel.layoutParams.width = panelWidthPx

        recycler.layoutManager = LinearLayoutManager(this)
        btnSources.setOnClickListener { openSourcesMenu() }

        setupGestures()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (listVisible) {
                    hideList()
                } else {
                    isEnabled = false
                    finish()
                }
            }
        })

        if (ChannelStore.isLoaded()) {
            initChannels()
        } else {
            // Proceso restaurado sin pasar por el splash
            Thread {
                ChannelStore.loadAll(applicationContext) { _, _ -> }
                runOnUiThread { initChannels() }
            }.start()
        }
        handler.post(clockRunnable)
    }

    private fun initChannels() {
        channels = ChannelStore.channels
        if (channels.isEmpty()) {
            Toast.makeText(this, "No hay canales. Agrega una lista M3U8.", Toast.LENGTH_LONG).show()
        }
        current = ChannelStore.startIndex() // siempre inicia en Caracol
        adapter = ChannelAdapter(channels) { index ->
            tune(index)
            hideList()
        }
        adapter.setCurrent(current)
        recycler.adapter = adapter
        ready = true
        if (started) startPlayer()
    }

    override fun onStart() {
        super.onStart()
        started = true
        if (ready) startPlayer()
    }

    override fun onStop() {
        started = false
        releasePlayer()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullscreen()
    }

    private fun enterFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // ------------------------------------------------------------------ reproductor

    private fun startPlayer() {
        if (player != null || channels.isEmpty()) return
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(10000)
            .setUserAgent("KoalaTV/1.0")
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10000, 30000, 1500, 3000)
            .build()
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(loadControl)
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                spinner.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_READY) {
                    statusView.visibility = View.GONE
                    retries = 0
                }
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = if (size.height > 0) "${size.width}x${size.height}" else ""
                updateOsd()
            }

            override fun onPlayerError(error: PlaybackException) {
                spinner.visibility = View.GONE
                statusView.text = "Señal no disponible"
                statusView.visibility = View.VISIBLE
                if (retries < 3) {
                    retries++
                    handler.removeCallbacks(retryRunnable)
                    handler.postDelayed(retryRunnable, 3000)
                }
            }
        })
        playerView.player = p
        player = p
        tune(current)
    }

    private fun releasePlayer() {
        handler.removeCallbacks(retryRunnable)
        playerView.player = null
        player?.release()
        player = null
    }

    private fun tune(index: Int) {
        if (channels.isEmpty()) return
        current = ((index % channels.size) + channels.size) % channels.size
        val ch = channels[current]
        videoSize = ""
        retries = 0
        handler.removeCallbacks(retryRunnable)
        statusView.visibility = View.GONE
        player?.let {
            it.setMediaItem(MediaItem.fromUri(ch.url))
            it.prepare()
            it.playWhenReady = true
        }
        if (::adapter.isInitialized) adapter.setCurrent(current)
        showOsd()
        if (listVisible) scrollListToCurrent()
    }

    private fun zap(delta: Int) {
        if (channels.isEmpty()) return
        tune(current + delta)
    }

    // ------------------------------------------------------------------ OSD

    private fun showOsd() {
        updateOsd()
        osd.animate().cancel()
        osd.alpha = 1f
        osd.visibility = View.VISIBLE
        handler.removeCallbacks(hideOsdRunnable)
        handler.postDelayed(hideOsdRunnable, 5000)
    }

    private fun updateOsd() {
        if (channels.isEmpty()) return
        val ch = channels[current]
        osdNumber.text = ch.number.toString()
        osdName.text = ch.name
        val parts = ArrayList<String>()
        parts.add("Canal ${current + 1} de ${channels.size}")
        if (videoSize.isNotEmpty()) parts.add(videoSize)
        parts.add("HLS · En vivo")
        osdInfo.text = parts.joinToString("   ·   ")
        updateClock()
    }

    private fun updateClock() {
        osdClock.text = SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault()).format(Date())
    }

    // ------------------------------------------------------------------ lista de canales

    private fun showList() {
        if (listVisible || channels.isEmpty()) return
        listVisible = true
        listPanel.visibility = View.VISIBLE
        listPanel.translationX = -panelWidthPx.toFloat()
        listPanel.animate().translationX(0f).setDuration(180).start()
        scrollListToCurrent()
    }

    private fun hideList() {
        if (!listVisible) return
        listVisible = false
        listPanel.animate().translationX(-panelWidthPx.toFloat()).setDuration(160)
            .withEndAction { if (!listVisible) listPanel.visibility = View.GONE }
            .start()
    }

    private fun scrollListToCurrent() {
        val lm = recycler.layoutManager as LinearLayoutManager
        lm.scrollToPositionWithOffset(current, recycler.height / 3)
        recycler.postDelayed({
            recycler.findViewHolderForAdapterPosition(current)?.itemView?.requestFocus()
        }, 120)
    }

    // ------------------------------------------------------------------ teclado / control remoto

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!ready) return super.onKeyDown(keyCode, event)

        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                zap(+1); return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                zap(-1); return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (!listVisible) {
                zap(+1); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!listVisible) {
                zap(-1); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!listVisible) {
                showList(); return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (listVisible) {
                hideList(); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (!listVisible) {
                if (digits.isNotEmpty()) commitDigits() else showList()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                openSourcesMenu(); return true
            }
            KeyEvent.KEYCODE_INFO -> {
                showOsd(); return true
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                addDigit(keyCode - KeyEvent.KEYCODE_0); return true
            }
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                addDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------ numero directo

    private fun addDigit(d: Int) {
        if (digits.length >= 4) digits.clear()
        digits.append(d)
        numberView.text = digits.toString()
        numberView.visibility = View.VISIBLE
        handler.removeCallbacks(clearDigitsRunnable)
        handler.removeCallbacks(commitDigitsRunnable)
        val maxLen = channels.maxOfOrNull { it.number.toString().length } ?: 3
        if (digits.length >= maxLen) {
            commitDigits()
        } else {
            handler.postDelayed(commitDigitsRunnable, 2000)
        }
    }

    private fun commitDigits() {
        handler.removeCallbacks(commitDigitsRunnable)
        val n = digits.toString().toIntOrNull()
        digits.clear()
        if (n != null) {
            val idx = channels.indexOfFirst { it.number == n }
            if (idx >= 0) {
                numberView.visibility = View.GONE
                tune(idx)
            } else {
                numberView.text = "$n ✕"
                handler.postDelayed(clearDigitsRunnable, 1200)
            }
        } else {
            numberView.visibility = View.GONE
        }
    }

    // ------------------------------------------------------------------ tactil (celular)

    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (listVisible) hideList() else showList()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                showOsd(); return true
            }

            override fun onLongPress(e: MotionEvent) {
                openSourcesMenu()
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val start = e1 ?: return false
                val dy = e2.y - start.y
                val dx = e2.x - start.x
                if (abs(dy) > abs(dx) && abs(dy) > 120) {
                    if (dy < 0) zap(+1) else zap(-1)
                    return true
                }
                return false
            }
        })
        playerView.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            true
        }
    }

    // ------------------------------------------------------------------ menu de listas M3U8

    private fun openSourcesMenu() {
        if (!ready) return
        val items = arrayOf(
            "Agregar lista por URL (.m3u / .m3u8)",
            "Agregar lista desde un archivo",
            "Administrar listas agregadas",
            "Recargar canales"
        )
        AlertDialog.Builder(this)
            .setTitle("KoalaTV · Listas de canales")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> askUrl()
                    1 -> pickFile()
                    2 -> manageSources()
                    3 -> reloadChannels("Canales actualizados")
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun askUrl() {
        val input = EditText(this).apply {
            hint = "http://servidor/lista.m3u8"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("URL de la lista")
            .setView(input)
            .setPositiveButton("Agregar") { _, _ ->
                val url = input.text.toString().trim()
                if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                    Toast.makeText(this, "La URL debe empezar con http:// o https://", Toast.LENGTH_LONG).show()
                } else {
                    val host = try { Uri.parse(url).host ?: url } catch (e: Exception) { url }
                    ChannelStore.addSource(this, ChannelStore.Source(host, url, false))
                    reloadChannels("Lista agregada")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickFile() {
        try {
            openDoc.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Este equipo no tiene selector de archivos. Usa la opcion de URL.", Toast.LENGTH_LONG).show()
        }
    }

    private fun importFile(uri: Uri) {
        Thread {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("vacio")
                if (M3uParser.parse(text).isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this, "El archivo no contiene canales validos", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }
                val dest = File(filesDir, "lista_${System.currentTimeMillis()}.m3u8")
                dest.writeText(text, Charsets.UTF_8)
                val label = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: dest.name
                ChannelStore.addSource(this, ChannelStore.Source(label, dest.absolutePath, true))
                runOnUiThread { reloadChannels("Lista importada") }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "No se pudo leer el archivo", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun manageSources() {
        val list = ChannelStore.sources(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "No hay listas agregadas todavia", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Toca una lista para eliminarla")
            .setItems(list.map { it.name }.toTypedArray()) { _, which ->
                val s = list[which]
                AlertDialog.Builder(this)
                    .setTitle("Eliminar lista")
                    .setMessage("¿Eliminar \"${s.name}\"?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        ChannelStore.removeSource(this, s)
                        reloadChannels("Lista eliminada")
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun reloadChannels(doneMessage: String) {
        Toast.makeText(this, "Actualizando canales…", Toast.LENGTH_SHORT).show()
        val previousUrl = channels.getOrNull(current)?.url
        Thread {
            ChannelStore.loadAll(applicationContext) { _, _ -> }
            runOnUiThread {
                channels = ChannelStore.channels
                var idx = if (previousUrl != null) channels.indexOfFirst { it.url == previousUrl } else -1
                val keepPlaying = idx >= 0
                if (idx < 0) idx = ChannelStore.startIndex()
                current = idx
                adapter.submit(channels, current)
                if (!keepPlaying) {
                    if (player == null && started) startPlayer() else tune(current)
                } else {
                    updateOsd()
                }
                Toast.makeText(this, "$doneMessage · ${channels.size} canales", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
