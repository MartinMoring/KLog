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
        val editText = EditText(this).apply {
            hint = getString(R.string.logkit_hint_description)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 6
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logkit_dialog_title, file.name))
            .setView(editText)
            .setPositiveButton(R.string.logkit_confirm) { _, _ ->
                val desc = editText.text.toString().trim()
                if (!isValidDescription(desc)) {
                    val msg = if (KLog.getConfig().requirePhone)
                        getString(R.string.logkit_phone_required)
                    else
                        getString(R.string.logkit_desc_empty)
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                startUpload(file, desc)
            }
            .setNegativeButton(R.string.logkit_cancel, null)
            .show()
    }

    private fun isValidDescription(desc: String): Boolean {
        if (desc.isBlank()) return false
        if (!KLog.getConfig().requirePhone) return true
        return Regex("1[3-9]\\d{9}").containsMatchIn(desc)
    }

    private fun startUpload(file: File, description: String) {
        val uploader = KLog.getUploader()
        if (uploader == null) {
            Toast.makeText(this, R.string.logkit_no_uploader, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, R.string.logkit_uploading, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            var zipFile: File? = null
            try {
                zipFile = LogFileManager.compressToZip(file, KLog.getLogDir())
                val result = uploader.upload(zipFile, description)
                result.onSuccess { url ->
                    val config = KLog.getConfig()
                    val extra = config.deviceInfoProvider()
                    WxWorkNotifier.notify(
                        webhookKey = config.wxWebhookKey,
                        description = description,
                        fileUrl = url,
                        fileSizeBytes = zipFile.length(),
                        extraInfo = extra
                    )
                    config.emailConfig?.let { emailConfig ->
                        try {
                            EmailNotifier.send(
                                config = emailConfig,
                                description = description,
                                fileUrl = url,
                                zipFile = zipFile,
                                extraInfo = extra
                            )
                        } catch (e: Exception) {
                            KLog.e("Email", "邮件发送失败: ${e.message}")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ReportLogActivity,
                            R.string.logkit_upload_success,
                            Toast.LENGTH_SHORT
                        ).show()
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
