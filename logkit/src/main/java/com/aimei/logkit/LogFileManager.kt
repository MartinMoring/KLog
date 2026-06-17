package com.aimei.logkit

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object LogFileManager {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** 获取今日日志文件，不存在则创建 */
    fun getTodayFile(dir: File): File {
        val name = "log_${DATE_FORMAT.format(Date())}.txt"
        return File(dir, name).also { if (!it.exists()) it.createNewFile() }
    }

    /**
     * 列出目录下所有日志文件，按文件名倒序（即日期最新在前）。
     * ISO 日期格式名称可直接按字符串排序，无需解析 Date。
     */
    fun listFiles(dir: File): List<File> {
        return (dir.listFiles { f ->
            f.isFile && f.name.startsWith("log_") && f.name.endsWith(".txt")
        } ?: emptyArray()).sortedByDescending { it.name }
    }

    /**
     * 删除超出保留天数的旧日志文件。
     * listFiles 已按名称倒序，drop(maxDays) 即为最旧的文件。
     */
    fun deleteOldLogs(dir: File, maxDays: Int) {
        val all = listFiles(dir)
        if (all.size > maxDays) {
            all.drop(maxDays).forEach { it.delete() }
        }
    }

    /**
     * 将单个日志文件压缩为 zip，输出到同一目录。
     * @return 压缩后的 zip 文件
     */
    fun compressToZip(logFile: File, outputDir: File): File {
        val zipFile = File(outputDir, "${logFile.nameWithoutExtension}.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            zos.putNextEntry(ZipEntry(logFile.name))
            logFile.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        return zipFile
    }
}
