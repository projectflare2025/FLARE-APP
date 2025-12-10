package com.example.flare_capstone.views.fragment.investigator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.FragmentInReport5Binding

class InReport5Fragment : Fragment() {

    private var _binding: FragmentInReport5Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInReport5Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // BACK BUTTON
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // COMPLETE REPORT BUTTON
        binding.btnCompleteReport.setOnClickListener {

            // Collect inputs
            val establishmentName = binding.establishmentNameInput.text.toString().trim()
            val ownerName = binding.ownerInput.text.toString().trim()
            val occupantName = binding.occupantInput.text.toString().trim()
            val landArea = binding.landAreaInvolvedInput.text.toString().trim()

            // TODO: You can store these in a shared ViewModel or send to Firebase

            // Example: simple validation (optional)
            if (establishmentName.isEmpty()) {
                binding.establishmentNameInput.error = "Required"
                return@setOnClickListener
            }

            // After saving data → navigate to home
            findNavController().navigate(R.id.inHomeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
