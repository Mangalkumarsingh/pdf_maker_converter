package com.myPage.pdfGenerator.fragment

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.adapter.SelectedImagesDialogAdapter
import com.myPage.pdfGenerator.databinding.DialogSelectedImagesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yalantis.ucrop.UCrop
import java.io.File

class SelectedImagesDialogFragment : DialogFragment() {

    interface Listener {
        fun onImagesFinalized(finalUris: List<Uri>, pdfName: String)
        fun onAddMoreRequested()
    }

    private var listener: Listener? = null
    private var editingIndex: Int = -1

    private var uris = arrayListOf<Uri>()
    private lateinit var binding: DialogSelectedImagesBinding
    private lateinit var adapter: SelectedImagesDialogAdapter
    private val pickMoreMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { newUris ->
            if (newUris.isNotEmpty()) {
                val filtered = newUris.filterNot { it in uris }

                if (filtered.isNotEmpty()) {
                    uris.addAll(filtered)
                    adapter.addItems(filtered)
                }
            }
        }

    private val ucropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val out = UCrop.getOutput(result.data!!)
                val idx = editingIndex
                if (out != null && idx in uris.indices) {
                    uris[idx] = out
                    adapter.updateItem(idx, out)
                }
            }
            editingIndex = -1
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }


    override fun onDetach() {
        listener = null
        super.onDetach()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        uris = (arguments?.getParcelableArrayList(ARG_URIS) ?: arrayListOf())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DialogSelectedImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        adapter = SelectedImagesDialogAdapter(
            onEdit = { uri, pos ->
                editingIndex = pos
                startUCrop(uri)
            },
            onDelete = { pos ->
                if (pos in uris.indices) {
                    uris.removeAt(pos)
                    adapter.removeAt(pos)
                    if (uris.isEmpty()) dismiss()
                }
            }
        )

        binding.rvImages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvImages.adapter = adapter
        adapter.submitList(uris)

        binding.btnAddMore.setOnClickListener {
            pickMoreMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnDone.setOnClickListener {
            showRenameDialogCustom("MyPDF_${System.currentTimeMillis()}") { enteredName ->
                listener?.onImagesFinalized(adapter.currentList(), enteredName)
                dismiss()
            }
        }

    }

    private fun showRenameDialogCustom(
        defaultName: String,
        onOk: (String) -> Unit
    ) {
        val v = layoutInflater.inflate(R.layout.dialog_rename, null, false)

        val et = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etName)
        val btnOk = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOk)
        val btnCancel = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        et.setText(defaultName)
        et.setSelection(et.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(requireActivity())
            .setView(v)
            .create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnOk.setOnClickListener {
            val name = et.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                et.error = "Enter name"
                return@setOnClickListener
            }
            onOk(name)
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun startUCrop(inputUri: Uri) {
        val destination = Uri.fromFile(
            File(requireContext().cacheDir, "crop_${System.currentTimeMillis()}.jpg")
        )

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setFreeStyleCropEnabled(true)
            setToolbarTitle("Crop")
        }

        val intent = UCrop.of(inputUri, destination)
            .withOptions(options)
            .getIntent(requireContext())

        ucropLauncher.launch(intent)
    }
    companion object {
        private const val ARG_URIS = "arg_uris"
        fun newInstance(list: ArrayList<Uri>): SelectedImagesDialogFragment {
            return SelectedImagesDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_URIS, list)

                }
            }
        }
    }
}