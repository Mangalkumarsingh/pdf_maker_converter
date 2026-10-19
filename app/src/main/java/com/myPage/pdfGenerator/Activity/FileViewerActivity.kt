package com.myPage.pdfGenerator.Activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.databinding.ActivityFileViewerBinding
import com.myPage.pdfGenerator.databinding.ItemPdfPageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream

class FileViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileViewerBinding

    private var targetFile: File? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val renderLock = Any()

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024 * 4) // KB
    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val fileUri = intent.data

        val file = when {
            !filePath.isNullOrBlank() -> File(filePath)
            fileUri != null && fileUri.scheme == "file" -> File(fileUri.path.orEmpty())
            else -> null
        }

        if (file == null || !file.exists()) {
            showError("File not found.")
            return
        }

        targetFile = file
        binding.toolbar.title = file.name
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnShare.setOnClickListener { shareFile(file) }
        binding.btnOpenExternal.setOnClickListener { openExternally(file) }
        binding.btnFallbackOpen.setOnClickListener { openExternally(file) }

        val isPdf = file.name.endsWith(".pdf", ignoreCase = true)
        val isDoc = file.name.endsWith(".docx", ignoreCase = true) || file.name.endsWith(".doc", ignoreCase = true)

        if (isPdf) {
            setupToolbarTheme(R.color.btn_color_pdf)
            loadPdf(file)
        } else if (isDoc) {
            setupToolbarTheme(R.color.btn_color_docs)
            loadDocx(file)
        } else {
            showError("Unsupported file type: ${file.name}")
        }
    }

    private fun setupToolbarTheme(colorResId: Int) {
        val color = ContextCompat.getColor(this, colorResId)

        binding.appBarLayout.setBackgroundColor(color)
        binding.toolbar.setBackgroundColor(color)
        binding.appBarLayout.isLiftOnScroll = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = color
        }
    }

    // ==========================================
    // PDF Rendering
    // ==========================================

    private fun loadPdf(file: File) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvPdfPages.visibility = View.GONE
        binding.scrollDocView.visibility = View.GONE
        binding.layoutError.visibility = View.GONE

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer = PdfRenderer(pfd!!)
                }.isSuccess
            }

            binding.progressBar.visibility = View.GONE

            val renderer = pdfRenderer
            if (!success || renderer == null) {
                showError("Unable to render PDF document.")
                return@launch
            }

            val pageCount = renderer.pageCount
            binding.toolbar.subtitle = if (pageCount == 1) "1 page" else "$pageCount pages"

            binding.rvPdfPages.visibility = View.VISIBLE
            val layoutManager = LinearLayoutManager(this@FileViewerActivity)
            binding.rvPdfPages.layoutManager = layoutManager

            val adapter = PdfPagesAdapter(renderer, pageCount)
            binding.rvPdfPages.adapter = adapter

            binding.tvFloatingIndicator.visibility = View.VISIBLE
            updatePageIndicator(1, pageCount)

            binding.rvPdfPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    if (firstVisible != RecyclerView.NO_POSITION) {
                        updatePageIndicator(firstVisible + 1, pageCount)
                    }
                }
            })
        }
    }

    private fun updatePageIndicator(current: Int, total: Int) {
        binding.tvFloatingIndicator.text = "$current / $total"
    }

    inner class PdfPagesAdapter(
        private val renderer: PdfRenderer,
        private val totalPages: Int
    ) : RecyclerView.Adapter<PdfPagesAdapter.PageViewHolder>() {

        inner class PageViewHolder(val b: ItemPdfPageBinding) : RecyclerView.ViewHolder(b.root) {
            var renderJob: Job? = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val b = ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return PageViewHolder(b)
        }

        override fun getItemCount(): Int = totalPages

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.renderJob?.cancel()

            holder.b.tvPageNum.text = "${position + 1} / $totalPages"
            holder.b.tvPageNum.visibility = View.VISIBLE

            // Pre-calculate page aspect ratio without blocking
            var pageWidth = 595
            var pageHeight = 842
            synchronized(renderLock) {
                try {
                    val page = renderer.openPage(position)
                    pageWidth = page.width
                    pageHeight = page.height
                    page.close()
                } catch (_: Exception) {}
            }

            val displayWidth = (resources.displayMetrics.widthPixels - (28 * resources.displayMetrics.density)).toInt()
            val ratio = pageHeight.toFloat() / pageWidth.toFloat()
            val targetHeight = (displayWidth * ratio).toInt().coerceAtLeast(200)

            holder.b.ivPage.layoutParams.height = targetHeight

            val cached = bitmapCache.get(position)
            if (cached != null) {
                holder.b.pageProgress.visibility = View.GONE
                holder.b.ivPage.setImageBitmap(cached)
            } else {
                holder.b.ivPage.setImageBitmap(null)
                holder.b.pageProgress.visibility = View.VISIBLE

                holder.renderJob = lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        renderPageBitmap(position, displayWidth * 2)
                    }
                    if (bitmap != null) {
                        bitmapCache.put(position, bitmap)
                        holder.b.pageProgress.visibility = View.GONE
                        holder.b.ivPage.setImageBitmap(bitmap)
                    }
                }
            }
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            super.onViewRecycled(holder)
            holder.renderJob?.cancel()
            holder.b.ivPage.resetZoom()
            holder.b.ivPage.setImageBitmap(null)
        }
    }

    private fun renderPageBitmap(pageIndex: Int, targetWidth: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        synchronized(renderLock) {
            return try {
                val page = renderer.openPage(pageIndex)
                val ratio = page.height.toFloat() / page.width.toFloat()
                val targetHeight = (targetWidth * ratio).toInt().coerceAtLeast(100)

                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)

                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // ==========================================
    // DOCX Rendering
    // ==========================================

    private fun loadDocx(file: File) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvPdfPages.visibility = View.GONE
        binding.scrollDocView.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.toolbar.subtitle = "Word Document"

        lifecycleScope.launch {
            val docElements = withContext(Dispatchers.IO) {
                runCatching {
                    parseDocxElements(file)
                }.getOrNull()
            }

            binding.progressBar.visibility = View.GONE

            if (docElements == null || docElements.isEmpty()) {
                showError("Unable to display document directly. Try opening with an external app.")
                return@launch
            }

            binding.scrollDocView.visibility = View.VISIBLE
            binding.layoutDocContent.removeAllViews()

            for (elem in docElements) {
                when (elem) {
                    is DocElement.TextParagraph -> {
                        val tv = TextView(this@FileViewerActivity).apply {
                            text = elem.text
                            textSize = elem.sizeSp
                            setTextColor(elem.color)
                            if (elem.isBold) setTypeface(null, Typeface.BOLD)
                            setPadding(0, 4, 0, 10)
                            textAlignment = when (elem.alignment) {
                                ParagraphAlignment.CENTER -> View.TEXT_ALIGNMENT_CENTER
                                ParagraphAlignment.RIGHT -> View.TEXT_ALIGNMENT_TEXT_END
                                else -> View.TEXT_ALIGNMENT_TEXT_START
                            }
                        }
                        binding.layoutDocContent.addView(tv)
                    }
                    is DocElement.ImageElement -> {
                        val iv = ImageView(this@FileViewerActivity).apply {
                            adjustViewBounds = true
                            setImageBitmap(elem.bitmap)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setPadding(0, 8, 0, 16)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        binding.layoutDocContent.addView(iv)
                    }
                }
            }
        }
    }

    private sealed class DocElement {
        data class TextParagraph(
            val text: String,
            val sizeSp: Float = 15f,
            val isBold: Boolean = false,
            val color: Int = Color.BLACK,
            val alignment: ParagraphAlignment = ParagraphAlignment.LEFT
        ) : DocElement()

        data class ImageElement(val bitmap: Bitmap) : DocElement()
    }

    private fun parseDocxElements(file: File): List<DocElement> {
        val elements = mutableListOf<DocElement>()
        FileInputStream(file).use { fis ->
            val doc = XWPFDocument(fis)

            for (p in doc.paragraphs) {
                val text = p.text?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    val isFirstHeading = elements.isEmpty()
                    val fontSize = if (isFirstHeading) 18f else 15f
                    val bold = isFirstHeading || p.runs.any { it.isBold }
                    elements.add(
                        DocElement.TextParagraph(
                            text = text,
                            sizeSp = fontSize,
                            isBold = bold,
                            alignment = p.alignment ?: ParagraphAlignment.LEFT
                        )
                    )
                }

                for (run in p.runs) {
                    for (pic in run.embeddedPictures) {
                        val bytes = pic.pictureData.data
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            elements.add(DocElement.ImageElement(bmp))
                        }
                    }
                }
            }

            // Fallback for docx converted with only pictures
            if (elements.isEmpty()) {
                for (pic in doc.allPictures) {
                    val bytes = pic.data
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        elements.add(DocElement.ImageElement(bmp))
                    }
                }
            }
        }
        return elements
    }

    // ==========================================
    // Actions & Error handling
    // ==========================================

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.rvPdfPages.visibility = View.GONE
        binding.scrollDocView.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val mimeType = if (file.name.endsWith(".pdf", true)) "application/pdf"
        else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share File"))
    }

    private fun openExternally(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val mimeType = if (file.name.endsWith(".pdf", true)) "application/pdf"
        else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: Exception) {
            Toast.makeText(this, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        synchronized(renderLock) {
            try {
                pdfRenderer?.close()
                pdfRenderer = null
            } catch (_: Exception) {}
            try {
                pfd?.close()
                pfd = null
            } catch (_: Exception) {}
        }
        bitmapCache.evictAll()
    }

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"

        fun start(context: Context, file: File) {
            val intent = Intent(context, FileViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, file.absolutePath)
            }
            context.startActivity(intent)
        }
    }
}
