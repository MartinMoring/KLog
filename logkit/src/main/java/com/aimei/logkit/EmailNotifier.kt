package com.aimei.logkit

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Message
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

internal object EmailNotifier {

    private val TS = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun send(
        config: EmailConfig,
        description: String,
        fileUrl: String,
        zipFile: File,
        extraInfo: Map<String, String>
    ) {
        val props = buildProps(config)
        val session = Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication() =
                javax.mail.PasswordAuthentication(config.username, config.password)
        })

        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(config.username, config.senderName, "UTF-8"))
            setRecipients(
                Message.RecipientType.TO,
                config.recipients.map { InternetAddress(it) }.toTypedArray()
            )
            subject = "[KLog] 日志上报 ${TS.format(Date())}"
            sentDate = Date()
            setContent(buildMultipart(description, fileUrl, extraInfo, zipFile))
        }

        Transport.send(msg)
    }

    private fun buildProps(config: EmailConfig): Properties = Properties().apply {
        put("mail.smtp.host", config.smtpHost)
        put("mail.smtp.port", config.smtpPort.toString())
        put("mail.smtp.auth", "true")
        if (config.useSsl) {
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            put("mail.smtp.socketFactory.port", config.smtpPort.toString())
        } else {
            put("mail.smtp.starttls.enable", "true")
        }
        put("mail.smtp.connectiontimeout", "15000")
        put("mail.smtp.timeout", "30000")
    }

    private fun buildMultipart(
        description: String,
        fileUrl: String,
        extraInfo: Map<String, String>,
        zipFile: File
    ): MimeMultipart {
        val body = buildString {
            appendLine("【问题描述】")
            appendLine(description)
            appendLine()
            appendLine("【日志下载地址】")
            appendLine(fileUrl)
            if (extraInfo.isNotEmpty()) {
                appendLine()
                appendLine("【设备信息】")
                extraInfo.forEach { (k, v) -> appendLine("$k：$v") }
            }
        }

        val textPart = MimeBodyPart().apply { setText(body, "UTF-8") }
        val attachPart = MimeBodyPart().apply {
            dataHandler = DataHandler(FileDataSource(zipFile))
            fileName = zipFile.name
        }

        return MimeMultipart().apply {
            addBodyPart(textPart)
            addBodyPart(attachPart)
        }
    }
}
