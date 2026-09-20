package com.myPage.pdfGenerator.utils

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr
import java.math.BigInteger

//
//private data class PageSpec(
//    val widthTwips: Int,
//    val heightTwips: Int,
//    val marginLeftTwips: Int,
//    val marginRightTwips: Int,
//    val marginTopTwips: Int,
//    val marginBottomTwips: Int
//)
//{
//    val usableWidthInch: Double get() = (widthTwips - marginLeftTwips - marginRightTwips) / 1440.0
//    val usableHeightInch: Double get() = (heightTwips - marginTopTwips - marginBottomTwips) / 1440.0
//
//
//
//}
//
//private fun applyA4Page(docx: XWPFDocument): PageSpec {
//    val a4Width = 11906   // twips
//    val a4Height = 16838  // twips
//
//    // margin: 0.35 inch ≈ 504 twips (you can reduce more if you want bigger image)
//    val m = 504
//
//    val body = docx.document.body
//    val sectPr: CTSectPr = if (body.isSetSectPr) body.sectPr else body.addNewSectPr()
//
//    val pgSz: CTPageSz = if (sectPr.isSetPgSz) sectPr.pgSz else sectPr.addNewPgSz()
//    pgSz.w = BigInteger.valueOf(a4Width.toLong())
//    pgSz.h = BigInteger.valueOf(a4Height.toLong())
//
//    val pgMar: CTPageMar = if (sectPr.isSetPgMar) sectPr.pgMar else sectPr.addNewPgMar()
//    pgMar.left = BigInteger.valueOf(m.toLong())
//    pgMar.right = BigInteger.valueOf(m.toLong())
//    pgMar.top = BigInteger.valueOf(m.toLong())
//    pgMar.bottom = BigInteger.valueOf(m.toLong())
//
//    return PageSpec(a4Width, a4Height, m, m, m, m)
//}
//
//
