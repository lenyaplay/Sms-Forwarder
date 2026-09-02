package com.smsforwarder.gateway.data.perf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PerfMonitor.measure() always runs the wrapped block regardless of the
 * diagnostics toggle - only the logging side effect is gated. File writes are
 * dispatched off-thread (see PerfMonitor.log), so assertions that depend on
 * the file poll briefly instead of asserting immediately after the suspend
 * call returns.
 */
@RunWith(AndroidJUnit4::class)
class PerfMonitorTest {

    private lateinit var context: Context
    private lateinit var configStore: GatewayConfigStore
    private lateinit var perfMonitor: PerfMonitor
    private lateinit var logFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sms_forwarder_gateway_config", Context.MODE_PRIVATE).edit().clear().commit()
        configStore = GatewayConfigStore(context)
        perfMonitor = PerfMonitor(context, configStore)
        logFile = File(File(context.filesDir, "perf"), "perf-log.txt")
        logFile.delete()
    }

    @Test
    fun measureAlwaysRunsTheBlockRegardlessOfDiagnosticsSetting() = runBlocking {
        configStore.setDiagnosticsEnabled(false)
        val result = perfMonitor.measure("some_op") { 42 }
        assertEquals(42, result)
    }

    @Test
    fun measureDoesNotWriteWhenDiagnosticsDisabled() = runBlocking {
        configStore.setDiagnosticsEnabled(false)
        perfMonitor.measure("disabled_op") { Unit }
        assertFalse(waitForLineContaining("disabled_op"))
    }

    @Test
    fun measureWritesToFileWhenDiagnosticsEnabled() = runBlocking {
        configStore.setDiagnosticsEnabled(true)
        perfMonitor.measure("enabled_op") { Unit }
        assertTrue(waitForLineContaining("enabled_op"))
    }

    private fun waitForLineContaining(needle: String, timeoutMs: Long = 2000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (logFile.exists() && logFile.readText().contains(needle)) return true
            Thread.sleep(50)
        }
        return logFile.exists() && logFile.readText().contains(needle)
    }
}
