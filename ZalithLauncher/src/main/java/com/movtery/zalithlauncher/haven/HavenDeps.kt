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
import com.movtery.zalithlauncher.utils.logging.Logger
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Library mods a Fabric mod needs but the player didn't add ("requires fabric-language-kotlin, which is missing"):
 * before each start, the ones on this list come from Modrinth for the profile's Minecraft version.
 */
object HavenDeps {
    private const val TAG = "HavenDeps"

    /** mod id that mods depend on → Modrinth project (shortcut: only the common libraries; add more when players hit them) */
    private val KNOWN = mapOf(
        "fabric-language-kotlin" to "fabric-language-kotlin",
        "fabric-api" to "fabric-api",
        "fabric" to "fabric-api",
        "cloth-config" to "cloth-config",
        "cloth-config2" to "cloth-config",
        "architectury" to "architectury-api",
        "yet_another_config_lib_v3" to "yacl",
        "owo" to "owo-lib",
        "geckolib" to "geckolib",
        "modmenu" to "modmenu",
    )

    private fun json(input: InputStream): JSONObject? = runCatching { JSONObject(input.reader().readText()) }.getOrNull()

    /** The ids a fabric.mod.json gives (its own, "provides", and those of the jars nested inside its jar). */
    private fun ids(meta: JSONObject, into: MutableSet<String>) {
        into += meta.optString("id")
        meta.optJSONArray("provides")?.let { p -> for (i in 0 until p.length()) into += p.optString(i) }
    }

    private fun nestedIds(zip: ZipFile, path: String, into: MutableSet<String>) {
        val entry = zip.getEntry(path) ?: return
        ZipInputStream(zip.getInputStream(entry)).use { inner ->
            while (true) {
                val e = inner.nextEntry ?: break
                if (e.name == "fabric.mod.json") { json(inner)?.let { ids(it, into) }; break }
            }
        }
    }

    /** ids present in the mods folder and ids the mods ask for */
    internal fun scan(modsDir: File): Pair<Set<String>, Set<String>> {
        val have = mutableSetOf<String>()
        val want = mutableSetOf<String>()
        modsDir.listFiles { f -> f.name.endsWith(".jar") }.orEmpty().forEach { jar ->
            runCatching {
                ZipFile(jar).use { zip ->
                    val meta = zip.getEntry("fabric.mod.json")?.let { json(zip.getInputStream(it)) } ?: return@use
                    ids(meta, have)
                    meta.optJSONObject("depends")?.keys()?.forEach { want += it }
                    meta.optJSONArray("jars")?.let { jars ->
                        for (i in 0 until jars.length()) jars.optJSONObject(i)?.optString("file")?.let { nestedIds(zip, it, have) }
                    }
                }
            }
        }
        return have to want
    }

    /** Called from the game launch (off the main thread), after [HavenPerformance.ensure]. */
    fun ensure(version: Version) {
        val info = version.getVersionInfo() ?: return
        if (!info.hasLoader(ModLoader.FABRIC)) return
        val modsDir = File(version.getGameDir(), "mods")
        if (!modsDir.isDirectory) return
        // a library can need another one (Architectury → Fabric API): a few rounds at most
        repeat(3) {
            val (have, want) = scan(modsDir)
            val missing = want.filter { it !in have }.mapNotNull { KNOWN[it] }.distinct()
            if (missing.isEmpty()) return
            var added = false
            for (project in missing) {
                val v = HavenPerformance.versionFor(project, info.minecraftVersion) ?: continue
                if (HavenPerformance.download(v, modsDir)) {
                    added = true
                    Logger.info(TAG, "added $project ${v.optString("version_number")} for ${info.minecraftVersion}")
                }
            }
            if (!added) return
        }
    }
}
