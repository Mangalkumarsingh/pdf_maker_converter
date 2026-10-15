package com.myPage.pdfGenerator.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.myPage.pdfGenerator.Activity.PdfCreator
import com.myPage.pdfGenerator.databinding.DialogCompressPdfBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

object CompressDialogHelper {

    fun showCompressDialog(
        context: Context,
        uris: List<Uri>,
        scope: CoroutineScope,
        onConfirmed: (CompressLevelBar.Level) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val binding = DialogCompressPdfBinding.inflate(LayoutInflater.from(context))

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.imageLogo.setImageResource(com.myPage.pdfGenerator.R.drawable.image_pdf_img)

        var selectedLevel = CompressLevelBar.Level.HIGH
        binding.compressLevelBar.setLevel(selectedLevel)
        updateLevelText(binding, selectedLevel)

        val cachedSizes = mutableMapOf<CompressLevelBar.Level, Long>()

        // 1. Instant baseline estimation from image files (0ms, no waiting)
        val totalRawBytes = uris.sumOf { CommonUtil.getUriFileSize(context, it) }
        val perImageFallbackHigh = 280L * 1024L
        val estimatedHigh = if (totalRawBytes > 0) {
            (totalRawBytes * 0.65f).toLong().coerceIn(uris.size * 120L * 1024L, uris.size * 700L * 1024L)
        } else {
            uris.size * perImageFallbackHigh
        }

        cachedSizes[CompressLevelBar.Level.HIGH] = estimatedHigh
        cachedSizes[CompressLevelBar.Level.MEDIUM] = (estimatedHigh * 0.48f).toLong().coerceAtLeast(1024L)
        cachedSizes[CompressLevelBar.Level.LOW] = (estimatedHigh * 0.22f).toLong().coerceAtLeast(1024L)

        // Show High size immediately when dialog opens!
        binding.tvCurrentSize.text = formatFileSize(cachedSizes[selectedLevel] ?: estimatedHigh)

        // 2. Refine in background with actual sample compression
        scope.launch(Dispatchers.IO) {
            if (uris.isNotEmpty()) {
                val sampleHigh = PdfCreator.estimatePdfSize(context, uris, CompressLevelBar.Level.HIGH)
                if (sampleHigh > 0) {
                    cachedSizes[CompressLevelBar.Level.HIGH] = sampleHigh
                    cachedSizes[CompressLevelBar.Level.MEDIUM] = (sampleHigh * 0.48f).toLong().coerceAtLeast(1024L)
                    cachedSizes[CompressLevelBar.Level.LOW] = (sampleHigh * 0.22f).toLong().coerceAtLeast(1024L)

                    withContext(Dispatchers.Main) {
                        binding.tvCurrentSize.text = formatFileSize(cachedSizes[selectedLevel] ?: sampleHigh)
                    }
                }
            }
        }

        // 3. Instant update when changing between High, Medium, Low
        binding.compressLevelBar.setOnLevelChangeListener { level ->
            selectedLevel = level
            updateLevelText(binding, level)
            val size = cachedSizes[level] ?: when (level) {
                CompressLevelBar.Level.LOW -> (estimatedHigh * 0.22f).toLong().coerceAtLeast(1024L)
                CompressLevelBar.Level.MEDIUM -> (estimatedHigh * 0.48f).toLong().coerceAtLeast(1024L)
                CompressLevelBar.Level.HIGH -> estimatedHigh
            }
            binding.tvCurrentSize.text = formatFileSize(size)
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            onCancelled()
        }

        binding.btnOk.setOnClickListener {
            dialog.dismiss()
            onConfirmed(selectedLevel)
        }

        dialog.show()
    }

    fun showDocumentCompressDialog(
        context: Context,
        title: String = "Compress Document",
        sizeLabel: String = "Estimated File Size",
        scope: CoroutineScope,
        fallbackHighBytes: Long,
        estimateSizes: suspend () -> Map<CompressLevelBar.Level, Long>,
        onConfirmed: (CompressLevelBar.Level) -> Unit,
        onCancelled: () -> Unit = {},
        logoResId: Int = com.myPage.pdfGenerator.R.drawable.pdf
    ) {
        val binding = DialogCompressPdfBinding.inflate(LayoutInflater.from(context))

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.imageLogo.setImageResource(logoResId)
        binding.tvCompressTitle.text = title
        binding.tvSizeLabel.text = sizeLabel

        var selectedLevel = CompressLevelBar.Level.HIGH
        binding.compressLevelBar.setLevel(selectedLevel)
        updateLevelText(binding, selectedLevel)

        val cachedSizes = mutableMapOf<CompressLevelBar.Level, Long>()

        // 1. Instant baseline estimation (0ms, no waiting)
        val safeFallback = if (fallbackHighBytes > 0L) fallbackHighBytes else 512L * 1024L
        cachedSizes[CompressLevelBar.Level.HIGH] = safeFallback
        cachedSizes[CompressLevelBar.Level.MEDIUM] = (safeFallback * 0.52f).toLong().coerceAtLeast(1024L)
        cachedSizes[CompressLevelBar.Level.LOW] = (safeFallback * 0.25f).toLong().coerceAtLeast(1024L)

        // Show High size immediately when dialog opens!
        binding.tvCurrentSize.text = formatFileSize(cachedSizes[selectedLevel] ?: safeFallback)

        // 2. Refine in background with actual sample / calculation
        scope.launch(Dispatchers.IO) {
            try {
                val calculated = estimateSizes()
                if (calculated.isNotEmpty()) {
                    cachedSizes.putAll(calculated)
                    withContext(Dispatchers.Main) {
                        binding.tvCurrentSize.text = formatFileSize(cachedSizes[selectedLevel] ?: safeFallback)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Instant update when changing between High, Medium, Low
        binding.compressLevelBar.setOnLevelChangeListener { level ->
            selectedLevel = level
            updateLevelText(binding, level)
            val size = cachedSizes[level] ?: when (level) {
                CompressLevelBar.Level.LOW -> (safeFallback * 0.25f).toLong().coerceAtLeast(1024L)
                CompressLevelBar.Level.MEDIUM -> (safeFallback * 0.52f).toLong().coerceAtLeast(1024L)
                CompressLevelBar.Level.HIGH -> safeFallback
            }
            binding.tvCurrentSize.text = formatFileSize(size)
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            onCancelled()
        }

        binding.btnOk.setOnClickListener {
            dialog.dismiss()
            onConfirmed(selectedLevel)
        }

        dialog.show()
    }

    fun showGenericCompressDialog(
        context: Context,
        originalFileSizeBytes: Long,
        title: String = "Compress File",
        onConfirmed: (CompressLevelBar.Level) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val binding = DialogCompressPdfBinding.inflate(LayoutInflater.from(context))

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.tvCompressTitle.text = title

        var selectedLevel = CompressLevelBar.Level.HIGH
        binding.compressLevelBar.setLevel(selectedLevel)
        updateLevelText(binding, selectedLevel)

        // Safe positive size from original file
        val safeOriginalSize = if (originalFileSizeBytes > 0L) originalFileSizeBytes else 512L * 1024L

        fun updateGenericSize(level: CompressLevelBar.Level) {
            val estimatedBytes = when (level) {
                CompressLevelBar.Level.LOW -> (safeOriginalSize * 0.32f).toLong().coerceAtLeast(1024L)
                CompressLevelBar.Level.MEDIUM -> (safeOriginalSize * 0.58f).toLong().coerceAtLeast(1024L)
                CompressLevelBar.Level.HIGH -> (safeOriginalSize * 0.95f).toLong().coerceAtLeast(1024L)
            }
            binding.tvCurrentSize.text = formatFileSize(estimatedBytes)
        }

        // Display High size immediately when dialog opens!
        updateGenericSize(selectedLevel)

        // Instant update when changing between High, Medium, Low
        binding.compressLevelBar.setOnLevelChangeListener { level ->
            selectedLevel = level
            updateLevelText(binding, level)
            updateGenericSize(level)
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            onCancelled()
        }

        binding.btnOk.setOnClickListener {
            dialog.dismiss()
            onConfirmed(selectedLevel)
        }

        dialog.show()
    }

    private fun updateLevelText(binding: DialogCompressPdfBinding, level: CompressLevelBar.Level) {
        binding.tvQualityTag.text = level.title
        when (level) {
            CompressLevelBar.Level.LOW -> {
                binding.tvLevelDescription.text = "Low Quality (Smallest file size, lower resolution)"
            }
            CompressLevelBar.Level.MEDIUM -> {
                binding.tvLevelDescription.text = "Medium Quality (Balanced file size and clarity)"
            }
            CompressLevelBar.Level.HIGH -> {
                binding.tvLevelDescription.text = "High Quality (Largest file size, best visual clarity)"
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }
}
