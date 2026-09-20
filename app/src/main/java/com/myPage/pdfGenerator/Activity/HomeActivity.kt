package com.myPage.pdfGenerator.Activity

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.adapter.RecentPdfAdapter
import com.myPage.pdfGenerator.adapter.SelectedImagesAdapter
import com.myPage.pdfGenerator.databinding.ActivityHomeBinding
import com.myPage.pdfGenerator.fragment.SelectedImagesDialogFragment
import com.yalantis.ucrop.UCrop
import java.io.File


class HomeActivity : AppCompatActivity(),SelectedImagesDialogFragment.Listener {

    private lateinit var binding: ActivityHomeBinding

     val selectedUris = mutableListOf<Uri>()
    private var editingIndex: Int = -1

    private lateinit var recentAdapter: RecentPdfAdapter
    private val imagesAdapter = SelectedImagesAdapter { uri, position ->
        editingIndex = position
        startUCrop(uri)
    }
    private var pendingCameraUri: Uri? = null

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera()
            else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                pendingCameraUri?.let { uri ->

                    selectedUris.add(uri)

                    SelectedImagesDialogFragment
                        .newInstance(ArrayList(selectedUris))
                        .show(supportFragmentManager, "SelectedImagesDialog")
                }
            }
            pendingCameraUri = null
        }

    private val ucropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val outputUri = UCrop.getOutput(result.data!!)
                val idx = editingIndex

                if (outputUri != null && idx in selectedUris.indices) {
                    selectedUris[idx] = outputUri
                    imagesAdapter.updateItem(idx, outputUri)
                }
            } else if (result.data != null) {
                val err = UCrop.getError(result.data!!)
                Toast.makeText(this, err?.message ?: "Crop cancelled", Toast.LENGTH_SHORT).show()
            }
            editingIndex = -1
        }

    private val pickMultipleMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
            if (uris.isNotEmpty()) {
                SelectedImagesDialogFragment
                    .newInstance(ArrayList(uris))
                    .show(supportFragmentManager, "SelectedImagesDialog")
            }
        }

    private var pendingDownloadFile: File? = null

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            val src = pendingDownloadFile ?: return@registerForActivityResult
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show()
            }
            pendingDownloadFile = null
        }

    @SuppressLint("ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val window = getWindow()
            window.setStatusBarColor(getResources().getColor(R.color.btn_color));
        }

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        binding.toolbar.findViewById<android.widget.ImageView>(R.id.ivProfile)?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        recentAdapter = RecentPdfAdapter(
            onOpen = { openPdf(it) },
            onShare = { sharePdf(it) },
            onDownload = { downloadPdfToDownloads(it) },
            onDelete = { deletePdf(it) }
        )
        binding.rvRecentPdfs.layoutManager = LinearLayoutManager(this)
        binding.rvRecentPdfs.adapter = recentAdapter

        binding.btnSelectImages.setOnClickListener {
            pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnCreatePdf.setOnClickListener {
            requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        }


        loadRecentPdfs()
    }

    override fun onImagesFinalized(finalUris: List<Uri>, pdfName: String) {
        selectedUris.clear()
        selectedUris.addAll(finalUris)

        if (selectedUris.isEmpty()) return

        val safeName = pdfName.trim()
            .ifEmpty { "PDF_${System.currentTimeMillis()}" }
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")

        val pdfFile = PdfCreator.createPdfFromImages(this, selectedUris, safeName)
        keepOnlyLatestPdfs(6)
        Toast.makeText(this, "PDF saved: ${pdfFile.name}", Toast.LENGTH_SHORT).show()
        loadRecentPdfs()
        selectedUris.clear()
    }



    override fun onAddMoreRequested() {
        // dialog se add more दबाया तो picker reopen
        pickMultipleMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    private fun keepOnlyLatestPdfs(maxKeep: Int = 6) {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PDFMaker")
        if (!dir.exists()) return

        val files = dir.listFiles { f ->
            f.isFile && f.name.endsWith(".pdf", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: return

        if (files.size <= maxKeep) return

        // delete older (index maxKeep .. end)
        files.drop(maxKeep).forEach { it.delete() }
    }

    private fun startUCrop(inputUri: Uri) {
        val destination = Uri.fromFile(
            File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
        )

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setFreeStyleCropEnabled(true)
            setToolbarTitle("Crop")
        }

        val intent = UCrop.of(inputUri, destination)
            .withOptions(options)
            .getIntent(this)

        ucropLauncher.launch(intent)
    }

     fun loadRecentPdfs() {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PDFMaker")
        if (!dir.exists()) dir.mkdirs()

        val list: List<File> =
            dir.listFiles { f -> f.isFile && f.name.endsWith(".pdf", true) }
                ?.sortedByDescending { it.lastModified() }
                ?.take(6)
                ?.toList()
                ?: emptyList()

        recentAdapter.submitList(list)
    }

    private fun openPdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Open PDF"))
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    private fun deletePdf(file: File) {
        val ok = file.delete()
        if (ok) {
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            loadRecentPdfs()
        } else {
            Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
        }
    }
    private fun openCamera() {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "PDFMakerCamera")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    private fun downloadPdfToDownloads(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/PDFMaker"
                )
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
                return
            }

            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }

            Toast.makeText(this, "Saved to Downloads/PDFMaker", Toast.LENGTH_SHORT).show()
        } else {
            pendingDownloadFile = file
            createDocument.launch(file.name)
        }
    }
}
