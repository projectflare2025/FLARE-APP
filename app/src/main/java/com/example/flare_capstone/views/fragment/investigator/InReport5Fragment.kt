package com.example.flare_capstone.views.fragment.investigator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.FragmentInReport5Binding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class InReport5Fragment : Fragment() {

    private var _binding: FragmentInReport5Binding? = null
    private val binding get() = _binding!!

    private val formViewModel: InvestigatorFormViewModel by activityViewModels()

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

        // Prefill if user comes back from previous screens
        prefillFromViewModel()

        // COMPLETE REPORT BUTTON
        binding.btnCompleteReport.setOnClickListener {
            // 1) Save step 5 inputs to ViewModel
            saveStep5ToViewModel()

            // 2) (Optional) basic validation
            if (formViewModel.establishmentName.isNullOrBlank()) {
                binding.establishmentNameInput.error = "Required"
                return@setOnClickListener
            }

            // 3) Show confirmation dialog
            AlertDialog.Builder(requireContext())
                .setTitle("Submit Investigation Report")
                .setMessage(
                    "Are you sure you want to submit this investigation report? " +
                            "You won't be able to edit it after submitting."
                )
                .setPositiveButton("Submit") { _, _ ->
                    // 4) Actually write to Firebase
                    submitInvestigatorFeedback()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun prefillFromViewModel() {
        binding.establishmentNameInput.setText(formViewModel.establishmentName ?: "")
        binding.ownerInput.setText(formViewModel.ownerName ?: "")
        binding.occupantInput.setText(formViewModel.occupantName ?: "")
        binding.landAreaInvolvedInput.setText(formViewModel.landAreaInvolved ?: "")
    }

    private fun saveStep5ToViewModel() {
        formViewModel.establishmentName =
            binding.establishmentNameInput.text.toString().trim()
        formViewModel.ownerName =
            binding.ownerInput.text.toString().trim()
        formViewModel.occupantName =
            binding.occupantInput.text.toString().trim()
        formViewModel.landAreaInvolved =
            binding.landAreaInvolvedInput.text.toString().trim()
    }

    private fun submitInvestigatorFeedback() {
        val incidentId = formViewModel.incidentId
        val reportType = formViewModel.reportType ?: "FireReport"

        if (incidentId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Missing incident ID", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        val db = FirebaseDatabase.getInstance().reference
        val feedbackRef = db.child("AllReport")
            .child(reportType)
            .child(incidentId)
            .child("investigatorFeedback")
            .push()

        val data: MutableMap<String, Any?> = mutableMapOf(
            "investigatorId" to uid,
            "incidentId" to incidentId,
            "reportType" to reportType,
            "submittedAt" to ServerValue.TIMESTAMP,

            // Step 1: Incident info
            "incidentInfo" to mapOf(
                "incidentDate" to formViewModel.incidentDate,
                "fireCaller" to formViewModel.fireCaller,
                "caller" to formViewModel.caller,
                "alarmStatus" to formViewModel.alarmStatus,
                "causeOfFire" to formViewModel.causeOfFire,
                "investigationDetails" to formViewModel.investigationDetails
            ),

            // Step 2: Evidence (multiple base64 images + optional file name)
            "evidence" to mapOf(
                "photosBase64" to formViewModel.evidenceImagesBase64,
                "fileName" to formViewModel.evidenceFileName
            ),

            // Step 4: Response details
            "responseDetails" to mapOf(
                "timeDeparted" to formViewModel.timeDeparted,
                "timeArrival" to formViewModel.timeArrival,
                "fireUnderControlTime" to formViewModel.fireUnderControlTime,
                "fireOutTime" to formViewModel.fireOutTime,
                "groundCommander" to formViewModel.groundCommander,
                "firetrucksResponded" to formViewModel.firetrucksResponded,
                "listOfResponders" to formViewModel.listOfResponders,
                "fuelConsumedLiters" to formViewModel.fuelConsumedLiters,
                "distanceOfFireSceneKm" to formViewModel.distanceOfFireSceneKm
            ),

            // Step 5: Property info
            "propertyInfo" to mapOf(
                "establishmentName" to formViewModel.establishmentName,
                "ownerName" to formViewModel.ownerName,
                "occupantName" to formViewModel.occupantName,
                "landAreaInvolved" to formViewModel.landAreaInvolved
            )
        )

        feedbackRef.setValue(data)
            .addOnSuccessListener {
                Toast.makeText(
                    requireContext(),
                    "Investigation report submitted",
                    Toast.LENGTH_SHORT
                ).show()

                // Go back to investigator home (or wherever you like)
                findNavController().navigate(R.id.inHomeFragment)
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Failed to submit: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
