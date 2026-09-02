package com.smsforwarder.gateway.data.perf

import android.content.SharedPreferences
import android.util.Log
import android.view.Window
import androidx.metrics.performance.JankStats
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-demand performance instrumentation (spec 0022) - gated entirely by
 * GatewayConfigStore.isDiagnosticsEnabled(), off by default. Two independent
 * paths: JankStats for Compose-screen frame jank (attachTo), and measure()
 * for background-operation timing (history import, webhook delivery). Both
 * log to logcat and to a file under filesDir/perf so before/after numbers
 * survive closing the logcat session.
 */
@Singleton
open class PerfMonitor @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val configStore: GatewayConfigStore,
) {
    private var jankStats: JankStats? = null
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    // JankStats' frame-jank callback runs on the calling thread (main, in practice) -
    // file I/O there would itself add jank, so writes are dispatched off-thread.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Dispatchers.IO is a thread pool, not a single thread - two log() calls close
    // together (a jank frame and a measure() timer) could otherwise open concurrent
    // FileOutputStreams on the same file and interleave/corrupt lines.
    private val fileWriteMutex = Mutex()

    /** Idempotent - a second call on the same Window is a no-op, since the listener is only wired once per window/JankStats instance. */
    open fun attachTo(window: Window) {
        if (jankStats != null) return
        val stats = JankStats.createAndTrack(window) { frameData ->
            if (frameData.isJank) {
                log("jank frame_ns=${frameData.frameDurationUiNanos} states=${frameData.states.joinToString { it.key + "=" + it.value }}")
            }
        }
        stats.isTrackingEnabled = configStore.isDiagnosticsEnabled()
        jankStats = stats
        prefsListener = configStore.addOnDiagnosticsEnabledChangeListener { enabled -> stats.isTrackingEnabled = enabled }
    }

    open fun detach() {
        prefsListener?.let { configStore.removeOnDiagnosticsEnabledChangeListener(it) }
        prefsListener = null
        jankStats = null
    }

    open suspend fun <T> measure(operationName: String, block: suspend () -> T): T {
        if (!configStore.isDiagnosticsEnabled()) return block()
        val startNanos = System.nanoTime()
        val result = block()
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000
        log("timer $operationName duration_ms=$durationMs")
        return result
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        ioScope.launch { writeToFile(message) }
    }

    private suspend fun writeToFile(message: String) {
        val dir = File(context.filesDir, "perf")
        if (!dir.exists()) dir.mkdirs()
        // A fresh SimpleDateFormat per call, not a shared companion instance -
        // SimpleDateFormat isn't thread-safe, and ioScope dispatches onto a
        // pool of Dispatchers.IO threads, so concurrent log() calls (a jank
        // frame and a measure() timer landing close together) could otherwise
        // race on the same formatter instance.
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$timestamp $message\n"
        fileWriteMutex.withLock {
            runCatching {
                FileOutputStream(File(dir, "perf-log.txt"), /* append = */ true).use { it.write(line.toByteArray()) }
            }.onFailure { Log.w(TAG, "failed to write perf log file", it) }
        }
    }

    companion object {
        private const val TAG = "PerfMonitor"
    }
}
