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
import com.example.flare_capstone.databinding.FragmentInHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class InHomeFragment : Fragment() {

    private var _binding: FragmentInHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: InvestigatorReportAdapter

    // shared with InReportFragment
    private val formViewModel: InvestigatorFormViewModel by activityViewModels()

    // same categories as InReportFragment
    private val categories = listOf(
        "FireReport",
        "OtherEmergencyReport",
        "EmergencyMedicalServicesReport",
        "SmsReport"
    )

    private val mergedReports = mutableListOf<InvestigatorReport>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRef = FirebaseDatabase.getInstance().reference

        setupRecyclerView()
        setupCardClicks()
        loadReportsForCurrentInvestigator()
    }

    // ---------------- UI setup ----------------

    private fun setupRecyclerView() {
        adapter = InvestigatorReportAdapter(emptyList()) { report ->
            openReportDetails(report)
        }

        binding.recentReportRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InHomeFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupCardClicks() {
        // both cards can open the full reports list screen
        binding.cardPending.setOnClickListener {
            findNavController().navigate(R.id.inReportFragment)
        }

        binding.cardCompleted.setOnClickListener {
            findNavController().navigate(R.id.inReportFragment)
        }

        binding.notificationBell.setOnClickListener {
            // hook to investigator notifications later, if you add that
        }
    }

    private fun openReportDetails(report: InvestigatorReport) {
        formViewModel.incidentId = report.reportId
        formViewModel.reportType = report.reportType ?: "FireReport"

        val bundle = bundleOf(
            "incidentId" to report.reportId,
            "reportType" to (report.reportType ?: "FireReport")
        )

        findNavController().navigate(R.id.action_to_report2, bundle)
    }

    // ---------------- Data loading (same as InReportFragment) ----------------

    private fun loadReportsForCurrentInvestigator() {
        val prefs = requireContext()
            .getSharedPreferences("flare_session", Context.MODE_PRIVATE)

        val sessionId = prefs.getString("investigatorId", null)
        val authUid = FirebaseAuth.getInstance().currentUser?.uid

        Log.d("InHomeFragment", "session investigatorId = $sessionId")
        Log.d("InHomeFragment", "auth uid              = $authUid")

        if (sessionId.isNullOrBlank() && authUid.isNullOrBlank()) {
            Log.w("InHomeFragment", "No investigatorId in session or auth UID")
            _binding?.emptyStateText?.visibility = View.VISIBLE
            return
        }

        mergedReports.clear()
        adapter.updateList(emptyList())
        _binding?.emptyStateText?.visibility = View.GONE

        categories.forEach { typeCategory ->
            loadAssignmentsForCategory(typeCategory, sessionId, authUid)
        }
    }

    private fun loadAssignmentsForCategory(
        typeCategory: String,
        sessionId: String?,
        authUid: String?
    ) {
        dbRef.child("investigatorReports")
            .child(typeCategory)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null || !isAdded) {
                        Log.d("InHomeFragment", "onDataChange: view destroyed, skipping")
                        return
                    }

                    if (!snapshot.exists()) {
                        refreshDashboardUI()
                        return
                    }

                    for (assignmentSnap in snapshot.children) {
                        val base = assignmentSnap.getValue(InvestigatorReport::class.java)
                        if (base == null) {
                            Log.w("InHomeFragment", "Null InvestigatorReport in $typeCategory")
                            continue
                        }

                        val invId = base.investigatorId
                        val matchesSession =
                            !sessionId.isNullOrBlank() && invId == sessionId
                        val matchesAuth =
                            !authUid.isNullOrBlank() && invId == authUid

                        if (!matchesSession && !matchesAuth) continue
                        if (base.reportId.isNullOrBlank()) continue

                        base.reportType = typeCategory
                        fetchFullReportDetails(typeCategory, base)
                    }

                    refreshDashboardUI()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("InHomeFragment", "Error loading category $typeCategory", error.toException())
                }
            })
    }

    private fun fetchFullReportDetails(typeCategory: String, baseReport: InvestigatorReport) {
        val reportId = baseReport.reportId ?: return

        dbRef.child("AllReport")
            .child(typeCategory)
            .child(reportId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null || !isAdded) {
                        Log.d("InHomeFragment", "fetchFullReportDetails: view destroyed, skipping")
                        return
                    }

                    if (snapshot.exists()) {
                        val location = snapshot.child("location").getValue(String::class.java)
                        val date = snapshot.child("date").getValue(String::class.java)
                        val time = snapshot.child("time").getValue(String::class.java)
                        val reporterName = snapshot.child("name").getValue(String::class.java)

                        baseReport.location = location
                        baseReport.date = date
                        baseReport.time = time
                        baseReport.reporterName = reporterName
                    }

                    mergedReports.add(baseReport)
                    refreshDashboardUI()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(
                        "InHomeFragment",
                        "Error fetching AllReport/$typeCategory/$reportId",
                        error.toException()
                    )
                }
            })
    }

    // ---------------- Dashboard logic (pending / completed / weekly / recent) ----------------

    private fun refreshDashboardUI() {
        val binding = _binding ?: run {
            Log.d("InHomeFragment", "refreshDashboardUI: view destroyed, skipping")
            return
        }

        val distinct = mergedReports
            .distinctBy { it.reportType + "|" + it.reportId }
            .sortedByDescending { it.acceptedAt ?: 0L }

        // 🔹 Pending vs Completed
        // If your InvestigatorReport has a "status" field, we use it.
        // Otherwise we fallback to acceptedAt null / not null.
        val pending = distinct.count { report ->
            val status = report.status?.lowercase()
            status == "pending" || report.acceptedAt == null
        }

        val completed = distinct.count { report ->
            val status = report.status?.lowercase()
            status == "completed" || report.acceptedAt != null
        }

        binding.pendingCount.text = pending.toString()
        binding.completedCount.text = completed.toString()

        // 🔹 Weekly counts (Sun–Sat)
        val weekMap = mutableMapOf(
            "Sunday" to 0,
            "Monday" to 0,
            "Tuesday" to 0,
            "Wednesday" to 0,
            "Thursday" to 0,
            "Friday" to 0,
            "Saturday" to 0
        )

        distinct.forEach { report ->
            val dayName = getDayOfWeek(report.date)
            if (dayName != null && weekMap.containsKey(dayName)) {
                weekMap[dayName] = weekMap[dayName]!! + 1
            }
        }

        binding.sundayReportCount.text = weekMap["Sunday"].toString()
        binding.mondayReportCount.text = weekMap["Monday"].toString()
        binding.tuesdayReportCount.text = weekMap["Tuesday"].toString()
        binding.wednesdayReportCount.text = weekMap["Wednesday"].toString()
        binding.thursdayReportCount.text = weekMap["Thursday"].toString()
        binding.fridayReportCount.text = weekMap["Friday"].toString()
        binding.saturdayReportCount.text = weekMap["Saturday"].toString()

        // 🔹 Recent reports (like recent visitors in old project)
        val recent = distinct.take(10)  // or 5 if you prefer
        adapter.updateList(recent)

        binding.emptyStateText.visibility =
            if (recent.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Convert your AllReport "date" string to a day-of-week name.
     *
     * ⚠️ IMPORTANT:
     *  - Change DATE_PATTERNS to match your actual date format in RTDB.
     *    Example guesses:
     *      "MM/dd/yyyy"
     *      "yyyy-MM-dd"
     *      "dd/MM/yyyy"
     */
    private fun getDayOfWeek(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null

        // TODO: adjust these patterns to your real format
        val patterns = listOf(
            "MM/dd/yyyy",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val date = sdf.parse(dateStr) ?: continue
                val cal = Calendar.getInstance().apply { time = date }
                return when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> "Sunday"
                    Calendar.MONDAY -> "Monday"
                    Calendar.TUESDAY -> "Tuesday"
                    Calendar.WEDNESDAY -> "Wednesday"
                    Calendar.THURSDAY -> "Thursday"
                    Calendar.FRIDAY -> "Friday"
                    Calendar.SATURDAY -> "Saturday"
                    else -> null
                }
            } catch (e: Exception) {
                // try next pattern
            }
        }

        Log.w("InHomeFragment", "getDayOfWeek: could not parse date '$dateStr'")
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
