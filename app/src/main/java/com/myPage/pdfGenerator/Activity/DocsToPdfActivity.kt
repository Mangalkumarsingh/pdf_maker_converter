package com.myPage.pdfGenerator.Activity

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.databinding.ActivityDocsToPdfBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.myPage.pdfGenerator.adapter.PdfMakingAdapter
import com.myPage.pdfGenerator.utils.CommonUtil
import com.myPage.pdfGenerator.utils.CompressDialogHelper
import com.myPage.pdfGenerator.utils.CompressLevelBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


class DocsToPdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocsToPdfBinding

    private var selectedDocUri: Uri? = null
    private var pdfName: String = "converted"

    private lateinit var pdfMakingAdapter: PdfMakingAdapter
    private var pendingDownloadFile: File? = null
    private val preRenderedPdfBytes = mutableMapOf<CompressLevelBar.Level, ByteArray>()

    private val pickDocLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                selectedDocUri = uri
                preRenderedPdfBytes.clear()
                binding.tvSelectedPdf.visibility= View.VISIBLE
                binding.btnConvert.isEnabled = true
            }
        }

    private val createPdfDownload =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            val src = pendingDownloadFile ?: return@registerForActivityResult
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
            pendingDownloadFile = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocsToPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val window = getWindow()
            window.setStatusBarColor(getResources().getColor(R.color.btn_color_pdf));
        }
        binding.toolbar.findViewById<android.widget.ImageView>(R.id.ivBack).setOnClickListener {
            finish()
        }

        pdfMakingAdapter = PdfMakingAdapter(
            onOpen = { openPdf(it) },
            onShare = { sharePdf(it) },
            onDownload = { downloadPdfToDownloads(it) },
            onDelete = { deletePdf(it) }
        )
        binding.rvRecentPdfs.layoutManager = LinearLayoutManager(this)
        binding.rvRecentPdfs.adapter = pdfMakingAdapter

        loadRecentPdfs()

        binding.btnPickPdf.setOnClickListener {
            pickDocLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/msword"
                )
            )
        }

        binding.btnConvert.isEnabled = false
        binding.btnConvert.setOnClickListener {
            val docUri = selectedDocUri ?: return@setOnClickListener
            val defaultName = CommonUtil.getFileBaseName(this, docUri, "converted")

            showNameDialogCustom(defaultName) { entered ->
                pdfName = entered

                val originalSize = CommonUtil.getUriFileSize(this, docUri)

                CompressDialogHelper.showDocumentCompressDialog(
                    context = this,
                    title = "Compress Output PDF",
                    sizeLabel = "Estimated PDF Size",
                    scope = lifecycleScope,
                    fallbackHighBytes = originalSize,
                    estimateSizes = {
                        estimateDocsToPdfSizes(this@DocsToPdfActivity, docUri)
                    },
                    onConfirmed = { compressLevel ->
                        val outFile = File(getPdfsDir(), "${pdfName}.pdf")
                        convertDocxToPdf(docUri, outFile, compressLevel)
                    },
                    onCancelled = {
                        // DOCS to PDF not created if cancelled
                    }
                )
            }
        }
    }


    private fun showNameDialogCustom(defaultName: String, onOk: (String) -> Unit) {
        val v = layoutInflater.inflate(R.layout.dialog_rename, null, false)
        val et = v.findViewById<TextInputEditText>(R.id.etName)
        val btnOk = v.findViewById<MaterialButton>(R.id.btnOk)
        val btnCancel = v.findViewById<MaterialButton>(R.id.btnCancel)

        et.setText(defaultName)
        et.requestFocus()

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(v)
            .create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.window?.let { window ->
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes.blurBehindRadius = 35
                window.setDimAmount(0.4f)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0.75f)
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnOk.setOnClickListener {
            val name = et.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                et.error = "Enter document name"
                return@setOnClickListener
            }

            onOk(sanitizeFileName(name))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim()

    private fun renderDocxToPdf(
        context: android.content.Context,
        docUri: Uri,
        compressLevel: CompressLevelBar.Level,
        outStream: OutputStream
    ) {
        context.contentResolver.openInputStream(docUri).use { input ->
            requireNotNull(input)

            val doc = XWPFDocument(input)
            val pdf = android.graphics.pdf.PdfDocument()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                color = Color.BLACK
            }

            val pageWidth = 595
            val pageHeight = 842
            val margin = 40
            val maxW = pageWidth - margin * 2

            var pageNumber = 1
            var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdf.startPage(pageInfo)
            var canvas = page.canvas

            var x = margin.toFloat()
            var y = margin.toFloat()

            fun newPage() {
                pdf.finishPage(page)
                pageNumber++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = margin.toFloat()
            }

            fun drawWrapped(text: String) {
                val words = text.split(" ")
                var line = ""
                for (w in words) {
                    val test = if (line.isEmpty()) w else "$line $w"
                    if (paint.measureText(test) <= maxW) {
                        line = test
                    } else {
                        if (y > pageHeight - margin) newPage()
                        canvas.drawText(line, x, y, paint)
                        y += paint.textSize + 8
                        line = w
                    }
                }
                if (line.isNotEmpty()) {
                    if (y > pageHeight - margin) newPage()
                    canvas.drawText(line, x, y, paint)
                    y += paint.textSize + 10
                }
            }

            val maxDim = when (compressLevel) {
                CompressLevelBar.Level.HIGH -> 900
                CompressLevelBar.Level.MEDIUM -> 600
                CompressLevelBar.Level.LOW -> 380
            }

            for (p in doc.paragraphs) {
                val text = p.text?.trim().orEmpty()
                if (text.isNotEmpty()) drawWrapped(text)

                for (run in p.runs) {
                    val pics = run.embeddedPictures
                    for (pic in pics) {
                        val bytes = pic.pictureData.data
                        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue

                        val currentMax = maxOf(bmp.width, bmp.height)
                        if (currentMax > maxDim) {
                            val scale = maxDim.toFloat() / currentMax.toFloat()
                            val targetW = (bmp.width * scale).toInt().coerceAtLeast(100)
                            val targetH = (bmp.height * scale).toInt().coerceAtLeast(100)
                            val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                            if (scaled != bmp) {
                                bmp.recycle()
                                bmp = scaled
                            }
                        }

                        val availableW = maxW.toFloat()
                        val ratio = bmp.height.toFloat() / bmp.width.toFloat()
                        val drawW = availableW
                        val drawH = drawW * ratio

                        if (y + drawH > pageHeight - margin) newPage()

                        val dst = RectF(x, y, x + drawW, y + drawH)
                        canvas.drawBitmap(bmp, null, dst, null)
                        y += drawH + 12
                        bmp.recycle()
                    }
                }

                y += 6
                if (y > pageHeight - margin) newPage()
            }

            pdf.finishPage(page)
            pdf.writeTo(outStream)
            pdf.close()
            doc.close()
        }
    }

    private fun estimateDocsToPdfSizes(
        context: android.content.Context,
        docUri: Uri
    ): Map<CompressLevelBar.Level, Long> {
        val result = mutableMapOf<CompressLevelBar.Level, Long>()
        try {

            val baosHigh = ByteArrayOutputStream()
            renderDocxToPdf(context, docUri, CompressLevelBar.Level.HIGH, baosHigh)
            val highBytes = baosHigh.toByteArray()
            preRenderedPdfBytes[CompressLevelBar.Level.HIGH] = highBytes
            result[CompressLevelBar.Level.HIGH] = highBytes.size.toLong()

            val baosMed = ByteArrayOutputStream()
            renderDocxToPdf(context, docUri, CompressLevelBar.Level.MEDIUM, baosMed)
            val medBytes = baosMed.toByteArray()
            preRenderedPdfBytes[CompressLevelBar.Level.MEDIUM] = medBytes
            result[CompressLevelBar.Level.MEDIUM] = medBytes.size.toLong()


            val baosLow = ByteArrayOutputStream()
            renderDocxToPdf(context, docUri, CompressLevelBar.Level.LOW, baosLow)
            val lowBytes = baosLow.toByteArray()
            preRenderedPdfBytes[CompressLevelBar.Level.LOW] = lowBytes
            result[CompressLevelBar.Level.LOW] = lowBytes.size.toLong()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun convertDocxToPdf(
        docUri: Uri,
        outFile: File,
        compressLevel: CompressLevelBar.Level = CompressLevelBar.Level.HIGH
    ) {
        binding.progress.visibility = View.VISIBLE
        binding.btnPickPdf.isEnabled = false
        binding.btnConvert.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val preCached = preRenderedPdfBytes[compressLevel]
                    if (preCached != null && preCached.isNotEmpty()) {
                        FileOutputStream(outFile).use { fos ->
                            fos.write(preCached)
                            fos.flush()
                        }
                    } else {
                        FileOutputStream(outFile).use { out ->
                            renderDocxToPdf(this@DocsToPdfActivity, docUri, compressLevel, out)
                            out.flush()
                        }
                    }
                }
            }

            binding.progress.visibility = View.GONE
            binding.btnPickPdf.isEnabled = true

            result.onSuccess {
                Toast.makeText(this@DocsToPdfActivity, "Saved: ${outFile.name}", Toast.LENGTH_SHORT).show()
                loadRecentPdfs()
                resetConverterState()
            }.onFailure { e ->
                Toast.makeText(this@DocsToPdfActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnConvert.isEnabled = true
                binding.tvSelectedPdf.visibility = View.VISIBLE
            }
        }
    }

    private fun resetConverterState() {
        selectedDocUri = null
        binding.tvSelectedPdf.visibility = View.GONE
        binding.btnConvert.isEnabled = false
    }

    private fun getPdfsDir(): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DocsToPdf")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun loadRecentPdfs() {
        val dir = getPdfsDir()
        val list = dir.listFiles { f -> f.isFile && f.name.endsWith(".pdf", true) }
            ?.sortedByDescending { it.lastModified() }
            ?.take(6)
            ?.toList()
            ?: emptyList()

        pdfMakingAdapter.submitList(list)

        if (list.isEmpty()) {
            binding.ivShowNothing.visibility = View.VISIBLE
            binding.rvRecentPdfs.visibility = View.GONE
            binding.pdfText.visibility= View.GONE
        } else {
            binding.ivShowNothing.visibility = View.GONE
            binding.rvRecentPdfs.visibility = View.VISIBLE
            binding.pdfText.visibility= View.VISIBLE
        }
    }

    private fun openPdf(file: File) {
        FileViewerActivity.start(this, file)
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Share PDF"))
    }

    private fun deletePdf(file: File) {
        if (file.delete()) {
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            loadRecentPdfs()
        } else Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
    }

    private fun downloadPdfToDownloads(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocsToPdf")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: run {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show(); return
            }
            contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            Toast.makeText(this, "Saved to Downloads/DocsToPdf", Toast.LENGTH_SHORT).show()
        } else {
            pendingDownloadFile = file
            createPdfDownload.launch(file.name)
        }
    }
}
