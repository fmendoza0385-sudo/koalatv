package com.koalatv.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Pantalla de inicio: logo + barra de progreso azul con porcentaje mientras se cargan los canales. */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bar: ProgressBar
    private lateinit var percent: TextView
    private lateinit var status: TextView

    @Volatile private var target = 0
    @Volatile private var statusText = "Iniciando…"
    private var shown = 0
    private var launched = false

    private val tick = object : Runnable {
        override fun run() {
            if (shown < target) {
                shown++
                bar.progress = shown
                percent.text = "$shown%"
                status.text = statusText
            }
            if (shown >= 100) {
                goMain()
            } else {
                handler.postDelayed(this, 14)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        bar = findViewById(R.id.progress)
        percent = findViewById(R.id.percent)
        status = findViewById(R.id.status)

        handler.post(tick)

        Thread {
            ChannelStore.loadAll(applicationContext) { pct, text ->
                statusText = text
                target = pct
            }
        }.start()
    }

    private fun goMain() {
        if (launched) return
        launched = true
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 250)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
