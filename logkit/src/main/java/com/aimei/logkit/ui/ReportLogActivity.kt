package com.aimei.logkit.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.aimei.logkit.EmailNotifier
import com.aimei.logkit.KLog
import com.aimei.logkit.LogFileManager
import com.aimei.logkit.R
import com.aimei.logkit.WxWorkNotifier
import com.aimei.logkit.databinding.ActivityReportLogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReportLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val files = KLog.listLogFiles()
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.logkit_no_files, Toast.LENGTH_SHORT).show()
        }

        val adapter = ReportLogAdapter(files) { file -> showUploadDialog(file) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.recyclerView.adapter = adapter
    }

    private fun showUploadDialog(file: File) {
        val requirePhone = KLog.getConfig().requirePhone
        val editText = EditText(this).apply {
            hint = if (requirePhone) {
                getString(R.string.logkit_hint_description_phone_required)
            } else {
                getString(R.string.logkit_hint_description)
            }
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 6
            setPadding(48, 24, 48, 24)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.logkit_dialog_title, file.name))
            .setView(editText)
            // 正向按钮先不传监听，下面手动绑定：校验失败时只弹 Toast，不关闭弹框
            .setPositiveButton(R.string.logkit_confirm, null)
            .setNegativeButton(R.string.logkit_cancel, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val desc = editText.text.toString().trim()
            if (!isValidDescription(desc)) {
                val msg = if (requirePhone)
                    getString(R.string.logkit_phone_required)
                else
                    getString(R.string.logkit_desc_empty)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            startUpload(file, desc)
        }
    }

    private fun isValidDescription(desc: String): Boolean {
        if (desc.isBlank()) return false
        if (!KLog.getConfig().requirePhone) return true
        return Regex("1[3-9]\\d{9}").containsMatchIn(desc)
    }

    private fun startUpload(file: File, description: String) {
        val uploader = KLog.getUploader()
        val config = KLog.getConfig()
        // 未注册 uploader 时，只要配置了邮件就仍走邮箱上报；两者都没配才提示无法上报
        if (uploader == null && config.emailConfig == null) {
            Toast.makeText(this, R.string.logkit_no_uploader, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, R.string.logkit_uploading, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            var zipFile: File? = null
            try {
                zipFile = LogFileManager.compressToZip(file, KLog.getLogDir())
                // 未注册 uploader 时跳过服务器上传，没有公网 URL，仅走邮件上报
                val result: Result<String?> = uploader?.upload(zipFile, description) ?: Result.success(null)
                result.onSuccess { url ->
                    val extra = config.deviceInfoProvider()

                    // 企微通知和邮件发送并行执行、互不阻塞，单独失败不影响最终结果；
                    // 没有公网 URL（未配置 uploader）时不发送企微通知
                    val (wxOk, emailResult) = coroutineScope {
                        val wxJob = async {
                            if (url == null) true
                            else WxWorkNotifier.notify(
                                webhookKey = config.wxWebhookKey,
                                description = description,
                                fileUrl = url,
                                fileSizeBytes = zipFile.length(),
                                extraInfo = extra
                            )
                        }
                        val emailJob = async {
                            config.emailConfig?.let { emailConfig ->
                                runCatching {
                                    EmailNotifier.send(
                                        config = emailConfig,
                                        description = description,
                                        fileUrl = url,
                                        zipFile = zipFile,
                                        extraInfo = extra
                                    )
                                }.onFailure { e -> KLog.e("Email", "邮件发送失败: ${e.message}") }
                            }
                        }
                        wxJob.await() to emailJob.await()
                    }

                    withContext(Dispatchers.Main) {
                        val msg = if (url == null) {
                            // 未配置 uploader：邮件是唯一上报渠道，结果直接反映邮件发送状态
                            emailResult?.fold(
                                onSuccess = { getString(R.string.logkit_email_only_success) },
                                onFailure = { e -> getString(R.string.logkit_email_failed, e.message ?: "") }
                            ) ?: getString(R.string.logkit_no_uploader)
                        } else {
                            val issues = mutableListOf<String>()
                            if (config.wxWebhookKey.isNotBlank() && !wxOk) {
                                issues += getString(R.string.logkit_notify_wx_failed)
                            }
                            if (emailResult?.isFailure == true) {
                                issues += getString(R.string.logkit_notify_email_failed)
                            }
                            if (issues.isEmpty()) {
                                getString(R.string.logkit_upload_success)
                            } else {
                                getString(R.string.logkit_upload_success_partial, issues.joinToString("，"))
                            }
                        }
                        Toast.makeText(this@ReportLogActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ReportLogActivity,
                            getString(R.string.logkit_upload_failed, e.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ReportLogActivity,
                        getString(R.string.logkit_upload_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                zipFile?.delete()
            }
        }
    }
}
