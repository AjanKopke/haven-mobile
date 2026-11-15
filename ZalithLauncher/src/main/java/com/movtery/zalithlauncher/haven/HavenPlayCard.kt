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

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.ui.activities.MainActivity
import kotlinx.coroutines.launch

/**
 * Haven Play on the Multiplayer screen: paste the code a friend shares from Haven Client desktop and join. The
 * first join installs the friend's version, loader and mods as a profile; later joins reuse it.
 */
@Composable
fun HavenPlayCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Haven Play", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Arkadaşın Haven Client'ta dünyasını paylaştığında verdiği kodu yapıştır. Sürüm ve modlar kendiliğinden kurulur, oyun açılınca doğrudan dünyasına girersin.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = code,
                onValueChange = { code = it.trim() },
                singleLine = true,
                enabled = !busy,
                label = { Text("Davet kodu") },
                shape = MaterialTheme.shapes.large
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy,
                    onClick = { clipboard.getText()?.text?.trim()?.let { code = it } }
                ) { Text("Yapıştır") }
                Button(
                    enabled = !busy && code.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                when (val r = HavenPlay.join(code, null) { status = it }) {
                                    is HavenJoinResult.Ready -> status = "Hazır: ${r.versionName}. Başlat'a bas, arkadaşının dünyasına girersin."
                                    is HavenJoinResult.Import -> {
                                        status = "Kuruluyor: ${r.name}. Bitince Başlat'a bas."
                                        (context as? MainActivity)?.modpackImportViewModel?.import(context, Uri.fromFile(r.pack))
                                    }
                                }
                            } catch (e: Exception) {
                                status = e.message ?: "Katılınamadı"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Bağlanıyor…" else "Katıl") }
            }
            status?.let { Text(text = it, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
