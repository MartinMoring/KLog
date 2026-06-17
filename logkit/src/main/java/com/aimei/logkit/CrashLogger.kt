package com.aimei.logkit

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object CrashLogger {

    private const val TAG = "Crash"
    private val TS = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun register(context: Context) {
        val logDir = KLog.getLogDir()
        installMainThreadHandler()
        installUncaughtHandler(logDir)
    }

    private fun installMainThreadHandler() {
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                } catch (e: Throwable) {
                    KLog.e(TAG, "Main thread crash:\n${e.stackTraceToString()}")
                }
            }
        }
    }

    private fun installUncaughtHandler(logDir: File) {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val msg = "Thread [${thread.name}] crash:\n${throwable.stackTraceToString()}"
                KLog.e(TAG, msg)
                writeSync(logDir, msg)
            } catch (_: Throwable) {}
            original?.uncaughtException(thread, throwable)
        }
    }

    private fun writeSync(logDir: File, msg: String) {
        try {
            val line = "[${TS.format(Date())}][E][$TAG] $msg"
            LogFileManager.getTodayFile(logDir).appendText(line + "\n")
        } catch (_: Exception) {}
    }
}
