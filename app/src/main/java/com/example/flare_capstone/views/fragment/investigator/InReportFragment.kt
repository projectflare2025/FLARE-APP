package com.example.flare_capstone.views.fragment.investigator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flare_capstone.adapter.InvestigatorReportAdapter
import com.example.flare_capstone.data.model.InvestigatorReport
import com.example.flare_capstone.databinding.FragmentInReportBinding

class InReportFragment : Fragment() {

    private var _binding: FragmentInReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: InvestigatorReportAdapter

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
        showDummyItems()
    }

    private fun setupRecyclerView() {
        adapter = InvestigatorReportAdapter(emptyList()) {}

        binding.reportRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InReportFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun showDummyItems() {
        val dummyItems = listOf(
            InvestigatorReport(
                acceptedAt = System.currentTimeMillis(),
                investigatorId = "dummy1",
                reportId = "RPT001",
                reportType = "FireReport",
                stationId = "Station A",
                status = "Ongoing" // will display as Pending
            ),
            InvestigatorReport(
                acceptedAt = System.currentTimeMillis(),
                investigatorId = "dummy2",
                reportId = "RPT002",
                reportType = "GasLeak",
                stationId = "Station B",
                status = "Completed"
            )
        )

        adapter.updateList(dummyItems)

        // Hide empty state
        binding.emptyStateText.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
