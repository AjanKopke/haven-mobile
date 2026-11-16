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
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.activities.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The simple home, like Haven Client desktop's simple mode: three numbered steps (account, Minecraft, play) and
 * joining a friend, in words a 6–13 year old reads without help. Zalith's full home stays behind the
 * "Detaylı görünüm" switch.
 */
@Composable
fun HavenSimpleHome(
    onAccounts: () -> Unit,
    onVersions: () -> Unit,
    onAddMods: () -> Unit,
    onLaunchGame: (Version?) -> Unit,
    onDetailed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    val hasAccount = account != null
    val hasGame = version?.isValid() == true
    var installing by remember { mutableStateOf(false) }
    var installStatus by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // the steps scroll, OYNA stays on screen: a child who doesn't think of scrolling still finds it
        Column(modifier = Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = account?.username?.let { "Merhaba, $it!" } ?: "Haven'a hoş geldin!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Step(1, "Hesabın", hasAccount,
                    if (hasAccount) "Oyundaki adın: ${account?.username}" else "Oynamak için önce giriş yap. Google hesabınla tek dokunuşta girebilirsin."
                ) {
                    if (!hasAccount) {
                        Button(onClick = { HavenAccount.signIn(context) }) { Text("Google ile gir") }
                        OutlinedButton(onClick = onAccounts) { Text("Başka yolla gir") }
                    } else {
                        TextButton(onClick = onAccounts) { Text("Değiştir") }
                    }
                }

                Step(2, "Minecraft", hasGame,
                    when {
                        hasGame -> "Hazır: ${version?.getVersionName()}"
                        installStatus != null -> installStatus!!
                        else -> "Telefonunda henüz Minecraft yok. Bir kez kurman yeter, gerisini Haven halleder."
                    }
                ) {
                    if (!hasGame) {
                        Button(
                            enabled = !installing,
                            onClick = {
                                installing = true
                                scope.launch {
                                    try {
                                        val (pack, name) = HavenQuickStart.pack(context) { installStatus = it }
                                        installStatus = "Kuruluyor: $name. Bitince OYNA'ya bas."
                                        (context as? MainActivity)?.modpackImportViewModel?.import(context, Uri.fromFile(pack))
                                    } catch (e: Exception) {
                                        installStatus = e.message ?: "Kurulamadı, internetini kontrol edip tekrar dene"
                                    } finally {
                                        installing = false
                                    }
                                }
                            }
                        ) { Text(if (installing) "Hazırlanıyor…" else "Minecraft'ı kur") }
                        TextButton(onClick = onVersions) { Text("Sürümü ben seçeyim") }
                    } else {
                        Button(onClick = onAddMods) { Text("Mod ekle") }
                        TextButton(onClick = onVersions) { Text("Sürüm") }
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = hasAccount && hasGame,
                shape = MaterialTheme.shapes.large,
                onClick = { onLaunchGame(version) }
            ) {
                Text("OYNA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            if (!hasAccount || !hasGame) {
                Text(
                    text = "OYNA düğmesi 1 ve 2 bitince açılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Arkadaşınla oyna", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Arkadaşın bilgisayarda Haven Client'ta \"Dünyamı paylaş\"a basınca sana bir kod verir. O kodu buraya yapıştır ve Katıl'a bas.",
                style = MaterialTheme.typography.bodyMedium
            )
            HavenPlayCard(modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onDetailed) { Text("Detaylı görünüme geç") }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, done: Boolean, text: String, actions: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (done) 8.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = if (done) Alignment.CenterVertically else Alignment.Top
        ) {
            Box(
                modifier = Modifier.size(34.dp).background(
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (done) "✓" else number.toString(),
                    fontWeight = FontWeight.Bold,
                    color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            // a finished step is one line (its buttons on the right), so OYNA and the next step fit on a phone
            if (done) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = text, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = text, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
                }
            }
        }
    }
}

/**
 * One-tap install like desktop's quickStart: the newest Minecraft the bundled Haven mod supports (1.21.x first, it
 * runs better on phones), Fabric and Fabric API, as a Modrinth pack for Zalith's normal import.
 */
object HavenQuickStart {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "HavenMobile (havenclient.net)").build()).execute().use { r ->
            if (r.isSuccessful) r.body.string() else null
        }
    }.getOrNull()

    private fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    private fun fabricLoader(mc: String): String? {
        val list = get("https://meta.fabricmc.net/v2/versions/loader/$mc")?.let { JSONArray(it) } ?: return null
        val all = (0 until list.length()).map { list.getJSONObject(it).getJSONObject("loader") }
        return (all.firstOrNull { it.optBoolean("stable") } ?: all.firstOrNull())?.optString("version")
    }

    private fun fabricApi(mc: String): JSONObject? {
        val url = "https://api.modrinth.com/v2/project/fabric-api/version".toHttpUrl().newBuilder()
            .addQueryParameter("loaders", "[\"fabric\"]")
            .addQueryParameter("game_versions", "[\"$mc\"]")
            .build().toString()
        val list = get(url)?.let { JSONArray(it) } ?: return null
        val v = (0 until list.length()).map { list.getJSONObject(it) }.let { l -> l.firstOrNull { it.optString("version_type") == "release" } ?: l.firstOrNull() } ?: return null
        val files = v.optJSONArray("files") ?: return null
        val f = (0 until files.length()).map { files.getJSONObject(it) }.let { l -> l.firstOrNull { it.optBoolean("primary") } ?: l.firstOrNull() } ?: return null
        val hashes = f.optJSONObject("hashes") ?: return null
        return JSONObject()
            .put("path", "mods/" + f.getString("filename"))
            .put("hashes", JSONObject().put("sha1", hashes.optString("sha1")).put("sha512", hashes.optString("sha512")))
            .put("env", JSONObject().put("client", "required").put("server", "required"))
            .put("downloads", JSONArray().put(f.getString("url")))
            .put("fileSize", f.optLong("size"))
    }

    suspend fun pack(context: Context, onStatus: (String) -> Unit): Pair<File, String> = withContext(Dispatchers.IO) {
        onStatus("En uygun Minecraft sürümü bulunuyor…")
        val bundled = context.assets.list("haven-mod").orEmpty()
            .mapNotNull { Regex("haven-client-mod-(.+)\\.jar").matchEntire(it)?.groupValues?.get(1) }
            .sortedWith { a, b -> compare(b, a) }
        val order = bundled.filter { it.startsWith("1.") } + bundled.filterNot { it.startsWith("1.") }
        for (mc in order) {
            val loader = fabricLoader(mc) ?: continue
            onStatus("Minecraft $mc hazırlanıyor…")
            val name = "Haven $mc"
            val files = JSONArray()
            fabricApi(mc)?.let { files.put(it) }
            val index = JSONObject()
                .put("game", "minecraft").put("formatVersion", 1).put("versionId", "haven-$mc")
                .put("name", name).put("summary", "Haven Mobile")
                .put("files", files)
                .put("dependencies", JSONObject().put("minecraft", mc).put("fabric-loader", loader))
            val pack = File(PathManager.DIR_CACHE, "haven-quick-$mc.mrpack")
            ZipOutputStream(pack.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write(index.toString(2).toByteArray())
                zip.closeEntry()
            }
            return@withContext pack to name
        }
        error("İnternete bağlanılamadı. Bağlantını kontrol edip tekrar dene.")
    }
}
