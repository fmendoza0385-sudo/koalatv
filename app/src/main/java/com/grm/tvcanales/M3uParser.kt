package com.grm.tvcanales

data class Channel(val name: String, val url: String, val group: String = "")

object M3uParser {

    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingGroup = ""

        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingName = line.substringAfterLast(",", "").trim()
                    pendingGroup = Regex("group-title=\"([^\"]*)\"")
                        .find(line)?.groupValues?.get(1).orEmpty()
                }
                line.startsWith("#") -> Unit
                else -> {
                    val name = pendingName?.takeIf { it.isNotEmpty() } ?: line
                    channels.add(Channel(name, line, pendingGroup))
                    pendingName = null
                    pendingGroup = ""
                }
            }
        }
        return channels
    }
}
