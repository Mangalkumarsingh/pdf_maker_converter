package com.myPage.pdfGenerator.Activity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

object PdfCreator {

    fun createPdfFromImages(context: Context, imageUris: List<Uri>, fileName: String): File {
        val pdf = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val margin = 24

        imageUris.forEachIndexed { index, uri ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdf.startPage(pageInfo)

            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val bmp = decodeScaledBitmap(context, uri, pageWidth, pageHeight)
            if (bmp != null) {
                drawFitCenter(canvas, bmp, pageWidth, pageHeight, margin)
                bmp.recycle()
            }

            pdf.finishPage(page)
        }

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PDFMaker")
        if (!dir.exists()) dir.mkdirs()

        val outFile = File(dir, "${fileName}.pdf")

        FileOutputStream(outFile).use { out ->
            pdf.writeTo(out)
        }
        pdf.close()

        return outFile
    }

    private fun drawFitCenter(canvas: Canvas, bmp: Bitmap, pageW: Int, pageH: Int, margin: Int) {
        val maxW = pageW - margin * 2f
        val maxH = pageH - margin * 2f

        val scale = minOf(maxW / bmp.width, maxH / bmp.height)
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale

        val left = (pageW - drawW) / 2f
        val top = (pageH - drawH) / 2f

        val dest = RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(bmp, null, dest, Paint(Paint.ANTI_ALIAS_FLAG))
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        // 1) bounds
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }

        // 2) sample
        val sample = calculateInSampleSize(opts, reqW * 2, reqH * 2) // little sharper
        val opts2 = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts2)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (h, w) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (h > reqHeight || w > reqWidth) {
            var halfH = h / 2
            var halfW = w / 2
            while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
