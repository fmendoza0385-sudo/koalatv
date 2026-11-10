package com.grm.tvcanales

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var list: ListView
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private var channels: List<Channel> = emptyList()

    private val listFile: File get() = File(filesDir, "lista.m3u")
    private val prefs get() = getSharedPreferences("tvcanales", Context.MODE_PRIVATE)

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        list = findViewById(R.id.channelList)
        progress = findViewById(R.id.progress)
        message = findViewById(R.id.message)

        findViewById<Button>(R.id.btnArchivo).setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }
        findViewById<Button>(R.id.btnUrl).setOnClickListener { askUrl() }
        findViewById<Button>(R.id.btnBorrar).setOnClickListener { clearList() }

        list.setOnItemClickListener { _, _, position, _ ->
            val ch = channels[position]
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_NAME, ch.name)
                    .putExtra(PlayerActivity.EXTRA_URL, ch.url)
            )
        }

        showSavedList()
    }

    // ---------- Cargar lista guardada ----------

    private fun showSavedList() {
        if (!listFile.exists()) {
            showMessage(getString(R.string.sin_lista))
            return
        }
        applyText(listFile.readText())
    }

    private fun applyText(text: String) {
        val parsed = M3uParser.parse(text)
        channels = parsed
        if (parsed.isEmpty()) {
            list.adapter = null
            showMessage(getString(R.string.sin_canales))
        } else {
            message.visibility = View.GONE
            list.adapter = ArrayAdapter(
                this, R.layout.item_channel, R.id.channelName, parsed.map { it.name }
            )
            list.requestFocus()
        }
    }

    private fun saveAndShow(text: String) {
        listFile.writeText(text)
        applyText(text)
        Toast.makeText(this, getString(R.string.lista_cargada, channels.size), Toast.LENGTH_SHORT).show()
    }

    // ---------- Desde archivo ----------

    private fun importFromUri(uri: Uri) {
        runAsync {
            contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        }
    }

    // ---------- Desde URL ----------

    private fun askUrl() {
        val input = EditText(this).apply {
            hint = "http://servidor/lista.m3u"
            setText(prefs.getString("ultima_url", ""))
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.cargar_url)
            .setView(input)
            .setPositiveButton(R.string.cargar) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    prefs.edit().putString("ultima_url", url).apply()
                    runAsync { downloadText(url) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    // ---------- Utilidades ----------

    private fun runAsync(loader: () -> String) {
        progress.visibility = View.VISIBLE
        thread {
            val result = runCatching { loader() }
            runOnUiThread {
                progress.visibility = View.GONE
                result.onSuccess { text ->
                    if (M3uParser.parse(text).isEmpty()) {
                        Toast.makeText(this, R.string.sin_canales, Toast.LENGTH_LONG).show()
                    } else {
                        saveAndShow(text)
                    }
                }.onFailure {
                    Toast.makeText(
                        this, getString(R.string.error_lista, it.message ?: ""), Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun clearList() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirmar_borrar)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                listFile.delete()
                channels = emptyList()
                list.adapter = null
                showMessage(getString(R.string.sin_lista))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }
}
