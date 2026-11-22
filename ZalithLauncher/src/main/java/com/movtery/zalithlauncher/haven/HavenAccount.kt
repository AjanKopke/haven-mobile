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
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.account.localLogin
import com.movtery.zalithlauncher.path.PathManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/**
 * Google sign-in = the Haven account, like Haven Client desktop (main.js googleSignIn): the browser only sees
 * havenclient.net, which keeps the code under a secret state and exchanges it when the app claims it with the PKCE
 * verifier. The account plays as an offline profile with the name kept on the site, so the phone and the PC show
 * the same name.
 */
object HavenAccount {
    private const val RELAY = "https://havenclient.net/client/auth/google.php"
    private const val API = "https://havenclient.net/lol/api/companion/v1"
    private val NAME = Regex("^[A-Za-z0-9_]{3,16}$")
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var running = false

    private fun b64url(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun post(form: Map<String, String>): JSONObject {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        client.newCall(Request.Builder().url(RELAY).post(body).header("User-Agent", "HavenMobile").build()).execute().use { r ->
            return JSONObject(r.body.string())
        }
    }

    private fun sanitize(s: String?): String = Normalizer.normalize((s ?: "").replace("ı", "i").replace("İ", "I"), Normalizer.Form.NFKD)
        .replace(Regex("[^A-Za-z0-9_]"), "").take(16)

    private fun toast(context: Context, text: String) = Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()

    fun signIn(context: Context) {
        if (running) return
        running = true
        scope.launch {
            try {
                val (name, haven) = withContext(Dispatchers.IO) { claim(context) }
                AccountsManager.accountsFlow.value.firstOrNull { it.username == name && it.isLocalAccount() }
                    ?.let { AccountsManager.setCurrentAccount(it) } ?: localLogin(name, null)
                toast(context, if (haven.isNotEmpty()) "Haven hesabın: $haven · oyundaki adın: $name" else "Google ile girildi: $name")
            } catch (e: Exception) {
                toast(context, e.message ?: "Google girişi başarısız")
            } finally {
                running = false
            }
        }
    }

    private suspend fun claim(context: Context): Pair<String, String> {
        val rnd = SecureRandom()
        val verifier = b64url(ByteArray(32).also { rnd.nextBytes(it) })
        val state = b64url(ByteArray(32).also { rnd.nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        withContext(Dispatchers.Main) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$RELAY?go=1&s=$state&c=$challenge")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val end = System.currentTimeMillis() + 5 * 60 * 1000
        var misses = 0
        while (true) {
            delay(1500)
            if (System.currentTimeMillis() > end) error("Google girişi zaman aşımına uğradı, tekrar dene")
            val r = try {
                post(mapOf("action" to "claim", "state" to state, "verifier" to verifier)).also { misses = 0 }
            } catch (_: Exception) {
                if (++misses >= 8) error("Haven sunucusuna ulaşılamadı, internet bağlantını kontrol et")
                continue
            }
            if (r.optBoolean("pending")) continue
            if (r.optBoolean("ok") && r.has("sub")) return finish(r)
            when (r.optString("error")) {
                "access_denied" -> error("Google girişi tarayıcıda iptal edildi")
                "not_configured" -> error("Google girişi şu an kullanılamıyor, biraz sonra tekrar dene")
                "expired" -> error("Google girişinin süresi doldu, tekrar dene")
                else -> error("Google girişi başarısız (${r.optString("detail").ifEmpty { r.optString("error", "bilinmeyen hata") }})")
            }
        }
    }

    /**
     * The Haven account of this Google account (the site picks the one with the same Gmail, e.g. made on the site)
     * and the in-game name the site keeps for it; a first sign-in names the player like that Haven account, else
     * from the Google profile, and saves it.
     */
    private fun finish(r: JSONObject): Pair<String, String> {
        val token = r.optString("token")
        var haven = ""
        var made = false
        // same call the desktop makes; the token is kept for capes, friends and the shop
        if (token.isNotEmpty()) runCatching {
            val body = JSONObject().put("token", token).put("device", "Haven Mobile").toString().toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url("$API/auth/google").post(body).header("User-Agent", "HavenMobile").build()).execute().use { res ->
                val o = JSONObject(res.body.string())
                if (o.optBoolean("ok")) {
                    File(PathManager.DIR_FILES_PRIVATE, ".haven-token").writeText(o.optString("token"))
                    haven = o.optJSONObject("user")?.optString("display_name").orEmpty()
                    made = o.optBoolean("created")
                }
            }
        }
        val nick = r.optString("nick")
        val name = if (NAME.matches(nick)) nick else {
            // an existing Haven account names the player; a brand-new one would only repeat the email's first part
            var n = if (made) "" else sanitize(haven)
            if (n.length < 3) n = sanitize(r.optString("given_name").ifEmpty { r.optString("name") })
            if (n.length < 3) n = sanitize(r.optString("email").substringBefore('@'))
            if (n.length < 3) n = "Oyuncu" + r.optString("sub").takeLast(4)
            if (token.isNotEmpty()) runCatching { post(mapOf("action" to "setname", "token" to token, "name" to n)) }
            n
        }
        return name to haven
    }
}
