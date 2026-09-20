package com.myPage.pdfGenerator.Activity

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.adapter.DocsAdapter
import com.myPage.pdfGenerator.databinding.ActivityPdfToDocsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger

class PdfToDocsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfToDocsBinding

    private var selectedPdfUri: Uri? = null
    private lateinit var docsAdapter: DocsAdapter
    private var pendingDownloadFile: File? = null
    private var docxName: String = "converted"

    private var pendingPdfUri: Uri? = null

    private val pickPdfLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                selectedPdfUri = uri
                binding.tvSelectedPdf.visibility=View.VISIBLE
                binding.btnConvert.isEnabled = true
            }
        }
    private val createDocDownload =
        registerForActivityResult(ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )) { uri ->
            val src = pendingDownloadFile ?: return@registerForActivityResult
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
            pendingDownloadFile = null
        }

    private val createDocxLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        ) { outUri ->
            val pdfUri = pendingPdfUri
            if (outUri != null && pdfUri != null) {
                pendingPdfUri = null
               // convertPdfToDocxUsingOcr(pdfUri, outUri)
                convertPdfToDocx_AsImages(pdfUri, outUri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfToDocsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val window = getWindow()

            window.setStatusBarColor(getResources().getColor(R.color.btn_color_docs));
        }
        docsAdapter = DocsAdapter(
            onOpen = { openDocx(it) },
            onShare = { shareDocx(it) },
            onDownload = { downloadDocxToDownloads(it) },
            onDelete = { deleteDocx(it) }
        )

        binding.rvDocs.layoutManager = LinearLayoutManager(this)
        binding.rvDocs.adapter = docsAdapter

        loadRecentDocs()

        binding.toolbar.title = "PDF → DOCS"
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnPickPdf.setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }

        binding.btnConvert.isEnabled = false
        binding.btnConvert.setOnClickListener {
            val pdfUri = selectedPdfUri ?: return@setOnClickListener

            showNameDialog { enteredName ->
                docxName = enteredName

                val outFile = File(getDocsDir(), "${docxName}.docx")
                convertPdfToDocx_AsImages_SaveToFile(pdfUri, outFile)
                binding.btnConvert.isEnabled = false
                binding.tvSelectedPdf.visibility=View.GONE
            }
        }


    }
    private fun loadRecentDocs() {
        val dir = getDocsDir()

        val list = dir.listFiles { f ->
            f.isFile && f.name.endsWith(".docx", true)
        }?.sortedByDescending { it.lastModified() }
            ?.take(6)
            ?.toList()
            ?: emptyList()

        docsAdapter.submitList(list)
    }
    private fun convertPdfToDocx_AsImages_SaveToFile(pdfUri: Uri, outFile: File) {
        binding.progress.visibility = View.VISIBLE
        binding.btnPickPdf.isEnabled = false
        binding.btnConvert.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    FileOutputStream(outFile).use { fileOut ->
                        XWPFDocument().use { docx ->
                            docx.createStyles()
                            val pageSpec = applyA4Page(docx)

                            contentResolver.openFileDescriptor(pdfUri, "r").use { pfd ->
                                requireNotNull(pfd)

                                PdfRenderer(pfd).use { renderer ->
                                    for (i in 0 until renderer.pageCount) {
                                        renderer.openPage(i).use { page ->
                                            val scale = 2
                                            val bmp = Bitmap.createBitmap(
                                                page.width * scale,
                                                page.height * scale,
                                                Bitmap.Config.ARGB_8888
                                            )
                                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                            val bytes = ByteArrayOutputStream().use { baos ->
                                                bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                                                baos.toByteArray()
                                            }
                                            bmp.recycle()

                                            val ratio = page.height.toDouble() / page.width.toDouble()
                                            var targetWIn = pageSpec.usableWidthInch
                                            var targetHIn = targetWIn * ratio
                                            if (targetHIn > pageSpec.usableHeightInch) {
                                                targetHIn = pageSpec.usableHeightInch
                                                targetWIn = targetHIn / ratio
                                            }

                                            val wEmu = (targetWIn * 914400).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                            val hEmu = (targetHIn * 914400).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

                                            val p = docx.createParagraph().apply {
                                                alignment = ParagraphAlignment.CENTER
                                                spacingBefore = 0
                                                spacingAfter = 0
                                            }
                                            val run = p.createRun()

                                            run.addPicture(
                                                ByteArrayInputStream(bytes),
                                                Document.PICTURE_TYPE_JPEG,
                                                "page_${i + 1}.jpg",
                                                wEmu,
                                                hEmu
                                            )

                                            if (i != renderer.pageCount - 1) run.addBreak(BreakType.PAGE)
                                        }
                                    }
                                }
                            }
                            docx.write(fileOut)
                            fileOut.flush()
                        }
                    }
                }
            }

            binding.progress.visibility = View.GONE
            binding.btnPickPdf.isEnabled = true
            binding.btnConvert.isEnabled = true

            result.onSuccess {
                Toast.makeText(this@PdfToDocsActivity, "Saved: ${outFile.name}", Toast.LENGTH_SHORT).show()
                loadRecentDocs()
            }.onFailure { e ->
                Toast.makeText(this@PdfToDocsActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showNameDialog(onOk: (String) -> Unit) {
        val v = layoutInflater.inflate(R.layout.dialog_rename, null, false)

        val et = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etName)
        val btnOk = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOk)
        val btnCancel = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        //  blank field (NO default name)
        et.setText("")
        et.requestFocus()

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(v)
            .create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnOk.setOnClickListener {
            val name = et.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                et.error = "Enter name"
                return@setOnClickListener
            }
            onOk(sanitizeFileName(name))
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim()
    }

    private fun convertPdfToDocxUsingOcr(pdfUri: Uri, outUri: Uri) {
        binding.progress.visibility = View.VISIBLE
        binding.btnPickPdf.isEnabled = false
        binding.btnConvert.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                    contentResolver.openFileDescriptor(pdfUri, "r").use { pfd ->
                        requireNotNull(pfd) { "PDF file descriptor null" }

                        PdfRenderer(pfd).use { renderer ->
                            contentResolver.openOutputStream(outUri).use { out ->
                                requireNotNull(out) { "DOCX output stream null" }

                                XWPFDocument().use { docx ->
                                    val pageCount = renderer.pageCount
                                    docx.createStyles()

                                    for (i in 0 until pageCount) {
                                        renderer.openPage(i).use { page ->
                                            //  Higher scale => better OCR (but more memory)
                                            val scale = 2
                                            val bmp = Bitmap.createBitmap(
                                                page.width * scale,
                                                page.height * scale,
                                                Bitmap.Config.ARGB_8888
                                            )

                                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                            // OCR
                                            val text = ocrBitmap(recognizer, bmp)

                                            // Free memory
                                            bmp.recycle()

                                            // Write to DOCX
                                            val heading = docx.createParagraph()
                                            heading.createRun().apply {
                                                isBold = true
                                                fontSize = 14
                                                setText("Page ${i + 1}")
                                            }

                                            val body = docx.createParagraph()
                                            val run = body.createRun()
                                            run.fontSize = 12

                                            // preserve line breaks
                                            text.lines().forEachIndexed { idx, line ->
                                                run.setText(line)
                                                if (idx != text.lines().lastIndex) run.addBreak()
                                            }

                                            // Optional: page gap
                                            docx.createParagraph()
                                        }
                                    }

                                    docx.write(out)
                                }




                            }
                        }
                    }
                }
            }

            binding.progress.visibility = View.GONE
            binding.btnPickPdf.isEnabled = true
            binding.btnConvert.isEnabled = true

            result.onSuccess {

                Toast.makeText(this@PdfToDocsActivity, "DOCX (OCR) saved!", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->

                Toast.makeText(this@PdfToDocsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }



    private fun convertPdfToDocx_AsImages(pdfUri: Uri, outUri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val tempDocx = File(cacheDir, "pdf_as_images_${System.currentTimeMillis()}.docx")

                    FileOutputStream(tempDocx).use { fileOut ->
                        XWPFDocument().use { docx ->
                            docx.createStyles()

                            val pageSpec = applyA4Page(docx)

                            contentResolver.openFileDescriptor(pdfUri, "r").use { pfd ->
                                requireNotNull(pfd) { "PDF file descriptor null" }

                                PdfRenderer(pfd).use { renderer ->
                                    val pageCount = renderer.pageCount

                                    for (i in 0 until pageCount) {
                                        renderer.openPage(i).use { page ->
                                            val scale = 2
                                            val bmp = Bitmap.createBitmap(
                                                page.width * scale,
                                                page.height * scale,
                                                Bitmap.Config.ARGB_8888
                                            )
                                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                            val bytes = ByteArrayOutputStream().use { baos ->
                                                bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                                                baos.toByteArray()
                                            }
                                            bmp.recycle()

                                            val ratio = page.height.toDouble() / page.width.toDouble()
                                            var targetWIn = pageSpec.usableWidthInch
                                            var targetHIn = targetWIn * ratio
                                            if (targetHIn > pageSpec.usableHeightInch) {
                                                targetHIn = pageSpec.usableHeightInch
                                                targetWIn = targetHIn / ratio
                                            }

                                            val wEmu = (targetWIn * 914400).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                            val hEmu = (targetHIn * 914400).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

                                            val p = docx.createParagraph().apply {
                                                alignment = ParagraphAlignment.CENTER
                                                spacingBefore = 0
                                                spacingAfter = 0
                                            }
                                            val run = p.createRun()

                                            run.addPicture(
                                                ByteArrayInputStream(bytes),
                                                Document.PICTURE_TYPE_JPEG,
                                                "page_${i + 1}.jpg",
                                                wEmu,
                                                hEmu
                                            )

                                            if (i != pageCount - 1) run.addBreak(BreakType.PAGE)

                                        }
                                    }
                                }
                            }

                            docx.write(fileOut)
                            fileOut.flush()
                        }
                    }

                    contentResolver.openOutputStream(outUri, "w")!!.use { out ->
                        tempDocx.inputStream().use { input -> input.copyTo(out) }
                        out.flush()
                    }

                    val savedFile = File(getDocsDir(), "${docxName}_${System.currentTimeMillis()}.docx")
                    tempDocx.copyTo(savedFile, overwrite = true)
                    tempDocx.delete()
                }
            }

            result.onSuccess {
                Toast.makeText(this@PdfToDocsActivity, "DOCX saved ", Toast.LENGTH_SHORT).show()
                loadRecentDocs()
            }.onFailure { e ->
                Toast.makeText(this@PdfToDocsActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun openDocx(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Open DOCX"))
    }
    private fun shareDocx(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share DOCX"))
    }
    private fun deleteDocx(file: File) {
        if (file.delete()) {
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            loadRecentDocs()
        } else {
            Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDocsDir(): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ConvertedDOCX")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun downloadDocxToDownloads(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ConvertedDOCX")
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
                return
            }

            contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            Toast.makeText(this, "Saved to Downloads/ConvertedDOCX", Toast.LENGTH_SHORT).show()
        } else {
            pendingDownloadFile = file
            createDocDownload.launch(file.name)
        }
    }

    private fun applyA4Page(docx: XWPFDocument): PageSpec {
        val a4Width = 11906
        val a4Height = 16838

        val m = 216

        val body = docx.document.body
        val sectPr: CTSectPr = if (body.isSetSectPr) body.sectPr else body.addNewSectPr()

        val pgSz: CTPageSz = if (sectPr.isSetPgSz) sectPr.pgSz else sectPr.addNewPgSz()
        pgSz.w = BigInteger.valueOf(a4Width.toLong())
        pgSz.h = BigInteger.valueOf(a4Height.toLong())

        val pgMar: CTPageMar = if (sectPr.isSetPgMar) sectPr.pgMar else sectPr.addNewPgMar()
        pgMar.left = BigInteger.valueOf(m.toLong())
        pgMar.right = BigInteger.valueOf(m.toLong())
        pgMar.top = BigInteger.valueOf(m.toLong())
        pgMar.bottom = BigInteger.valueOf(m.toLong())

        return PageSpec(a4Width, a4Height, m, m, m, m)
    }

    private suspend fun ocrBitmap(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap
    ): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.text.orEmpty()
    }
}
private data class PageSpec(
    val widthTwips: Int,
    val heightTwips: Int,
    val marginLeftTwips: Int,
    val marginRightTwips: Int,
    val marginTopTwips: Int,
    val marginBottomTwips: Int
) {
    val usableWidthInch: Double get() = (widthTwips - marginLeftTwips - marginRightTwips) / 1440.0
    val usableHeightInch: Double get() = (heightTwips - marginTopTwips - marginBottomTwips) / 1440.0
}

