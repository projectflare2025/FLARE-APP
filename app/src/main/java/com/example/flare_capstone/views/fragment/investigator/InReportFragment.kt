package com.example.flare_capstone.views.fragment.investigator

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flare_capstone.R
import com.example.flare_capstone.adapter.InvestigatorReportAdapter
import com.example.flare_capstone.data.model.InvestigatorReport
import com.example.flare_capstone.databinding.FragmentInReportBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class InReportFragment : Fragment() {

    private var _binding: FragmentInReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: InvestigatorReportAdapter
    private lateinit var dbRef: DatabaseReference

    private val allReports = mutableListOf<InvestigatorReport>()

    private enum class ReportFilter { PENDING, COMPLETE }
    private var currentFilter = ReportFilter.PENDING

    private var selectedDate: Calendar = Calendar.getInstance()
    private var searchQuery: String = ""

    private val dateFormatDb = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateFormatDisplay = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFirebase()
        setupSearch()
        setupDateNavigation()
        setupFilterButtons()
        updateSelectedDateText()
        loadInvestigatorReports()
    }

    // ---------------- RecyclerView ----------------

    private fun setupRecyclerView() {
        adapter = InvestigatorReportAdapter(emptyList()) { report ->
            // TODO: navigate to detail screen if you want
            // val action = InReportFragmentDirections
            //     .actionInReportFragmentToInvestigatorReportDetailsFragment(report.reportId!!)
            // findNavController().navigate(action)
        }

        binding.reportRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InReportFragment.adapter
            setHasFixedSize(true)
        }
    }

    // ---------------- Firebase ----------------

    private fun setupFirebase() {
        dbRef = FirebaseDatabase.getInstance()
            .getReference("investigatorReports")
            .child("FireReport")
    }

    private fun loadInvestigatorReports() {
        toggleLoading(true)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            toggleLoading(false)
            return
        }

        val query = dbRef.orderByChild("investigatorId").equalTo(currentUserId)

        query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allReports.clear()
                for (child in snapshot.children) {
                    val report = child.getValue(InvestigatorReport::class.java)
                    if (report != null) {
                        allReports.add(report)
                    }
                }
                applyAllFilters()
                toggleLoading(false)
            }

            override fun onCancelled(error: DatabaseError) {
                toggleLoading(false)
                // log or show a toast if needed
            }
        })
    }

    // ---------------- Search ----------------

    private fun setupSearch() {
        binding.searchReportInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyAllFilters()
            }
        })
    }

    // ---------------- Date navigation ----------------

    private fun setupDateNavigation() {
        binding.prevDateButton.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1)
            updateSelectedDateText()
            applyAllFilters()
        }

        binding.nextDateButton.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1)
            updateSelectedDateText()
            applyAllFilters()
        }

        // Tap the whole date layout to open a DatePicker
        binding.dateFilterLayout.setOnClickListener {
            val c = selectedDate
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDate.set(y, m, d)
                updateSelectedDateText()
                applyAllFilters()
            }, year, month, day).apply {
                // optional: max date = today
                datePicker.maxDate = Calendar.getInstance().timeInMillis
            }.show()
        }
    }

    private fun updateSelectedDateText() {
        binding.selectedDateText.text = dateFormatDisplay.format(selectedDate.time)
    }

    // ---------------- Filter buttons ----------------

    private fun setupFilterButtons() {
        updateFilterUI()

        binding.pendingFilterButton.setOnClickListener {
            currentFilter = ReportFilter.PENDING
            updateFilterUI()
            applyAllFilters()
        }

        binding.completeFilterButton.setOnClickListener {
            currentFilter = ReportFilter.COMPLETE
            updateFilterUI()
            applyAllFilters()
        }
    }

    private fun updateFilterUI() {
        val selectedBg = ContextCompat.getDrawable(requireContext(), R.drawable.filter_button_selected)
        val unselectedBg = ContextCompat.getDrawable(requireContext(), R.drawable.filter_button_unselected)
        val white = ContextCompat.getColor(requireContext(), android.R.color.white)
        val primary = ContextCompat.getColor(requireContext(), R.color.primaryColor)

        val isPending = currentFilter == ReportFilter.PENDING
        val isComplete = currentFilter == ReportFilter.COMPLETE

        binding.pendingFilterButton.apply {
            background = if (isPending) selectedBg else unselectedBg
            setTextColor(if (isPending) white else primary)
        }

        binding.completeFilterButton.apply {
            background = if (isComplete) selectedBg else unselectedBg
            setTextColor(if (isComplete) white else primary)
        }
    }

    // ---------------- Combined filter logic ----------------

    private fun applyAllFilters() {
        val filtered = allReports.filter { report ->
            matchesStatus(report) && matchesDate(report) && matchesSearch(report)
        }

        adapter.updateList(filtered)
        toggleEmptyState(filtered.isEmpty())
    }

    private fun matchesStatus(report: InvestigatorReport): Boolean {
        val status = report.status?.trim()?.lowercase(Locale.getDefault()) ?: ""

        return when (currentFilter) {
            ReportFilter.PENDING -> {
                // DB: ongoing -> Pending
                status == "ongoing" || status == "pending"
            }
            ReportFilter.COMPLETE -> {
                // DB: complete/completed -> Complete
                status == "completed" || status == "complete" || status == "resolved"
            }
        }
    }

    private fun matchesDate(report: InvestigatorReport): Boolean {
        val ts = report.acceptedAt ?: return false
        val cal = Calendar.getInstance().apply { timeInMillis = ts }

        return cal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                cal.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
                cal.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH)
    }

    private fun matchesSearch(report: InvestigatorReport): Boolean {
        if (searchQuery.isEmpty()) return true

        val q = searchQuery.lowercase(Locale.getDefault())
        val type = report.reportType?.lowercase(Locale.getDefault()) ?: ""
        val station = report.stationId?.lowercase(Locale.getDefault()) ?: ""

        return type.contains(q) || station.contains(q)
    }

    // ---------------- UI helpers ----------------

    private fun toggleEmptyState(isEmpty: Boolean) {
        binding.emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.reportRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun toggleLoading(isLoading: Boolean) {
        binding.loadingLayout.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.reportRecyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
