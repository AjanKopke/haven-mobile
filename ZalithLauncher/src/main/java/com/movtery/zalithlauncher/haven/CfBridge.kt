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

import com.movtery.zalithlauncher.utils.logging.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * What `cloudflared access tcp` does on desktop, without the binary: a quick tunnel started with
 * `--url tcp://…` takes a plain WebSocket at wss://<name>.trycloudflare.com and carries the TCP bytes as binary
 * frames. Each local connection gets its own WebSocket.
 */
class CfBridge(private val sub: String) {
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    @Volatile private var closed = false

    init {
        thread(name = "HavenBridge-$sub", isDaemon = true) {
            while (!closed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                runCatching { pipe(sock) }.onFailure { Logger.warning(TAG, "Bridge connection failed", it); sock.close() }
            }
        }
    }

    private fun pipe(sock: Socket) {
        sock.tcpNoDelay = true
        val out = sock.getOutputStream()
        val request = Request.Builder().url("https://$sub.trycloudflare.com").header("User-Agent", "HavenMobile").build()
        val ws = CLIENT.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                thread(name = "HavenBridge-up", isDaemon = true) {
                    val buf = ByteArray(32 * 1024)
                    val input = sock.getInputStream()
                    try {
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            webSocket.send(buf.toByteString(0, n))
                        }
                    } catch (_: Exception) {
                    } finally {
                        webSocket.close(1000, null)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    out.write(bytes.toByteArray())
                    out.flush()
                } catch (_: Exception) {
                    webSocket.cancel()
                    sock.close()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                runCatching { sock.close() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runCatching { sock.close() }
            }
        })
        // the game hanging up closes the socket; the up thread then closes the WebSocket
        if (sock.isClosed) ws.cancel()
    }

    fun close() {
        closed = true
        runCatching { server.close() }
    }

    companion object {
        private const val TAG = "HavenBridge"
        private val CLIENT = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
