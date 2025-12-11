package com.example.flare_capstone.views.fragment.investigator

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.FragmentInReport4Binding
import java.util.*

class InReport4Fragment : Fragment() {

    private var _binding: FragmentInReport4Binding? = null
    private val binding get() = _binding!!

    private val formViewModel: InvestigatorFormViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInReport4Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Prefill existing values
        prefillFromViewModel()

        // Time pickers
        binding.timeDepartedInput.setOnClickListener {
            showTimePicker { timeStr ->
                binding.timeDepartedInput.setText(timeStr)
            }
        }

        binding.timeArrivalInput.setOnClickListener {
            showTimePicker { timeStr ->
                binding.timeArrivalInput.setText(timeStr)
            }
        }

        binding.fireUnderControlTimeInput.setOnClickListener {
            showTimePicker { timeStr ->
                binding.fireUnderControlTimeInput.setText(timeStr)
            }
        }

        binding.fireOutTimeInput.setOnClickListener {
            showTimePicker { timeStr ->
                binding.fireOutTimeInput.setText(timeStr)
            }
        }

        binding.btnFinishReport.setOnClickListener {
            saveStep4ToViewModel()
            findNavController().navigate(R.id.action_to_report5)
        }
    }

    private fun showTimePicker(onSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val dialog = TimePickerDialog(
            requireContext(),
            { _, h, m ->
                val formatted = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                onSelected(formatted)
            },
            hour,
            minute,
            true // 24-hour format; set false if you want AM/PM
        )
        dialog.show()
    }

    private fun prefillFromViewModel() {
        binding.timeDepartedInput.setText(formViewModel.timeDeparted ?: "")
        binding.timeArrivalInput.setText(formViewModel.timeArrival ?: "")
        binding.fireUnderControlTimeInput.setText(formViewModel.fireUnderControlTime ?: "")
        binding.fireOutTimeInput.setText(formViewModel.fireOutTime ?: "")
        binding.groundCommanderInput.setText(formViewModel.groundCommander ?: "")
        binding.firetrucksRespondedInput.setText(formViewModel.firetrucksResponded ?: "")
        binding.listOfRespondersInput.setText(formViewModel.listOfResponders ?: "")
        binding.fuelConsumedInput.setText(formViewModel.fuelConsumedLiters ?: "")
        binding.distanceOfFireSceneInput.setText(formViewModel.distanceOfFireSceneKm ?: "")
    }

    private fun saveStep4ToViewModel() {
        formViewModel.timeDeparted = binding.timeDepartedInput.text.toString().trim()
        formViewModel.timeArrival = binding.timeArrivalInput.text.toString().trim()
        formViewModel.fireUnderControlTime =
            binding.fireUnderControlTimeInput.text.toString().trim()
        formViewModel.fireOutTime = binding.fireOutTimeInput.text.toString().trim()
        formViewModel.groundCommander =
            binding.groundCommanderInput.text.toString().trim()
        formViewModel.firetrucksResponded =
            binding.firetrucksRespondedInput.text.toString().trim()
        formViewModel.listOfResponders =
            binding.listOfRespondersInput.text.toString().trim()
        formViewModel.fuelConsumedLiters =
            binding.fuelConsumedInput.text.toString().trim()
        formViewModel.distanceOfFireSceneKm =
            binding.distanceOfFireSceneInput.text.toString().trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
