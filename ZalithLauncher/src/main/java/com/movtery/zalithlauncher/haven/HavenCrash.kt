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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Crashes go to havenclient.net (no account, no personal data: the stack trace or the end of the game log, the app
 * version, Android version and phone model), so a crash on a player's phone can be fixed instead of guessed. The site
 * keeps the same crash once a day.
 */
object HavenCrash {
    private const val URL = "https://havenclient.net/lol/api/public/crash"
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val CAUSE = Regex("(Exception|Error|FATAL|SIGSEGV|SIGABRT|Caused by|Could not reserve|Out of memory|OutOfMemory)")
    /** lines every game prints that never cause a crash (offline accounts can't reach Realms) */
    private val HARMLESS = Regex("(Realms|SignedJWT|authorize you|Failed to fetch user properties|Narrator)", RegexOption.IGNORE_CASE)
    /** "JVM ölümcül sinyal 6" and friends: the game died in native code, the Java log can't name a cause */
    private val NATIVE = Regex("(sinyal|signal)", RegexOption.IGNORE_CASE)

    /** Launcher crash: the stack trace as it is. */
    fun launcher(context: Context, trace: String) = send(context, trace, "Başlatıcı")

    /**
     * Game crash: the end of the log, led by the first line that names a cause so the site can tell crashes apart.
     * A native signal leads with itself; "Could not create the Java Virtual Machine" keeps the JVM's reason with it.
     */
    fun game(context: Context, log: File, exitMessage: String) {
        val tail = runCatching { log.readText().takeLast(7000) }.getOrDefault("")
        val lines = tail.lines()
        val cause = if (NATIVE.containsMatchIn(exitMessage)) null
            else lines.firstOrNull { CAUSE.containsMatchIn(it) && !HARMLESS.containsMatchIn(it) }?.trim()?.take(200)
        val jvm = lines.indexOfFirst { it.contains("Could not create the Java Virtual Machine") }
            .takeIf { it >= 0 }?.let { i -> lines.subList(maxOf(0, i - 4), i + 1).joinToString("\n") }
        // the log tail only holds the end of Minecraft's crash report (system details); its head names the crash
        val report = reportHead(tail)
        val reportCause = report?.lineSequence()?.firstOrNull { CAUSE.containsMatchIn(it) && !HARMLESS.containsMatchIn(it) && !it.contains("~~ERROR~~") }?.trim()?.take(200)
        val lead = reportCause ?: cause ?: exitMessage
        val head = listOfNotNull(lead, if (lead != exitMessage) exitMessage else null, setup(context), jvm).joinToString("\n")
        val body = listOfNotNull(report?.let { "--- Çökme raporu ---\n$it" }, "--- Oyun günlüğünün sonu ---\n$tail").joinToString("\n\n")
        send(context, "$head\n\n$body".take(8000), "Oyun")
    }

    /** First lines of the crash report or hs_err file the log points to (description, exception, stack). */
    private fun reportHead(tail: String): String? {
        val path = Regex("Crash report saved to:\\s*(?:#@!@#\\s*)?(\\S.*?\\.txt)").find(tail)?.groupValues?.get(1)
            ?: Regex("(/\\S*hs_err_pid\\d+\\.log)").find(tail)?.groupValues?.get(1)
            ?: return null
        return runCatching {
            File(path.trim()).bufferedReader().use { r -> r.lineSequence().filter { it.isNotBlank() }.take(60).joinToString("\n") }
        }.getOrNull()?.take(3500)
    }

    /** What the game ran with: architecture, phone RAM, heap, Java (no personal data). */
    private fun setup(context: Context): String = runCatching {
        val ram = com.movtery.zalithlauncher.utils.platform.getTotalMemory(context) / 1048576
        val arch = if (com.movtery.zalithlauncher.utils.device.Architecture.is32BitsDevice) "32-bit" else "64-bit"
        val heap = com.movtery.zalithlauncher.setting.GameSettings.ramAllocation.getValue() ?: 0
        val java = com.movtery.zalithlauncher.setting.GameSettings.javaRuntime.getValue().ifEmpty { "otomatik" }
        "Cihaz: $arch · RAM ${ram} MB · oyuna ayrılan ${heap} MB · Java $java"
    }.getOrDefault("")

    private fun send(context: Context, trace: String, where: String) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val body = JSONObject()
            .put("app", "Haven Mobile")
            .put("version", version)
            .put("android", Build.VERSION.RELEASE)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("where", where)
            .put("trace", trace.take(8000))
            .toString()
        Thread {
            runCatching {
                client.newCall(
                    Request.Builder().url(URL).post(body.toRequestBody("application/json".toMediaType()))
                        .header("User-Agent", "HavenMobile").build()
                ).execute().close()
            }
        }.start()
    }
}
