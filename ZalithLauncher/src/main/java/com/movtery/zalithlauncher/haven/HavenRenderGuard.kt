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
import android.os.Build
import com.movtery.zalithlauncher.context.GlobalContext
import java.io.File

/**
 * MobileGlues is picked by itself on GLES 3.2 phones, but older drivers (Samsung Mali on Android 11, A30s/A50) freeze
 * on the loading screen or crash with it. Android 11 and older keep the old default, and a phone where an automatic
 * MobileGlues start never got past loading falls back to it for good. Only the automatic pick: a renderer the player
 * chose is never touched.
 */
object HavenRenderGuard {
    /** lines the game logs once it has finished loading textures, so the renderer worked */
    private val LOADED = Regex("-atlas|Sound engine started")

    private fun prefs() = GlobalContext.getSharedPreferences("haven", Context.MODE_PRIVATE)

    /** May MobileGlues be the automatic renderer here? Also settles the result of the last automatic try. */
    fun mobileGluesAllowed(): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) return false
        val p = prefs()
        p.getString("mg_try", null)?.let { marker ->
            val log = File(marker.substringBeforeLast('|'))
            val since = marker.substringAfterLast('|').toLongOrNull() ?: 0L
            val ok = log.lastModified() >= since && runCatching { LOADED.containsMatchIn(log.readText()) }.getOrDefault(false)
            p.edit().remove("mg_try").putBoolean(if (ok) "mg_ok" else "mg_failed", true).apply()
        }
        return !p.getBoolean("mg_failed", false)
    }

    /** An automatic MobileGlues start is about to run: check on the next launch whether it loaded. */
    fun trying(gameDir: File) {
        val p = prefs()
        if (p.getBoolean("mg_ok", false)) return
        p.edit().putString("mg_try", "${File(gameDir, "logs/latest.log").absolutePath}|${System.currentTimeMillis()}").apply()
    }
}
