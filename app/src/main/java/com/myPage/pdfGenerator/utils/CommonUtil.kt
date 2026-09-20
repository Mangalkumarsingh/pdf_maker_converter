package com.myPage.pdfGenerator.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object CommonUtil {

    fun openPdf(context: Context, packageName: String, file: File) {
        val uri = FileProvider.getUriForFile(context, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    }

    fun sharePdf(context: Context, packageName: String, file: File) {
        val uri = FileProvider.getUriForFile(context, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    fun deletePdf(context: Context, file: File): Boolean {
        val ok = file.delete()
        if (ok) {
            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
        }
        return ok
    }

    fun downloadPdfToDownloads(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/PDFMaker"
                )
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                return false
            }

            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }

            Toast.makeText(context, "Saved to Downloads/PDFMaker", Toast.LENGTH_SHORT).show()
            return true
        }
        return false // for pre-Q, we handle it via ActivityResultContracts
    }

    fun keepOnlyLatestPdfs(context: Context, maxKeep: Int = 6) {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PDFMaker")
        if (!dir.exists()) return

        val files = dir.listFiles { f ->
            f.isFile && f.name.endsWith(".pdf", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: return

        if (files.size <= maxKeep) return

        // delete older (index maxKeep .. end)
        files.drop(maxKeep).forEach { it.delete() }
    }
}
