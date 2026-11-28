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

import com.movtery.zalithlauncher.game.addons.modloader.ModLoader
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.setting.MiscSettings
import com.movtery.zalithlauncher.utils.logging.Logger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The FPS pack: on the first start of a Fabric profile the client-side performance mods for its exact Minecraft
 * version come from Modrinth (with their required dependencies). Runs once per profile, so a mod the player removes
 * stays removed; a jar already in the folder (a friend's pack that has Sodium) is never doubled.
 */
object HavenPerformance {
    private const val TAG = "HavenPerformance"
    private const val MARKER = ".haven-perf"
    private const val API = "https://api.modrinth.com/v2"

    /**
     * Lithium (game logic), FerriteCore (RAM), Entity Culling, ModernFix (start, RAM): none of them touch the GL
     * calls. Sodium and ImmediatelyFast are left out until they are tested on the phone renderers (GL4ES and some
     * MobileGlues builds crash with them).
     */
    private val PACK = listOf("lithium", "ferrite-core", "entityculling", "modernfix")

    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "HavenMobile (havenclient.net)").build()).execute().use { r ->
            if (r.isSuccessful) r.body.string() else null
        }
    }.getOrNull()

    internal fun versionFor(project: String, mc: String): JSONObject? {
        val url = "$API/project/$project/version".toHttpUrl().newBuilder()
            .addQueryParameter("loaders", "[\"fabric\"]")
            .addQueryParameter("game_versions", "[\"$mc\"]")
            .build().toString()
        val list = get(url)?.let { JSONArray(it) } ?: return null
        // releases first; a beta only when the version has nothing else
        val all = (0 until list.length()).map { list.getJSONObject(it) }
        return all.firstOrNull { it.optString("version_type") == "release" } ?: all.firstOrNull()
    }

    private fun sha1(file: File) = MessageDigest.getInstance("SHA-1").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    internal fun download(v: JSONObject, modsDir: File): Boolean {
        val files = v.optJSONArray("files") ?: return false
        val f = (0 until files.length()).map { files.getJSONObject(it) }.let { l -> l.firstOrNull { it.optBoolean("primary") } ?: l.firstOrNull() } ?: return false
        val name = f.getString("filename")
        val target = File(modsDir, name)
        if (target.exists()) return true
        val tmp = File(modsDir, "$name.part")
        val ok = runCatching {
            client.newCall(Request.Builder().url(f.getString("url")).header("User-Agent", "HavenMobile").build()).execute().use { r ->
                if (!r.isSuccessful) return@use false
                tmp.outputStream().use { out -> r.body.byteStream().copyTo(out) }
                true
            }
        }.getOrDefault(false)
        val want = f.optJSONObject("hashes")?.optString("sha1")
        if (!ok || (want != null && want.isNotEmpty() && sha1(tmp) != want)) { tmp.delete(); return false }
        return tmp.renameTo(target)
    }

    /** Called from the game launch (game process, off the main thread). */
    fun ensure(version: Version) {
        if (!MiscSettings.havenPerformance.getValue()) return
        val info = version.getVersionInfo() ?: return
        if (!info.hasLoader(ModLoader.FABRIC)) return
        val gameDir = version.getGameDir()
        val marker = File(gameDir, MARKER)
        if (marker.exists()) return
        val modsDir = File(gameDir, "mods").apply { mkdirs() }
        val present = modsDir.list().orEmpty().map { it.lowercase().replace("-", "").replace("_", "") }
        // Embeddium/Rubidium and friends are Sodium too; never two renderers
        val hasRenderer = present.any { it.contains("sodium") || it.contains("embeddium") || it.contains("rubidium") || it.contains("optifine") || it.contains("iris") }
        val mc = info.minecraftVersion
        val done = mutableSetOf<String>()
        var reached = false
        fun install(project: String, depth: Int) {
            if (!done.add(project) || depth > 3) return
            // dependencies come as ids ("P7dR8mSH"); the slug ("fabric-api") is what the jar names start with
            val slug = if (depth == 0) project else get("$API/project/$project")?.let { JSONObject(it).optString("slug") } ?: return
            val key = slug.lowercase().replace("-", "").replace("_", "")
            if (present.any { it.startsWith(key) }) return
            val v = versionFor(project, mc) ?: return
            reached = true
            if (!download(v, modsDir)) return
            Logger.info(TAG, "$project ${v.optString("version_number")} for $mc")
            val deps = v.optJSONArray("dependencies") ?: return
            for (i in 0 until deps.length()) {
                val d = deps.getJSONObject(i)
                if (d.optString("dependency_type") == "required") d.optString("project_id").takeIf { it.isNotBlank() }?.let { install(it, depth + 1) }
            }
        }
        PACK.filter { !(hasRenderer && it == "sodium") }.forEach { runCatching { install(it, 0) } }
        // offline or Modrinth down: try again next start instead of never
        if (reached) marker.writeText(System.currentTimeMillis().toString())
    }
}
