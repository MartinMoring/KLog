package com.aimei.logkit

import java.io.File

/**
 * 日志上传接口，由宿主 App 实现具体上传逻辑（如 OSS、自有服务器等）。
 *
 * 示例：
 * ```kotlin
 * class OssLogUploader : LogUploader {
 *     override suspend fun upload(zipFile: File, description: String): Result<String> {
 *         return runCatching {
 *             // 使用项目已有的 OSS 客户端上传，返回文件公网 URL
 *             "https://your-bucket.oss.com/logs/${zipFile.name}"
 *         }
 *     }
 * }
 * ```
 */
interface LogUploader {
    /**
     * 上传压缩后的日志文件。
     * @param zipFile   已压缩的 .zip 文件（上传完成后 SDK 会自动删除）
     * @param description 用户填写的问题描述
     * @return Result.success(url) 上传成功后的文件访问 URL；Result.failure(e) 失败原因
     */
    suspend fun upload(zipFile: File, description: String): Result<String>
}
