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

import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** What the host's Gate answers to "manifest": the game the friend needs to join. */
data class HavenManifest(val version: String, val loader: String, val loaderVersion: String?, val mods: List<HavenModFile>) {
    /** Same as the desktop's mirror key: one profile per host setup, reused while nothing changes. */
    val key: String by lazy {
        val text = "$version|$loader|${loaderVersion.orEmpty()}|" + mods.map { it.sha1 }.sorted().joinToString(",")
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
    }
}

/** One of the host's jars as the host's Gate lists it. */
data class HavenModFile(val name: String, val sha1: String, val size: Long)

/** How the phone talks to the host: straight to one of its addresses, or through a local Cloudflare bridge. */
sealed interface HavenRoute {
    val host: String
    val port: Int
    data class Direct(val endpoint: HavenEndpoint) : HavenRoute {
        override val host get() = endpoint.host
        override val port get() = endpoint.port
    }
    data class Tunnel(val bridge: CfBridge) : HavenRoute {
        override val host get() = "127.0.0.1"
        override val port get() = bridge.port
    }
}

/** Where a join stands, for the Multiplayer screen. */
sealed interface HavenJoinResult {
    /** A profile for this host already exists and now points at the new invite. */
    data class Ready(val versionName: String) : HavenJoinResult
    /** A Modrinth pack with the host's game and mods, to go through the normal modpack import. */
    data class Import(val pack: File, val name: String) : HavenJoinResult
}

/**
 * Haven Play on the phone (joining a friend's world shared from Haven Client desktop). The host's Gate speaks
 * "HAVENPLAY1 manifest" / "HAVENPLAY1 file <sha1>" on the same port as the game, like haven-play.js.
 */
object HavenPlay {
    private const val TAG = "HavenPlay"
    private const val MAGIC = "HAVENPLAY1 "
    const val JOIN_FILE = "havenplay.json"

    private fun ask(host: String, port: Int, line: String, timeoutMs: Int): Socket {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), timeoutMs)
        s.soTimeout = 30_000
        s.getOutputStream().apply { write((MAGIC + line + "\n").toByteArray(Charsets.ISO_8859_1)); flush() }
        return s
    }

    private fun fetchManifest(host: String, port: Int, timeoutMs: Int): HavenManifest? = runCatching {
        ask(host, port, "manifest", timeoutMs).use { s ->
            s.soTimeout = timeoutMs.coerceAtLeast(5_000)
            val o = JSONObject(s.getInputStream().readBytes().toString(Charsets.UTF_8))
            val mods = o.optJSONArray("mods") ?: JSONArray()
            HavenManifest(
                version = o.getString("version"),
                loader = o.optString("loader", "vanilla"),
                loaderVersion = o.optString("loaderVersion").takeIf { it.isNotBlank() && it != "null" },
                mods = (0 until mods.length()).map { i ->
                    val m = mods.getJSONObject(i)
                    HavenModFile(m.getString("name"), m.getString("sha1"), m.optLong("size"))
                }
            )
        }
    }.getOrNull()

    /** First direct address that answers within 3 s, else the tunnel (a fresh share needs a few seconds there). */
    suspend fun reach(invite: HavenInvite): Pair<HavenRoute, HavenManifest?> = withContext(Dispatchers.IO) {
        if (invite.direct.isNotEmpty()) {
            val hit = coroutineScope {
                invite.direct.map { d -> async { fetchManifest(d.host, d.port, 3000)?.let { d to it } } }.awaitAll()
            }.firstOrNull { it != null }
            if (hit != null) return@withContext HavenRoute.Direct(hit.first) to hit.second
        }
        val sub = invite.sub ?: error("Arkadaşının dünyasına doğrudan ulaşılamadı. Paylaşımın hâlâ açık olduğunu sor ve tekrar dene")
        val bridge = CfBridge(sub)
        repeat(8) {
            fetchManifest("127.0.0.1", bridge.port, 10_000)?.let { return@withContext HavenRoute.Tunnel(bridge) to it }
            // hosts on Haven Client 1.3.3 have no Gate: a status ping tells the world is there anyway
            if (pingWorld("127.0.0.1", bridge.port, 4000)) return@withContext HavenRoute.Tunnel(bridge) to null
            delay(2500)
        }
        bridge.close()
        error("Arkadaşının dünyasına ulaşılamadı. Paylaşımın hâlâ açık olduğunu sor ve tekrar dene")
    }

    /** A Minecraft status request; true when anything answers. */
    fun pingWorld(host: String, port: Int, timeoutMs: Int): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            fun varint(v: Int): ByteArray {
                val out = ByteArrayOutputStream()
                var n = v
                do {
                    var b = n and 0x7f
                    n = n ushr 7
                    if (n != 0) b = b or 0x80
                    out.write(b)
                } while (n != 0)
                return out.toByteArray()
            }
            fun packet(vararg parts: ByteArray): ByteArray {
                val body = parts.fold(ByteArray(0)) { a, b -> a + b }
                return varint(body.size) + body
            }
            val h = host.toByteArray()
            val handshake = packet(varint(0), varint(-1), varint(h.size), h, byteArrayOf((port shr 8).toByte(), port.toByte()), varint(1))
            s.getOutputStream().write(handshake + packet(varint(0)))
            s.getInputStream().read() >= 0
        }
    }.getOrDefault(false)

    private fun downloadMod(host: String, port: Int, mod: HavenModFile, out: java.io.OutputStream, onData: (Int) -> Unit) {
        ask(host, port, "file ${mod.sha1}", 10_000).use { s ->
            val md = MessageDigest.getInstance("SHA-1")
            val input: InputStream = s.getInputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                out.write(buf, 0, n)
                onData(n)
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (got != mod.sha1) error("Mod dosyası eksik geldi: ${mod.name}")
        }
    }

    private fun joinJson(manifest: HavenManifest?, invite: HavenInvite, name: String?): String = JSONObject().apply {
        put("key", manifest?.key ?: "")
        put("sub", invite.sub ?: JSONObject.NULL)
        put("direct", JSONArray().apply {
            invite.direct.forEach { put(JSONObject().put("host", it.host).put("port", it.port).put("kind", it.kind)) }
        })
        put("name", name ?: "")
        put("join", true)
        put("at", System.currentTimeMillis())
    }.toString(2)

    private fun profileFor(key: String): Version? = VersionsManager.versions.value.firstOrNull { v ->
        runCatching { JSONObject(File(v.getGameDir(), JOIN_FILE).readText()).optString("key") == key }.getOrDefault(false)
    }

    /**
     * Joins from an invite code: reuses the profile made for this host's setup (only the address is updated), or
     * builds a .mrpack with the host's version, loader and jars so the normal modpack import installs it.
     */
    suspend fun join(code: String, friend: String?, onStatus: (String) -> Unit): HavenJoinResult = withContext(Dispatchers.IO) {
        val invite = HavenInviteParser.parse(code) ?: error("Bu bir Haven davet kodu değil")
        onStatus("Arkadaşının dünyası aranıyor…")
        val (route, found) = reach(invite)
        try {
            val manifest = found ?: HavenManifest(invite.version ?: error("Davet kodunda sürüm yok"), "vanilla", null, emptyList())
            profileFor(manifest.key)?.let { v ->
                File(v.getGameDir(), JOIN_FILE).writeText(joinJson(manifest, invite, friend))
                VersionsManager.saveCurrentVersion(v.getVersionName())
                return@withContext HavenJoinResult.Ready(v.getVersionName())
            }
            val total = manifest.mods.sumOf { it.size }.coerceAtLeast(1)
            var got = 0L
            val name = "Haven Play ${manifest.version}" + if (manifest.mods.isNotEmpty()) " ${manifest.key.take(4)}" else ""
            val pack = File(PathManager.DIR_CACHE, "haven-play-${manifest.key}.mrpack")
            ZipOutputStream(pack.outputStream().buffered()).use { zip ->
                val deps = JSONObject().put("minecraft", manifest.version)
                when (manifest.loader) {
                    "fabric" -> deps.put("fabric-loader", manifest.loaderVersion ?: "")
                    "quilt" -> deps.put("quilt-loader", manifest.loaderVersion ?: "")
                    "forge" -> deps.put("forge", manifest.loaderVersion ?: "")
                    "neoforge" -> deps.put("neoforge", manifest.loaderVersion ?: "")
                }
                // no loader version from an old host: any Fabric is fine, the importer picks the newest
                if (deps.optString("fabric-loader") == "") deps.remove("fabric-loader")
                val index = JSONObject()
                    .put("game", "minecraft").put("formatVersion", 1).put("versionId", manifest.key)
                    .put("name", name).put("summary", "${friend ?: "Arkadaşının"} dünyası (Haven Play)")
                    .put("files", JSONArray()).put("dependencies", deps)
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write(index.toString(2).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("overrides/$JOIN_FILE"))
                zip.write(joinJson(manifest, invite, friend).toByteArray())
                zip.closeEntry()
                manifest.mods.forEachIndexed { i, mod ->
                    onStatus("Modlar alınıyor ${i + 1}/${manifest.mods.size}: ${mod.name}")
                    zip.putNextEntry(ZipEntry("overrides/mods/${mod.name.substringAfterLast('/')}"))
                    downloadMod(route.host, route.port, mod, zip) { got += it }
                    zip.closeEntry()
                }
            }
            Logger.info(TAG, "Pack for ${manifest.version} ${manifest.loader}: ${manifest.mods.size} mods, $got/$total bytes")
            HavenJoinResult.Import(pack, name)
        } finally {
            (route as? HavenRoute.Tunnel)?.bridge?.close()
        }
    }

    private var launchBridge: CfBridge? = null

    /**
     * Runs in the game process right before the JVM starts: when the profile was set up by a join that hasn't been
     * used yet, finds the way to the host again (direct address or a bridge that lives as long as the game) and
     * returns the address for the game's auto-join. The join flag is cleared so a later plain start stays plain.
     */
    fun launchAddress(gameDir: File): String? {
        val file = File(gameDir, JOIN_FILE)
        if (!file.exists()) return null
        val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        if (!o.optBoolean("join")) return null
        o.put("join", false)
        runCatching { file.writeText(o.toString(2)) }
        val direct = o.optJSONArray("direct") ?: JSONArray()
        for (i in 0 until direct.length()) {
            val d = direct.getJSONObject(i)
            val e = HavenEndpoint(d.getString("host"), d.getInt("port"), d.optString("kind"))
            if (pingWorld(e.host, e.port, 2000)) return e.address
        }
        val sub = o.optString("sub").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return runCatching {
            launchBridge?.close()
            val bridge = CfBridge(sub)
            launchBridge = bridge
            "127.0.0.1:${bridge.port}"
        }.onFailure { Logger.warning(TAG, "No bridge to $sub", it) }.getOrNull()
    }
}
