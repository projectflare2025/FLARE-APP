package com.example.flare_capstone.views.fragment.investigator

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flare_capstone.R
import com.example.flare_capstone.adapter.InvestigatorReportAdapter
import com.example.flare_capstone.data.model.InvestigatorReport
import com.example.flare_capstone.databinding.FragmentInReportBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class InReportFragment : Fragment() {

    private var _binding: FragmentInReportBinding? = null
    // Use this only when you're sure the view exists (onCreateView → onDestroyView)
    private val binding get() = _binding!!

    private lateinit var adapter: InvestigatorReportAdapter
    private lateinit var dbRef: DatabaseReference

    // Shared ViewModel for all steps
    private val formViewModel: InvestigatorFormViewModel by activityViewModels()

    // Same categories as Angular
    private val categories = listOf(
        "FireReport",
        "OtherEmergencyReport",
        "EmergencyMedicalServicesReport",
        "SmsReport"
    )

    private val mergedReports = mutableListOf<InvestigatorReport>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRef = FirebaseDatabase.getInstance().reference

        setupRecyclerView()
        loadReportsForCurrentInvestigator()
    }

    private fun setupRecyclerView() {
        adapter = InvestigatorReportAdapter(emptyList()) { report ->
            openReportDetails(report)
        }

        binding.reportRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InReportFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun openReportDetails(report: InvestigatorReport) {
        // Save basics to shared ViewModel
        formViewModel.incidentId = report.reportId
        formViewModel.reportType = report.reportType ?: "FireReport"

        // Pass incidentId + reportType via Bundle too (for safety / direct access)
        val bundle = bundleOf(
            "incidentId" to report.reportId,
            "reportType" to (report.reportType ?: "FireReport")
        )

        findNavController().navigate(R.id.action_to_report2, bundle)
    }

    /**
     * Get investigator ID from session AND from FirebaseAuth, then load reports.
     */
    private fun loadReportsForCurrentInvestigator() {
        val prefs = requireContext()
            .getSharedPreferences("flare_session", Context.MODE_PRIVATE)

        val sessionId = prefs.getString("investigatorId", null)
        val authUid = FirebaseAuth.getInstance().currentUser?.uid

        Log.d("InReportFragment", "session investigatorId = $sessionId")
        Log.d("InReportFragment", "auth uid              = $authUid")

        if (sessionId.isNullOrBlank() && authUid.isNullOrBlank()) {
            Log.w("InReportFragment", "No investigatorId in session or auth UID")
            // Only touch binding if view is still alive
            _binding?.emptyStateText?.visibility = View.VISIBLE
            return
        }

        mergedReports.clear()
        adapter.updateList(emptyList())
        _binding?.emptyStateText?.visibility = View.GONE

        // Load from each category
        categories.forEach { typeCategory ->
            loadAssignmentsForCategory(typeCategory, sessionId, authUid)
        }
    }

    /**
     * Reads all investigatorReports/{typeCategory}, then filters by
     * investigatorId == sessionId OR == authUid (to handle mismatched IDs).
     */
    private fun loadAssignmentsForCategory(
        typeCategory: String,
        sessionId: String?,
        authUid: String?
    ) {
        Log.d("InReportFragment", "Loading category: $typeCategory")

        dbRef.child("investigatorReports")
            .child(typeCategory)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    // If the view is already destroyed, don't update UI
                    if (_binding == null || !isAdded) {
                        Log.d("InReportFragment", "onDataChange: view destroyed, skipping UI update")
                        return
                    }

                    Log.d(
                        "InReportFragment",
                        "investigatorReports/$typeCategory children = ${snapshot.childrenCount}"
                    )

                    if (!snapshot.exists()) {
                        refreshListUI()
                        return
                    }

                    for (assignmentSnap in snapshot.children) {
                        val base = assignmentSnap.getValue(InvestigatorReport::class.java)
                        if (base == null) {
                            Log.w("InReportFragment", "Null InvestigatorReport in $typeCategory")
                            continue
                        }

                        val invId = base.investigatorId
                        Log.d(
                            "InReportFragment",
                            "Found assignment: reportId=${base.reportId}, investigatorId=$invId"
                        )

                        val matchesSession =
                            !sessionId.isNullOrBlank() && invId == sessionId
                        val matchesAuth =
                            !authUid.isNullOrBlank() && invId == authUid

                        if (!matchesSession && !matchesAuth) {
                            // Not assigned to this investigator
                            continue
                        }

                        if (base.reportId.isNullOrBlank()) {
                            Log.w("InReportFragment", "assignment has no reportId, skipping")
                            continue
                        }

                        base.reportType = typeCategory

                        // Now we join with AllReport/{typeCategory}/{reportId}
                        fetchFullReportDetails(typeCategory, base)
                    }

                    refreshListUI()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("InReportFragment", "Error loading category $typeCategory", error.toException())
                }
            })
    }

    /**
     * Get data from AllReport/{typeCategory}/{reportId} and merge into baseReport.
     */
    private fun fetchFullReportDetails(typeCategory: String, baseReport: InvestigatorReport) {
        val reportId = baseReport.reportId ?: return

        Log.d(
            "InReportFragment",
            "Fetching AllReport/$typeCategory/$reportId"
        )

        dbRef.child("AllReport")
            .child(typeCategory)
            .child(reportId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    // If the view is already destroyed, don't update UI
                    if (_binding == null || !isAdded) {
                        Log.d("InReportFragment", "fetchFullReportDetails.onDataChange: view destroyed, skipping UI update")
                        return
                    }

                    if (!snapshot.exists()) {
                        Log.w(
                            "InReportFragment",
                            "AllReport/$typeCategory/$reportId does not exist"
                        )
                    } else {
                        // Adjust keys here to match your RTDB AllReport structure
                        val location = snapshot.child("location").getValue(String::class.java)
                        val date = snapshot.child("date").getValue(String::class.java)
                        val time = snapshot.child("time").getValue(String::class.java)
                        val reporterName = snapshot.child("name").getValue(String::class.java)

                        Log.d(
                            "InReportFragment",
                            "AllReport data: location=$location, date=$date, time=$time"
                        )

                        baseReport.location = location
                        baseReport.date = date
                        baseReport.time = time
                        baseReport.reporterName = reporterName
                    }

                    mergedReports.add(baseReport)
                    refreshListUI()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(
                        "InReportFragment",
                        "Error fetching AllReport/$typeCategory/$reportId",
                        error.toException()
                    )
                }
            })
    }

    /**
     * Sort and update adapter + empty state.
     * This is now safe against a null binding.
     */
    private fun refreshListUI() {
        // If the view is already destroyed, there's no UI to refresh
        val binding = _binding ?: run {
            Log.d("InReportFragment", "refreshListUI: view destroyed, skipping")
            return
        }

        val sorted = mergedReports
            .distinctBy { it.reportType + "|" + it.reportId } // avoid duplicates
            .sortedByDescending { it.acceptedAt ?: 0L }

        Log.d("InReportFragment", "refreshListUI: size=${sorted.size}")

        adapter.updateList(sorted)
        binding.emptyStateText.visibility =
            if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
