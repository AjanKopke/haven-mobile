/*
 * Haven Mobile, based on Zalith Launcher 2
 * Copyright (C) 2026 Haven Client
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.haven

import java.math.BigInteger

/** One way to reach the host: its LAN, public IPv4 or IPv6 address and the Gate port. */
data class HavenEndpoint(val host: String, val port: Int, val kind: String) {
    /** "host:port" the game understands; IPv6 in brackets. */
    val address: String get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"
}

/** A parsed Haven Play invite: the Cloudflare tunnel name (null for "hv"), the direct addresses and the version. */
data class HavenInvite(val sub: String?, val direct: List<HavenEndpoint>, val version: String?)

/**
 * Invite codes as Haven Client desktop writes them (haven-play.js parseInvite/decodeDirect):
 * "<tunnel>/<version>", "<tunnel>/<version>_hd…", "hv/<version>_hd…", the 1.10–1.11.0 forms
 * "<tunnel>-hd…" / "hv-hd…", and a pasted trycloudflare link.
 */
object HavenInviteParser {
    private val CODE = Regex("^[a-z0-9]+(?:-[a-z0-9]+){1,7}$")
    private val WORD = Regex("^hd[0-9a-z]{8,60}$")
    private val INVITE = Regex("^([a-z0-9-]+)(?:\\.trycloudflare\\.com)?/?([0-9a-z.\\-_]+)?$")

    /** "hd…" word → endpoints, or null when the word is not one of ours. */
    fun decodeDirect(word: String): List<HavenEndpoint>? {
        if (!WORD.matches(word)) return null
        val n = BigInteger(word.substring(2), 36)
        var hex = n.toString(16)
        if (hex.length % 2 == 1) hex = "0$hex"
        val b = IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }
        if (b.size < 5 || b[0] != 1) return null
        if ((b.dropLast(1).sum() and 255) != b.last()) return null
        val flags = b[1]
        val port = (b[2] shl 8) or b[3]
        val expected = 4 + (if (flags and 1 != 0) 4 else 0) + (if (flags and 2 != 0) 4 else 0) + (if (flags and 4 != 0) 16 else 0) + 1
        if (b.size != expected || port < 1024) return null
        var o = 4
        val out = mutableListOf<HavenEndpoint>()
        if (flags and 1 != 0) { out += HavenEndpoint(b.slice(o until o + 4).joinToString("."), port, "lan"); o += 4 }
        if (flags and 2 != 0) { out += HavenEndpoint(b.slice(o until o + 4).joinToString("."), port, "pub"); o += 4 }
        if (flags and 4 != 0) {
            val groups = (0 until 16 step 2).map { ((b[o + it] shl 8) or b[o + it + 1]).toString(16) }
            out += HavenEndpoint(groups.joinToString(":"), port, "v6")
        }
        return out
    }

    fun parse(text: String): HavenInvite? {
        val t = text.trim().lowercase().replace(Regex("^https?://"), "")
        val m = INVITE.find(t) ?: return null
        val first = m.groupValues[1]
        if (!(CODE.matches(first) || first == "hv")) return null
        var version: String? = m.groupValues[2].ifEmpty { null }
        var direct: List<HavenEndpoint>? = null
        val k = version?.lastIndexOf("_hd") ?: -1
        if (version != null && k >= 0) {
            direct = decodeDirect(version.substring(k + 1))
            if (direct != null) version = version.substring(0, k).ifEmpty { null }
        }
        val words = first.split('-').toMutableList()
        val old = if (words.size > 1) decodeDirect(words.last()) else null
        if (old != null) { words.removeAt(words.lastIndex); direct = direct ?: old }
        val sub = words.joinToString("-")
        if (if (sub == "hv") direct.isNullOrEmpty() else !CODE.matches(sub)) return null
        return HavenInvite(if (sub == "hv") null else sub, direct.orEmpty(), version)
    }
}
