package com.koalatv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Almacen de canales en memoria + listas agregadas por el usuario.
 * La numeracion empieza en 100 y sigue el orden: lista base (assets/canales.m3u8) y luego
 * las listas agregadas.
 */
object ChannelStore {

    const val FIRST_NUMBER = 100
    private const val PREFS = "koalatv"
    private const val KEY_SOURCES = "sources"

    data class Source(val name: String, val location: String, val isFile: Boolean)

    @Volatile
    var channels: List<Channel> = emptyList()
        private set

    fun isLoaded(): Boolean = channels.isNotEmpty()

    // ---------- Listas agregadas ----------

    fun sources(ctx: Context): MutableList<Source> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SOURCES, "[]") ?: "[]"
        val out = mutableListOf<Source>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Source(o.getString("name"), o.getString("loc"), o.optBoolean("file", false)))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(ctx: Context, list: List<Source>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("loc", it.location).put("file", it.isFile))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SOURCES, arr.toString()).apply()
    }

    fun addSource(ctx: Context, source: Source) {
        val list = sources(ctx)
        if (list.none { it.location == source.location }) {
            list.add(source)
            save(ctx, list)
        }
    }

    fun removeSource(ctx: Context, source: Source) {
        val list = sources(ctx)
        list.removeAll { it.location == source.location }
        save(ctx, list)
        if (source.isFile) {
            try { File(source.location).delete() } catch (_: Exception) {}
        }
    }

    // ---------- Carga ----------

    /** Debe llamarse en un hilo secundario. progress(porcentaje, texto) tambien se llama desde ese hilo. */
    fun loadAll(ctx: Context, progress: (Int, String) -> Unit) {
        progress(5, "Iniciando…")

        val raws = ArrayList<RawChannel>()

        progress(15, "Leyendo lista base…")
        try {
            val base = ctx.assets.open("canales.m3u8").bufferedReader(Charsets.UTF_8).use { it.readText() }
            raws.addAll(M3uParser.parse(base))
        } catch (_: Exception) {
        }
        progress(40, "Lista base lista")

        val extra = sources(ctx)
        extra.forEachIndexed { i, s ->
            progress(40 + (45 * i) / maxOf(1, extra.size), "Cargando ${s.name}…")
            try {
                val text = if (s.isFile) File(s.location).readText(Charsets.UTF_8) else download(s.location)
                raws.addAll(M3uParser.parse(text))
            } catch (_: Exception) {
            }
        }

        progress(90, "Numerando canales…")

        // Sin URLs repetidas; si la misma URL aparece con un nombre generico y otro real, gana el real.
        val byUrl = LinkedHashMap<String, RawChannel>()
        for (r in raws) {
            val prev = byUrl[r.url]
            if (prev == null || (M3uParser.isGeneric(prev.name) && !M3uParser.isGeneric(r.name))) {
                byUrl[r.url] = r
            }
        }
        channels = byUrl.values.mapIndexed { i, r ->
            Channel(FIRST_NUMBER + i, r.name, r.url, r.tvgId, r.group)
        }

        progress(100, "Listo")
    }

    /** Indice del canal con el que arranca la app: Caracol. */
    fun startIndex(): Int {
        val list = channels
        var i = list.indexOfFirst { it.tvgId.equals("CaracolTV.co", true) }
        if (i < 0) i = list.indexOfFirst { it.name.contains("caracol", true) }
        return if (i < 0) 0 else i
    }

    private fun download(address: String): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "KoalaTV/1.0")
        try {
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
