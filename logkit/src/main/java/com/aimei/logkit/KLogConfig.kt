package com.aimei.logkit

import java.io.File

/**
 * SMTP 邮件发送配置。
 *
 * @param smtpHost    SMTP 服务器地址，如 "smtp.exmail.qq.com"
 * @param smtpPort    端口，SSL 通常 465，STARTTLS 通常 587
 * @param username    发件邮箱账号
 * @param password    发件邮箱密码或授权码
 * @param recipients  收件人列表
 * @param senderName  发件人显示名称，默认 "KLog"
 * @param useSsl      true = SSL/TLS（465），false = STARTTLS（587）
 */
data class EmailConfig(
    val smtpHost: String,
    val smtpPort: Int = 465,
    val username: String,
    val password: String,
    val recipients: List<String>,
    val senderName: String = "KLog",
    val useSsl: Boolean = true
)

/**
 * KLog 配置项，所有字段均有默认值，按需覆盖即可。
 *
 * @param logDir            日志目录；null 时使用 context.filesDir/klog
 * @param maxRetainDays     最多保留天数，超出则删除最旧文件，默认 5
 * @param flushIntervalMs   定期刷盘间隔（毫秒），默认 30 秒
 * @param bufferMaxSize     缓冲队列条目阈值，超出立即触发刷盘，默认 500
 * @param wxWebhookKey      企业微信群机器人 webhook key，为空时不发送通知
 * @param deviceInfoProvider 额外设备信息，回调返回 key-value map，写入企微通知
 * @param requirePhone      描述中是否必须包含手机号，默认 true
 * @param emailConfig       邮件发送配置；null 时不发送邮件
 */
data class KLogConfig(
    val logDir: File? = null,
    val maxRetainDays: Int = 5,
    val flushIntervalMs: Long = 30_000L,
    val bufferMaxSize: Int = 500,
    val wxWebhookKey: String = "",
    val deviceInfoProvider: () -> Map<String, String> = { emptyMap() },
    val requirePhone: Boolean = true,
    val enableClickLog: Boolean = true,
    val enableLifecycleLog: Boolean = true,
    val enableCrashLog: Boolean = true,
    val emailConfig: EmailConfig? = null
)
