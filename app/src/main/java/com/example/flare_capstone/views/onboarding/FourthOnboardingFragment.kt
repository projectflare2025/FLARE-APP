package com.example.flare_capstone.views.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.flare_capstone.databinding.FourthFragmentOnboardingBinding

class FourthOnboardingFragment : Fragment() {

    private lateinit var binding: FourthFragmentOnboardingBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FourthFragmentOnboardingBinding.inflate(inflater, container, false)

        binding.getStartedBtn.setOnClickListener {
            requireActivity().finish()  // closes onboarding & returns to login/main
        }

        return binding.root
    }
}
