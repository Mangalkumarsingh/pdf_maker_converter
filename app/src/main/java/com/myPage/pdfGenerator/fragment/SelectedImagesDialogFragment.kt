package com.myPage.pdfGenerator.fragment

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.myPage.pdfGenerator.R
import com.myPage.pdfGenerator.adapter.SelectedImagesDialogAdapter
import com.myPage.pdfGenerator.databinding.DialogSelectedImagesBinding
import com.yalantis.ucrop.UCrop
import java.io.File

class SelectedImagesDialogFragment : DialogFragment() {

    interface Listener {
        fun onImagesFinalized(finalUris: List<Uri>, pdfName: String, compressLevel: com.myPage.pdfGenerator.utils.CompressLevelBar.Level)
        fun onAddMoreRequested()
    }

    private var listener: Listener? = null
    private var editingIndex: Int = -1

    private var uris = arrayListOf<Uri>()
    private lateinit var binding: DialogSelectedImagesBinding
    private lateinit var adapter: SelectedImagesDialogAdapter
    private val snapHelper = PagerSnapHelper()

    private val pickMoreMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { newUris ->
            if (newUris.isNotEmpty()) {
                val filtered = newUris.filterNot { it in uris }

                if (filtered.isNotEmpty()) {
                    val start = uris.size
                    uris.addAll(filtered)
                    adapter.addItems(filtered)
                    binding.rvImages.post {
                        binding.rvImages.smoothScrollToPosition(start)
                        updatePageIndicator()
                    }
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
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
        uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelableArrayList(ARG_URIS, Uri::class.java) ?: arrayListOf()
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList(ARG_URIS) ?: arrayListOf()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DialogSelectedImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Top Toolbar
        binding.toolbar.setNavigationOnClickListener { dismiss() }
        binding.btnDelete.setOnClickListener {
            val pos = getCurrentPosition()
            if (pos in uris.indices) {
                uris.removeAt(pos)
                adapter.removeAt(pos)
                if (uris.isEmpty()) {
                    dismiss()
                } else {
                    updatePageIndicator()
                }
            }
        }

        // Horizontal RecyclerView setup with PagerSnapHelper
        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvImages.layoutManager = layoutManager
        snapHelper.attachToRecyclerView(binding.rvImages)

        adapter = SelectedImagesDialogAdapter()
        binding.rvImages.adapter = adapter
        adapter.submitList(uris)

        binding.rvImages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updatePageIndicator()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updatePageIndicator()
            }
        })

        updatePageIndicator()

        // 1. Cancel action
        val cancelAction = View.OnClickListener { dismiss() }
        binding.cardCancel.setOnClickListener(cancelAction)
        binding.btnCancel.setOnClickListener(cancelAction)

        // 2. Edit action (crops currently visible image)
        val editAction = View.OnClickListener {
            val pos = getCurrentPosition()
            if (pos in uris.indices) {
                editingIndex = pos
                startUCrop(uris[pos])
            }
        }
        binding.cardEdit.setOnClickListener(editAction)
        binding.btnEdit.setOnClickListener(editAction)

        // 3. Add More action
        val addAction = View.OnClickListener {
            pickMoreMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.cardAdd.setOnClickListener(addAction)
        binding.btnAddMore.setOnClickListener(addAction)

        // 4. Done action
        val doneAction = View.OnClickListener {
            if (uris.isEmpty()) {
                dismiss()
                return@OnClickListener
            }
            showRenameDialogCustom("MyPDF_${System.currentTimeMillis()}") { enteredName ->
                val currentList = adapter.currentList()
                com.myPage.pdfGenerator.utils.CompressDialogHelper.showCompressDialog(
                    requireContext(),
                    currentList,
                    viewLifecycleOwner.lifecycleScope,
                    onConfirmed = { compressLevel ->
                        listener?.onImagesFinalized(currentList, enteredName, compressLevel)
                        dismiss()
                    },
                    onCancelled = {
                        // When user cancels compression dialog, PDF is NOT created
                    }
                )
            }
        }
        binding.cardDone.setOnClickListener(doneAction)
        binding.btnDone.setOnClickListener(doneAction)
    }

    private fun getCurrentPosition(): Int {
        val layoutManager = binding.rvImages.layoutManager as? LinearLayoutManager ?: return 0
        val snapView = snapHelper.findSnapView(layoutManager)
        if (snapView != null) {
            val pos = layoutManager.getPosition(snapView)
            if (pos != RecyclerView.NO_POSITION) return pos
        }
        val firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        if (firstVisible != RecyclerView.NO_POSITION) return firstVisible
        val firstPos = layoutManager.findFirstVisibleItemPosition()
        return if (firstPos != RecyclerView.NO_POSITION) firstPos else 0
    }

    private fun updatePageIndicator() {
        val total = uris.size
        if (total == 0) {
            binding.tvPageIndicator.text = "0 of 0"
            return
        }
        val current = (getCurrentPosition() + 1).coerceIn(1, total)
        binding.tvPageIndicator.text = "$current of $total"
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