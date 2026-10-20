package com.tdater.blackbox.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Ping, TCP port check and DNS lookup for the "BLACKBOX Net" edition. Only that build has the INTERNET permission;
 * in the standard build these calls simply fail and the UI never shows them. Each returns plain text, ready to save
 * as a log record.
 */
object NetTools {
    private val HOST = Regex("^[A-Za-z0-9.:\\-\\[\\]%_]{1,253}$")

    fun validHost(host: String) = HOST.matches(host.trim())

    suspend fun ping(host: String, count: Int = 4): String = withContext(Dispatchers.IO) {
        val h = host.trim()
        require(validHost(h)) { "That doesn't look like a host name or IP address." }
        val header = "ping $h (${Instant.now()})"
        // The system ping binary sends real ICMP on most phones without root.
        val viaBinary = runCatching {
            val p = ProcessBuilder("/system/bin/ping", "-c", count.coerceIn(1, 10).toString(), "-W", "2", h)
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(count * 3L + 5, TimeUnit.SECONDS)) p.destroy()
            out.trim()
        }.getOrNull()
        if (!viaBinary.isNullOrBlank() && !viaBinary.contains("Permission denied", ignoreCase = true)) return@withContext "$header\n$viaBinary"
        // Fallback: time TCP connects to 443, which works even where ICMP is blocked for apps.
        val lines = (1..count).map { i ->
            val r = tcpConnect(h, 443, 2_000)
            "attempt $i: tcp/443 ${r.first}" + (r.second?.let { " in $it ms" } ?: "")
        }
        "$header\nICMP ping isn't available to apps on this phone; timed TCP connects to port 443 instead.\n" + lines.joinToString("\n")
    }

    suspend fun port(host: String, port: Int, timeoutMs: Int = 3_000): String = withContext(Dispatchers.IO) {
        val h = host.trim()
        require(validHost(h)) { "That doesn't look like a host name or IP address." }
        require(port in 1..65535) { "Port must be between 1 and 65535." }
        val (state, ms) = tcpConnect(h, port, timeoutMs)
        "tcp $h:$port (${Instant.now()})\nresult: $state" + (ms?.let { "\nconnect time: $it ms" } ?: "")
    }

    suspend fun dns(host: String): String = withContext(Dispatchers.IO) {
        val h = host.trim()
        require(validHost(h)) { "That doesn't look like a host name." }
        val start = System.nanoTime()
        val result = withTimeoutOrNull(10_000) { runCatching { InetAddress.getAllByName(h).toList() } }
        val ms = (System.nanoTime() - start) / 1_000_000
        val body = when {
            result == null -> "lookup timed out after ${ms} ms"
            result.isFailure -> when (val e = result.exceptionOrNull()) {
                is UnknownHostException -> "no such host (NXDOMAIN or no network)"
                else -> "lookup failed: ${e?.message ?: e?.javaClass?.simpleName}"
            }
            else -> result.getOrThrow().joinToString("\n") { (if (it is Inet4Address) "A     " else "AAAA  ") + it.hostAddress } +
                "\nlookup time: $ms ms"
        }
        "dns $h (${Instant.now()})\n$body"
    }

    /** Returns (state, connect time in ms when it connected). */
    private fun tcpConnect(host: String, port: Int, timeoutMs: Int): Pair<String, Long?> {
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            "OPEN" to (System.nanoTime() - start) / 1_000_000
        } catch (_: SocketTimeoutException) {
            "TIMED OUT (filtered or host down)" to null
        } catch (_: UnknownHostException) {
            "UNKNOWN HOST" to null
        } catch (e: java.net.ConnectException) {
            "CLOSED (${e.message ?: "refused"})" to null
        } catch (e: SecurityException) {
            "BLOCKED: this build has no internet permission" to null
        } catch (e: Exception) {
            "ERROR: ${e.message ?: e.javaClass.simpleName}" to null
        }
    }
}
