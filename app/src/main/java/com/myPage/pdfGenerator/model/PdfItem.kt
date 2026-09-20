package com.myPage.pdfGenerator.model

import java.io.File

data class PdfItem(
    val file: File
) {
    val name: String get() = file.name
    val lastModified: Long get() = file.lastModified()
}
