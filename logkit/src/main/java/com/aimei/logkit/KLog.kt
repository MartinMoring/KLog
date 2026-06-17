package com.aimei.logkit

import android.content.Context
import android.content.Intent
import android.util.Log
import com.aimei.logkit.ui.ReportLogActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 日志 SDK 主入口，单例。
 *
 * 使用方式：
 * ```kotlin
 * // Application.onCreate()
 * KLog.init(this, KLogConfig(wxWebhookKey = "your-key"))
 * KLog.registerUploader(OssLogUploader())
 *
 * // 记录日志
 * KLog.d("MainActivity", "页面启动")
 * KLog.e("Network", "请求失败", exception)
 *
 * // 打开上报页面
 * KLog.openReportPage(context)
 *
 * // 进入后台时调用（AppLifecycleCallback.onActivityStopped）
 * KLog.flush()
 * ```
 *
 * 优化写入策略：
 * - 所有日志先进入 [ConcurrentLinkedQueue]（无锁，多线程安全）
 * - 单个后台协程通过 `select {}` 同时等待：
 *   1. 队列满信号（[Channel.CONFLATED]）：缓冲达到阈值立即触发
 *   2. 定时超时：[KLogConfig.flushIntervalMs]（默认 30 秒）到期触发
 * - 每次 flush 用 [BufferedWriter] 批量追加写入，减少系统调用次数
 * - 跨天自动切换文件：[LogFileManager.getTodayFile] 每次 flush 动态获取当日文件
 */
object KLog {

    private val queue = ConcurrentLinkedQueue<String>()
    private val flushSignal = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private lateinit var config: KLogConfig
    private lateinit var logDir: File
    private var uploader: LogUploader? = null
    private var initialized = false

    /** 初始化 SDK，必须在 Application.onCreate() 中调用一次 */
    fun init(context: Context, config: KLogConfig = KLogConfig()) {
        this.config = config
        this.logDir = config.logDir ?: File(context.filesDir, "klog")
        if (!logDir.exists()) logDir.mkdirs()
        initialized = true
        startFlushLoop()
        if (config.enableClickLog) ClickEventLogger.register(context)
        if (config.enableLifecycleLog) LifecycleLogger.register(context)
        if (config.enableCrashLog) CrashLogger.register(context)
    }

    /** 注册上传实现，由宿主 App 提供（调用 init 后注册） */
    fun registerUploader(uploader: LogUploader) {
        this.uploader = uploader
    }

    fun d(tag: String, msg: String) = log(Log.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Log.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(Log.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(Log.ERROR, tag, msg)
    fun e(tag: String, msg: String, t: Throwable) = log(Log.ERROR, tag, "$msg\n${t.stackTraceToString()}")

    /** 立即将内存缓冲落盘（建议在 App 进入后台时调用） */
    fun flush() {
        scope.launch { drainQueue() }
    }

    /** 获取日志目录下所有日志文件列表，按日期倒序（最新在前） */
    fun listLogFiles(): List<File> {
        checkInit()
        return LogFileManager.listFiles(logDir)
    }

    /** 获取日志存储目录 */
    fun getLogDir(): File {
        checkInit()
        return logDir
    }

    /** 打开日志上报页面 */
    fun openReportPage(context: Context) {
        checkInit()
        context.startActivity(
            Intent(context, ReportLogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    internal fun getConfig(): KLogConfig {
        checkInit()
        return config
    }

    internal fun getUploader(): LogUploader? = uploader

    private fun log(priority: Int, tag: String, msg: String) {
        if (!initialized) return
        Log.println(priority, tag, msg)
        val level = when (priority) {
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "V"
        }
        val line = "[${tsFormat.format(Date())}][$level][$tag] $msg"
        queue.offer(line)
        if (queue.size >= config.bufferMaxSize) {
            flushSignal.trySend(Unit)
        }
    }

    private fun startFlushLoop() {
        scope.launch {
            while (isActive) {
                // 等待满队列信号或超时，两者都触发 flush
                withTimeoutOrNull(config.flushIntervalMs) {
                    flushSignal.receive()
                }
                drainQueue()
                LogFileManager.deleteOldLogs(logDir, config.maxRetainDays)
            }
        }
    }

    private suspend fun drainQueue() = withContext(Dispatchers.IO) {
        if (queue.isEmpty()) return@withContext
        try {
            val file = LogFileManager.getTodayFile(logDir)
            BufferedWriter(FileWriter(file, true)).use { writer ->
                var line = queue.poll()
                while (line != null) {
                    writer.write(line)
                    writer.newLine()
                    line = queue.poll()
                }
            }
        } catch (e: Exception) {
            Log.e("KLog", "flush to file failed", e)
        }
    }

    private fun checkInit() {
        check(initialized) { "KLog not initialized. Call KLog.init() in Application.onCreate() first." }
    }
}
