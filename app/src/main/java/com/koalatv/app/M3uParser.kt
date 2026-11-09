package com.koalatv.app

import android.net.Uri

data class RawChannel(
    val name: String,
    val url: String,
    val tvgId: String?,
    val group: String?
)

data class Channel(
    val number: Int,
    val name: String,
    val url: String,
    val tvgId: String?,
    val group: String?
)

/** Lector de listas M3U / M3U8 (formato #EXTINF). */
object M3uParser {

    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String): List<RawChannel> {
        val out = ArrayList<RawChannel>()
        var name: String? = null
        var tvgId: String? = null
        var group: String? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim().trimStart('﻿')
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val comma = line.lastIndexOf(',')
                name = if (comma >= 0) line.substring(comma + 1).trim() else null
                val attrs = attrRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                if (name.isNullOrBlank()) name = attrs["tvg-name"]
            } else if (line.startsWith("#")) {
                continue
            } else if (line.startsWith("http://", true) || line.startsWith("https://", true)) {
                val host = try { Uri.parse(line).host ?: "canal" } catch (e: Exception) { "canal" }
                var finalName = cleanName(name, host)
                out.add(RawChannel(finalName, line, tvgId, group))
                name = null
                tvgId = null
                group = null
            }
        }
        return out
    }

    /** Nombres genericos (index.m3u8, playlist.m3u8) se marcan para poder reemplazarlos si hay duplicados. */
    fun isGeneric(name: String): Boolean =
        name.endsWith(".m3u8", ignoreCase = true) || name.startsWith("Señal ")

    private fun cleanName(raw: String?, host: String): String {
        val n = raw?.replace('_', ' ')?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (n.isEmpty() || n.endsWith(".m3u8", ignoreCase = true)) return "Señal $host"
        return n
    }
}
