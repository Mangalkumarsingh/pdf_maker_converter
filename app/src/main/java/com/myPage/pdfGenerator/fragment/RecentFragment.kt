package com.myPage.pdfGenerator.fragment

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.adapter.RecentPdfAdapter
import java.io.File

class RecentFragment : Fragment() {

    private lateinit var recentAdapter: RecentPdfAdapter
    private var pendingDownloadFile: File? = null

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
        val rvRecent = view.findViewById<RecyclerView>(R.id.rvRecentPdfs)
        
        recentAdapter = RecentPdfAdapter(
            onOpen = { openFile(it) },
            onShare = { shareFile(it) },
            onDownload = { downloadFile(it) },
            onDelete = { deleteFile(it) }
        )
        
        rvRecent.layoutManager = LinearLayoutManager(requireContext())
        rvRecent.adapter = recentAdapter

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

        val sortedList = list.sortedByDescending { it.lastModified() }
        recentAdapter.submitList(sortedList)
    }

    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        val mimeType = if (file.name.endsWith(".pdf", true)) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Open File"))
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
