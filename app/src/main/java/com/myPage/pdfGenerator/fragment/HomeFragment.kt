package com.myPage.pdfGenerator.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myPage.pdfGenerator.Activity.DocsToPdfActivity
import com.myPage.pdfGenerator.Activity.HomeActivity
import com.myPage.pdfGenerator.Activity.PdfToDocsActivity
import com.myPage.pdfGenerator.MainActivity.Companion.TOOL_DOCS_TO_PDF
import com.myPage.pdfGenerator.MainActivity.Companion.TOOL_IMG_TO_PDF
import com.myPage.pdfGenerator.MainActivity.Companion.TOOL_PDF_TO_DOCS
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.adapter.ToolsAdapter
import com.myPage.pdfGenerator.model.ToolItem

class HomeFragment : Fragment() {

    private lateinit var adapter: ToolsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        val rvTools = view.findViewById<RecyclerView>(R.id.rvTools)

        adapter = ToolsAdapter { tool ->
            when (tool.id) {
                TOOL_PDF_TO_DOCS -> {
                    startActivity(Intent(requireContext(), PdfToDocsActivity::class.java))
                }
                TOOL_DOCS_TO_PDF -> {
                    startActivity(Intent(requireContext(), DocsToPdfActivity::class.java))
                }
                TOOL_IMG_TO_PDF -> {
                    startActivity(Intent(requireContext(), HomeActivity::class.java))
                }
            }
        }

        rvTools.layoutManager = GridLayoutManager(requireContext(), 3)
        rvTools.adapter = adapter

        adapter.submitList(
            listOf(
                ToolItem(TOOL_IMG_TO_PDF, "IMG -> PDF", R.drawable.image_pdf_img),
                ToolItem(TOOL_PDF_TO_DOCS, "PDF -> DOCS", R.drawable.docs),
                ToolItem(TOOL_DOCS_TO_PDF, "DOCS -> PDF", R.drawable.pdf_img),
            )
        )

        return view
    }
}
