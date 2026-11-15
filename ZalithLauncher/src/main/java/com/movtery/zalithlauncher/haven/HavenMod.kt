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

import android.content.Context
import com.movtery.zalithlauncher.game.addons.modloader.ModLoader
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.security.MessageDigest

/**
 * The Haven mod (capes, titles, the Haven menu) ships inside the APK, one jar per Minecraft version, the same jars
 * Haven Client desktop uses. Like on desktop it is not put in the profile's mods folder: Fabric loads it from the
 * app's private folder through -Dfabric.addMods, so it never shows up in the mod list or gets copied by exports.
 */
object HavenMod {
    private const val TAG = "HavenMod"
    private const val ASSET_DIR = "haven-mod"

    /** The jar for this profile, extracted to private storage, or null when the profile isn't Fabric or no jar fits. */
    fun jarFor(context: Context, version: Version): File? {
        val info = version.getVersionInfo() ?: return null
        if (!info.hasLoader(ModLoader.FABRIC)) return null
        val name = "haven-client-mod-${info.minecraftVersion}.jar"
        val assets = context.assets.list(ASSET_DIR) ?: return null
        if (name !in assets) return null
        return runCatching {
            val bytes = context.assets.open("$ASSET_DIR/$name").use { it.readBytes() }
            val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
            val dir = File(context.filesDir, ".hv").apply { mkdirs() }
            val jar = File(dir, "hc-$sha1.jar")
            if (!jar.exists() || jar.length() != bytes.size.toLong()) {
                dir.listFiles()?.filter { it.name.startsWith("hc-") && it.name != jar.name }?.forEach { it.delete() }
                val tmp = File(dir, jar.name + ".part")
                tmp.writeBytes(bytes)
                tmp.renameTo(jar)
            }
            jar
        }.onFailure { Logger.warning(TAG, "Could not prepare the Haven mod", it) }.getOrNull()
    }

    /** Puts -Dfabric.addMods in front of the main class so Fabric sees it as a JVM option. */
    fun addTo(context: Context, version: Version, args: MutableList<String>) {
        val jar = jarFor(context, version) ?: return
        val at = args.indexOf("mio.Wrapper").takeIf { it >= 0 } ?: return
        val existing = args.indexOfFirst { it.startsWith("-Dfabric.addMods=") }
        if (existing in 0 until at) {
            args[existing] = args[existing] + File.pathSeparator + jar.absolutePath
        } else {
            args.add(at, "-Dfabric.addMods=${jar.absolutePath}")
        }
        Logger.info(TAG, "Haven mod: ${jar.name}")
    }
}
