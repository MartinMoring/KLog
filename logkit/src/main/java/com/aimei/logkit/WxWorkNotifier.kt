package com.aimei.logkit

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object WxWorkNotifier {

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val TS_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * 向企业微信群机器人发送日志上报通知。
     *
     * @param webhookKey    企业微信 webhook key
     * @param description   用户填写的问题描述
     * @param fileUrl       上传成功后的文件公网 URL
     * @param fileSizeBytes zip 文件大小（字节）
     * @param extraInfo     额外设备/房间信息（来自 KLogConfig.deviceInfoProvider）
     */
    suspend fun notify(
        webhookKey: String,
        description: String,
        fileUrl: String,
        fileSizeBytes: Long,
        extraInfo: Map<String, String> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        if (webhookKey.isBlank()) {
            Log.w("KLog", "WxWorkNotifier: webhookKey is empty, skip notify")
            return@withContext false
        }

        val ts = TS_FORMAT.format(Date())
        val sizeStr = fileSizeBytes.toHumanSize()
        val extraLines = if (extraInfo.isNotEmpty()) {
            extraInfo.entries.joinToString("\n") { (k, v) -> "**$k**: ${escapeMarkdown(v)}" } + "\n"
        } else ""

        val content = """
            ### 日志上报
            **时间**: $ts
            **描述**: ${escapeMarkdown(description)}
            ${extraLines}**日志文件** ($sizeStr): [点击下载]($fileUrl)
        """.trimIndent()

        val body = JSONObject().apply {
            put("msgtype", "markdown")
            put("markdown", JSONObject().apply { put("content", content) })
        }.toString()

        val url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=$webhookKey"

        runCatching {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("KLog", "WxWork notify failed: ${response.code} ${response.body?.string()}")
                }
                response.isSuccessful
            }
        }.onFailure {
            Log.e("KLog", "WxWork notify exception", it)
        }.getOrDefault(false)
    }

    private fun escapeMarkdown(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("`", "\\`")
    }

    private fun Long.toHumanSize(): String = when {
        this < 1024 -> "${this}B"
        this < 1024 * 1024 -> "${"%.1f".format(this / 1024.0)}KB"
        else -> "${"%.1f".format(this / 1024.0 / 1024.0)}MB"
    }
}
