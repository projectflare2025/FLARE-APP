package com.example.flare_capstone.views.fragment.investigator

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.FragmentInReport3Binding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.util.Base64

class InReport3Fragment : Fragment() {

    private var _binding: FragmentInReport3Binding? = null
    private val binding get() = _binding!!

    private val formViewModel: InvestigatorFormViewModel by activityViewModels()

    // Pick image
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            handleSelectedImage(uri)
        }

    // Pick file
    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            handleSelectedFile(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInReport3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Show last image (if any) & file name (if any)
//
        if (formViewModel.evidenceImagesBase64.isNotEmpty()) {
            // just preview the last one
            val lastBase64 = formViewModel.evidenceImagesBase64.last()
            val bytes = Base64.decode(lastBase64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            binding.investigationImagePreview.setImageBitmap(bmp)
        }

        binding.uploadedFileNameText.text =
            formViewModel.evidenceFileName ?: "No file selected"

        binding.btnSelectInvestigationImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnSelectInvestigationFile.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        binding.btnNextReport3.setOnClickListener {
            // Data already stored in ViewModel as user selects
            findNavController().navigate(R.id.action_to_report4)
        }
    }

    private fun handleSelectedImage(uri: Uri) {
        val tmpFile = createTempFileFromUri(uri) ?: return

        val base64 = compressAndEncodeBase64(tmpFile)
        if (base64.isNotEmpty()) {
            formViewModel.evidenceImagesBase64.add(base64)

            // Preview the latest
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            binding.investigationImagePreview.setImageBitmap(bmp)
        }

        tmpFile.delete()
    }

    private fun handleSelectedFile(uri: Uri) {
        val name = queryFileName(uri) ?: "Selected file"
        formViewModel.evidenceFileName = name
        binding.uploadedFileNameText.text = name
    }

    private fun queryFileName(uri: Uri): String? {
        val cursor = requireContext().contentResolver
            .query(uri, null, null, null, null) ?: return null
        cursor.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex == -1) return null
            if (!it.moveToFirst()) return null
            return it.getString(nameIndex)
        }
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("inv_img_", ".tmp", requireContext().cacheDir)
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    // ========= Your compression → Base64 helper =========
    private fun compressAndEncodeBase64(
        file: File,
        maxDim: Int = 1024,
        initialQuality: Int = 75,
        targetBytes: Int = 400 * 1024
    ): String {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(file).use { BitmapFactory.decodeStream(it, null, opts) }

        fun computeSampleSize(w: Int, h: Int, maxDim: Int): Int {
            var sample = 1
            var width = w
            var height = h
            while (width / 2 >= maxDim || height / 2 >= maxDim) {
                width /= 2; height /= 2; sample *= 2
            }
            return sample
        }

        val inSample = computeSampleSize(opts.outWidth, opts.outHeight, maxDim)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = inSample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded =
            FileInputStream(file).use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: return ""

        val w = decoded.width
        val h = decoded.height
        val scale = maxOf(1f, maxOf(w, h) / maxDim.toFloat())
        val outW = (w / scale).toInt().coerceAtLeast(1)
        val outH = (h / scale).toInt().coerceAtLeast(1)
        val scaled = if (w > maxDim || h > maxDim) {
            Bitmap.createScaledBitmap(decoded, outW, outH, true)
        } else decoded
        if (scaled !== decoded) decoded.recycle()

        val baos = ByteArrayOutputStream()
        var q = initialQuality
        scaled.compress(Bitmap.CompressFormat.JPEG, q, baos)
        var data = baos.toByteArray()
        while (data.size > targetBytes && q > 40) {
            baos.reset()
            q -= 10
            scaled.compress(Bitmap.CompressFormat.JPEG, q, baos)
            data = baos.toByteArray()
        }
        if (!scaled.isRecycled) scaled.recycle()
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
