package com.myPage.pdfGenerator.fragment

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.adapter.DocsAdapter
import com.myPage.pdfGenerator.adapter.RecentPdfAdapter
import java.io.File

class RecentFragment : Fragment() {

    private lateinit var rvRecent: RecyclerView
    private lateinit var pdfAdapter: RecentPdfAdapter
    private lateinit var docAdapter: DocsAdapter

    private var pendingDownloadFile: File? = null
    private var allRecentFiles: List<File> = emptyList()

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val src = pendingDownloadFile ?: return@registerForActivityResult
        if (uri != null) {
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { input -> input.copyTo(out) }
            }
            Toast.makeText(requireContext(), "File saved", Toast.LENGTH_SHORT).show()
        }
        pendingDownloadFile = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_recent, container, false)
        rvRecent = view.findViewById(R.id.rvRecentPdfs)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutRecent)

        rvRecent.layoutManager = LinearLayoutManager(requireContext())

        // 1. दोनों Adapters को Initialize करना
        val onOpen = { file: File -> openFile(file) }
        val onShare = { file: File -> shareFile(file) }
        val onDownload = { file: File -> downloadFile(file) }
        val onDelete = { file: File -> deleteFile(file) }

        pdfAdapter = RecentPdfAdapter(onOpen, onShare, onDownload, onDelete)
        docAdapter = DocsAdapter(onOpen, onShare, onDownload, onDelete)

        // 2. Tabs के बीच में Gap (Margin) देना
        tabLayout.post {
            val tabs = tabLayout.getChildAt(0) as ViewGroup
            for (i in 0 until tabs.childCount) {
                val tab = tabs.getChildAt(i)
                val layoutParams = tab.layoutParams as LinearLayout.LayoutParams
                // Left और Right से 16px का गैप (आप इसे अपने हिसाब से कम/ज़्यादा कर सकते हैं)
                layoutParams.setMargins(16, 0, 16, 0)
                tab.layoutParams = layoutParams
                tab.requestLayout()
            }
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterData(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadRecentData()
        return view
    }

    private fun loadRecentData() {
        val list = mutableListOf<File>()

        val pdfDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PDFMaker")
        if (pdfDir.exists()) {
            list.addAll(pdfDir.listFiles { f -> f.isFile } ?: emptyArray())
        }

        val docsToPdfDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DocsToPdf")
        if (docsToPdfDir.exists()) {
            list.addAll(docsToPdfDir.listFiles { f -> f.isFile } ?: emptyArray())
        }

        val pdfToDocsDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ConvertedDOCX")
        if (pdfToDocsDir.exists()) {
            list.addAll(pdfToDocsDir.listFiles { f -> f.isFile } ?: emptyArray())
        }

        allRecentFiles = list.sortedByDescending { it.lastModified() }
        val tabLayout = view?.findViewById<TabLayout>(R.id.tabLayoutRecent)
        filterData(tabLayout?.selectedTabPosition ?: 0)
    }

    private fun filterData(tabPosition: Int) {
        // 3. Tab के अनुसार Adapter चेंज करना
        if (tabPosition == 0) {
            // PDF tab सेलेक्टेड है
            val filteredList = allRecentFiles.filter { it.name.endsWith(".pdf", true) }
            pdfAdapter.submitList(filteredList)
            rvRecent.adapter = pdfAdapter
        } else {
            // Docs tab सेलेक्टेड है
            val filteredList = allRecentFiles.filter { it.name.endsWith(".doc", true) || it.name.endsWith(".docx", true) }
            docAdapter.submitList(filteredList)
            rvRecent.adapter = docAdapter
        }
    }

    private fun openFile(file: File) {
        com.myPage.pdfGenerator.Activity.FileViewerActivity.start(requireContext(), file)
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        val mimeType = if (file.name.endsWith(".pdf", true)) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share File"))
    }

    private fun deleteFile(file: File) {
        if (file.delete()) {
            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
            loadRecentData()
        } else {
            Toast.makeText(requireContext(), "Delete failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadFile(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mimeType = if (file.name.endsWith(".pdf", true)) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PDFMakerApp")
            }

            val uri = requireContext().contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
                return
            }

            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            Toast.makeText(requireContext(), "Saved to Downloads", Toast.LENGTH_SHORT).show()
        } else {
            pendingDownloadFile = file
            createDocument.launch(file.name)
        }
    }
}