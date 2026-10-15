package com.myPage.pdfGenerator.Activity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import com.myPage.pdfGenerator.utils.CompressLevelBar
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.ByteArrayOutputStream
import java.io.File

object PdfCreator {

    fun createPdfFromImages(
        context: Context,
        imageUris: List<Uri>,
        fileName: String,
        level: CompressLevelBar.Level = CompressLevelBar.Level.HIGH
    ): File {
        PDFBoxResourceLoader.init(context.applicationContext)
        val document = PDDocument()

        val pageWidth = 595.28f  // Standard A4 width in points
        val pageHeight = 841.89f // Standard A4 height in points
        val margin = 20f

        try {
            for (uri in imageUris) {
                val jpegBytes = getCompressedJpegBytes(context, uri, level) ?: continue
                val pdImage = JPEGFactory.createFromByteArray(document, jpegBytes)

                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                document.addPage(page)

                val maxW = pageWidth - margin * 2f
                val maxH = pageHeight - margin * 2f

                val scale = minOf(maxW / pdImage.width.toFloat(), maxH / pdImage.height.toFloat())
                val drawW = pdImage.width * scale
                val drawH = pdImage.height * scale

                // In PDF coordinates, origin (0, 0) is bottom-left
                val x = (pageWidth - drawW) / 2f
                val y = (pageHeight - drawH) / 2f

                PDPageContentStream(document, page).use { cs ->
                    cs.drawImage(pdImage, x, y, drawW, drawH)
                }
            }

            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PDFMaker")
            if (!dir.exists()) dir.mkdirs()

            val outFile = File(dir, "${fileName}.pdf")
            document.save(outFile)
            return outFile
        } finally {
            document.close()
        }
    }

    /**
     * Estimates PDF size in bytes for a given compression level without writing full file to disk.
     */
    fun estimatePdfSize(
        context: Context,
        imageUris: List<Uri>,
        level: CompressLevelBar.Level
    ): Long {
        if (imageUris.isEmpty()) return 0L

        var sampledBytes = 0L
        val sampleCount = minOf(imageUris.size, 1)

        for (i in 0 until sampleCount) {
            val bytes = getCompressedJpegBytes(context, imageUris[i], level)
            if (bytes != null) {
                sampledBytes += bytes.size
            }
        }

        val avgBytesPerPage = if (sampleCount > 0) sampledBytes / sampleCount else 0L
        // PDF structure overhead: ~1.5KB per page object + ~2KB header/catalog/trailer
        val totalBytes = (avgBytesPerPage * imageUris.size) + (imageUris.size * 1536L) + 2048L
        return totalBytes
    }

    private fun getCompressedJpegBytes(
        context: Context,
        uri: Uri,
        level: CompressLevelBar.Level
    ): ByteArray? {
        try {
            // 1) Read image bounds
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return null

            // Target maximum dimension based on level scaleFactor
            val maxDimension = (1800 * level.scaleFactor).toInt().coerceAtLeast(600)

            // Calculate sample size for initial downsampling to avoid high memory consumption
            var inSampleSize = 1
            val maxOrig = maxOf(origW, origH)
            while ((maxOrig / (inSampleSize * 2)) >= maxDimension) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            // 2) Check EXIF rotation and rotate if needed
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exif = ExifInterface(inputStream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    val degrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    if (degrees != 0f) {
                        val matrix = Matrix().apply { postRotate(degrees) }
                        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        if (rotated != bitmap) {
                            bitmap.recycle()
                            bitmap = rotated
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3) Smooth downscale to exact target dimension if still larger
            val currentMax = maxOf(bitmap.width, bitmap.height)
            if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax.toFloat()
                val targetW = (bitmap.width * scale).toInt().coerceAtLeast(100)
                val targetH = (bitmap.height * scale).toInt().coerceAtLeast(100)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            // 4) Ensure white background if image has alpha transparency (prevents black background)
            val finalBitmap = if (bitmap.hasAlpha()) {
                val opaqueBmp = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(opaqueBmp)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
                opaqueBmp
            } else {
                bitmap
            }

            // 5) Compress to JPEG bytes with requested quality
            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, level.quality, baos)
            finalBitmap.recycle()

            return baos.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
