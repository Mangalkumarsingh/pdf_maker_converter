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

        val pageWidth = 595.28f
        val pageHeight = 841.89f
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



    fun estimatePdfSizes(
        context: Context,
        imageUris: List<Uri>
    ): Map<CompressLevelBar.Level, Long> {
        if (imageUris.isEmpty()) return emptyMap()

        val result = mutableMapOf<CompressLevelBar.Level, Long>()
        val sampleCount = minOf(imageUris.size, 2)

        for (level in CompressLevelBar.Level.values()) {
            var sampledBytes = 0L
            var validSamples = 0

            for (i in 0 until sampleCount) {
                val bytes = getCompressedJpegBytes(context, imageUris[i], level)
                if (bytes != null) {
                    sampledBytes += bytes.size
                    validSamples++
                }
            }

            val avgBytesPerPage = if (validSamples > 0) sampledBytes / validSamples else 0L
            // PDF structure overhead: ~1.5KB per page object + ~2KB header/catalog/trailer
            val totalBytes = (avgBytesPerPage * imageUris.size) + (imageUris.size * 1536L) + 2048L
            result[level] = totalBytes
        }

        return result
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

            val maxDimension = (1800 * level.scaleFactor).toInt().coerceAtLeast(600)

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
