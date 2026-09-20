package com.myPage.pdfGenerator

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import com.myPage.pdfGenerator.Activity.BaseActivity
import com.myPage.pdfGenerator.Activity.DocsToPdfActivity
import com.myPage.pdfGenerator.Activity.HomeActivity
import com.myPage.pdfGenerator.Activity.PdfToDocsActivity
import com.myPage.pdfGenerator.adapter.ToolsAdapter
import com.myPage.pdfGenerator.databinding.ActivityMainBinding
import com.myPage.pdfGenerator.model.ToolItem

class MainActivity : BaseActivity() {


        private lateinit var binding: ActivityMainBinding
        private lateinit var adapter: ToolsAdapter

        companion object {
            const val TOOL_PDF_TO_DOCS = 1
            const val TOOL_DOCS_TO_PDF = 2
            const val TOOL_IMG_TO_PDF = 3
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityMainBinding.inflate(layoutInflater)

            setContentView(binding.root)
//            WindowCompat.setDecorFitsSystemWindows(window, true)
//            window.statusBarColor = ContextCompat.getColor(this, R.color.btn_color)


            val nav = binding.toolbar.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.ivNav)
//            val title = binding.toolbar.findViewById<TextView>(R.id.tvTitle)
//
//            title.text = "PDF Maker"

            nav.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            adapter = ToolsAdapter { tool ->
                when (tool.id) {
                    TOOL_PDF_TO_DOCS -> {
                        startActivity(Intent(this, PdfToDocsActivity::class.java))
                    }
                    TOOL_DOCS_TO_PDF -> {
                         startActivity(Intent(this, DocsToPdfActivity::class.java))
                    }
                    TOOL_IMG_TO_PDF -> {
                         startActivity(Intent(this, HomeActivity::class.java))
                    }
                }
            }

            binding.rvTools.layoutManager = GridLayoutManager(this, 3)
            binding.rvTools.adapter = adapter

            adapter.submitList(
                listOf(
                    ToolItem(TOOL_IMG_TO_PDF, "IMG → PDF", R.drawable.image_to_pdf),
                    ToolItem(TOOL_PDF_TO_DOCS, "PDF → DOCS", R.drawable.docs),
                    ToolItem(TOOL_DOCS_TO_PDF, "DOCS → PDF", R.drawable.pdf_img),
                )
            )
        }

}