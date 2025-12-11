package com.example.flare_capstone.views.fragment.investigator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.flare_capstone.databinding.FragmentInPorfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.example.flare_capstone.R
import com.example.flare_capstone.views.activity.AuthActivity

class InProfileFragment : Fragment() {

    private var _binding: FragmentInPorfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInPorfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogout.setOnClickListener {

            // Firebase logout
            FirebaseAuth.getInstance().signOut()

            // Clear session (optional but recommended)
            val prefs = requireContext().getSharedPreferences("flare_session", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            // Go back to AuthActivity
            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            // Finish current activity so user cannot go back
            requireActivity().finish()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
