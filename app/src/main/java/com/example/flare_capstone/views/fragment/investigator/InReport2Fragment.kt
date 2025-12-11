package com.example.flare_capstone.views.fragment.investigator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.FragmentInReport2Binding
import com.google.firebase.database.*

class InReport2Fragment : Fragment() {

    private var _binding: FragmentInReport2Binding? = null
    private val binding get() = _binding!!

    private val formViewModel: InvestigatorFormViewModel by activityViewModels()
    private lateinit var dbRef: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInReport2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRef = FirebaseDatabase.getInstance().reference

        // Get incidentId + type (from args or ViewModel)
        val incidentIdArg = arguments?.getString("incidentId")
        val reportTypeArg = arguments?.getString("reportType")

        if (incidentIdArg != null) formViewModel.incidentId = incidentIdArg
        if (reportTypeArg != null) formViewModel.reportType = reportTypeArg

        val incidentId = formViewModel.incidentId
        val reportType = formViewModel.reportType ?: "FireReport"

        if (incidentId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Missing incidentId", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        // BACK
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Load base report info from AllReport
        loadBaseIncidentInfo(reportType, incidentId)

        // If ViewModel already has values (e.g. coming back), prefill
        prefillFromViewModel()

        // NEXT
        binding.btnNextReport2.setOnClickListener {
            saveStep1ToViewModel()
            findNavController().navigate(R.id.action_to_report3)
        }
    }

    private fun loadBaseIncidentInfo(reportType: String, incidentId: String) {
        dbRef.child("AllReport")
            .child(reportType)
            .child(incidentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return

                    val date = snapshot.child("date").getValue(String::class.java)
                    // choose correct key from your DB, e.g. "name", "reporterName", "callerName"
                    val callerName =
                        snapshot.child("name").getValue(String::class.java)
                            ?: snapshot.child("reporterName").getValue(String::class.java)

                    formViewModel.baseDate = date
                    formViewModel.baseCallerName = callerName

                    // Show in UI (read-only)
                    binding.incidentDateInput.setText(date ?: "Unknown date")
                    binding.fireCallerInput.setText(callerName ?: "Unknown caller")

                    // Also set as step1 defaults if empty
                    if (formViewModel.incidentDate == null) {
                        formViewModel.incidentDate = date
                    }
                    if (formViewModel.fireCaller == null) {
                        formViewModel.fireCaller = callerName
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // optional: log / toast
                }
            })
    }

    private fun prefillFromViewModel() {
        binding.callerInput.setText(formViewModel.caller ?: "")
        binding.alarmStatusInput.setText(formViewModel.alarmStatus ?: "")
        binding.causeOfFireInput.setText(formViewModel.causeOfFire ?: "")
        binding.investigationDetailsInput.setText(formViewModel.investigationDetails ?: "")
    }

    private fun saveStep1ToViewModel() {
        formViewModel.incidentDate = formViewModel.baseDate // or read from UI if needed
        formViewModel.fireCaller = formViewModel.baseCallerName

        formViewModel.caller = binding.callerInput.text.toString().trim()
        formViewModel.alarmStatus = binding.alarmStatusInput.text.toString().trim()
        formViewModel.causeOfFire = binding.causeOfFireInput.text.toString().trim()
        formViewModel.investigationDetails =
            binding.investigationDetailsInput.text.toString().trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
