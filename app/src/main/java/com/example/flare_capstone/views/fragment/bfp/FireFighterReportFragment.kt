package com.example.flare_capstone.views.fragment.bfp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flare_capstone.R
import com.example.flare_capstone.adapter.FireFighterReportAdapter
import com.example.flare_capstone.data.model.Report
import com.example.flare_capstone.data.model.ReportKind
import com.example.flare_capstone.databinding.FragmentFireFighterReportBinding
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.LocalDateTime
import java.time.ZoneOffset

class FireFighterReportFragment : Fragment(R.layout.fragment_fire_fighter_report) {

    private lateinit var binding: FragmentFireFighterReportBinding
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val adapter = FireFighterReportAdapter { report -> openDetails(report) }

    private var allReports: MutableList<Report> = mutableListOf()
    private var currentTab: ReportKind = ReportKind.ALL
    private var currentStatus: String = "Any"

    private val attachedRefs = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    private val emailToStation = mapOf(
        "tcwestfiresubstation@gmail.com" to "MabiniFireFighterAccount",
        "lafilipinafire@gmail.com"       to "LaFilipinaFireFighterAccount",
        "bfp_tagumcity@yahoo.com"        to "CanocotanFireFighterAccount"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentFireFighterReportBinding.inflate(inflater, container, false)

        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        // Tabs (kind: All / Fire / Other / EMS / SMS)
        val tl = binding.tabType
        listOf("All", "Fire", "Other", "EMS", "SMS").forEach {
            tl.addTab(tl.newTab().setText(it))
        }
        tl.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = when (tab.position) {
                    1 -> ReportKind.FIRE
                    2 -> ReportKind.OTHER
                    3 -> ReportKind.EMS
                    4 -> ReportKind.SMS
                    else -> ReportKind.ALL
                }
                applyFilters()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Status filter buttons (Any, Pending, Ongoing, Completed)
        setupStatusButtons()

        // Search
        binding.inputSearch.addTextChangedListener {
            applyFilters()
        }

        // Pull-to-refresh
        binding.swipe.setOnRefreshListener {
            attachRealtimeListeners(resolveStationKey(), forceReattach = true)
        }

        // Start
        attachRealtimeListeners(resolveStationKey())

        return binding.root
    }

    private fun setupStatusButtons() {
        val buttons = listOf(
            binding.statusAnyButton to "Any",
            binding.statusPendingButton to "Pending",
            binding.statusOngoingButton to "Ongoing",
            binding.statusCompletedButton to "Completed"
        )

        fun updateSelection(selected: String) {
            currentStatus = selected
            buttons.forEach { (btn, value) ->
                val isSelected = value == selected
                btn.isSelected = isSelected
                // Optional visual feedback if your FilterButton style supports it
                btn.alpha = if (isSelected) 1f else 0.6f
            }
            applyFilters()
        }

        buttons.forEach { (btn, value) ->
            btn.setOnClickListener {
                updateSelection(value)
            }
        }

        // Default selection: Any
        updateSelection("Any")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detachAll()
    }

    private fun resolveStationKey(): String {
        val email = FirebaseAuth.getInstance().currentUser?.email?.lowercase().orEmpty()
        return emailToStation[email] ?: "CanocotanFireFighterAccount"
    }

    private fun attachRealtimeListeners(stationKey: String, forceReattach: Boolean = false) {
        if (forceReattach) detachAll()

        allReports.clear()
        adapter.submitList(emptyList())
        binding.swipe.isRefreshing = true

        val base = "TagumCityCentralFireStation/FireFighter/AllFireFighterAccount/$stationKey/AllReport"

        fun hook(path: String, kind: ReportKind) {
            val ref = db.child(path)
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    allReports.removeAll { it.kind == kind }
                    for (c in s.children) {
                        val id = c.key ?: continue
                        @Suppress("UNCHECKED_CAST")
                        val m = (c.value as? Map<String, Any?>) ?: emptyMap()
                        allReports += mapToReport(id, kind, m)
                    }
                    allReports.sortByDescending { it.timestamp }
                    applyFilters()
                    binding.swipe.isRefreshing = false
                }
                override fun onCancelled(e: DatabaseError) {
                    Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
                    binding.swipe.isRefreshing = false
                }
            }
            ref.addValueEventListener(l)
            attachedRefs += ref to l
        }

        hook("$base/FireReport", ReportKind.FIRE)
        hook("$base/OtherEmergencyReport", ReportKind.OTHER)
        hook("$base/EmergencyMedicalServicesReport", ReportKind.EMS)
        hook("$base/SmsReport", ReportKind.SMS)
    }

    private fun detachAll() {
        attachedRefs.forEach { (ref, l) ->
            try {
                ref.removeEventListener(l)
            } catch (_: Exception) { }
        }
        attachedRefs.clear()
    }

    private fun mapToReport(id: String, kind: ReportKind, raw: Map<String, Any?>): Report {
        fun s(key: String) = (raw[key] as? String).orEmpty()
        fun n(key: String) = (raw[key] as? Number)?.toLong()
        val location = s("exactLocation").ifBlank { s("location") }
        val status = s("status").ifBlank { "Unknown" }
        val date = s("date")
        val time = s("reportTime").ifBlank { s("time") }
        val ts = n("timestamp") ?: n("createdAt") ?: n("updatedAt") ?: parseFallback(date, time)
        return Report(
            id = id,
            kind = kind,
            location = location.ifBlank { "N/A" },
            status = status,
            date = date,
            time = time,
            timestamp = ts,
            raw = raw
        )
    }

    private fun parseFallback(date: String?, time: String?): Long {
        if (date.isNullOrBlank()) return 0L
        val t24 = to24h(time ?: "00:00")
        val parts = date.split("/")
        if (parts.size == 3) {
            val p1 = parts[0].padStart(2, '0')
            val p2 = parts[1].padStart(2, '0')
            val y = if (parts[2].length == 2) "20${parts[2]}" else parts[2]
            return runCatching {
                LocalDateTime.parse("$y-$p2$t24:00")
            }.map { it.atZone(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrElse {
                runCatching {
                    LocalDateTime.parse("$y-$p1$t24:00")
                }.map { it.atZone(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)
            }
        }
        return 0L
    }

    private fun to24h(t: String): String {
        val re = Regex("""^(\d{1,2}):(\d{2})(?::\d{2})?\s*(AM|PM)?$""", RegexOption.IGNORE_CASE)
        val m = re.matchEntire(t.trim()) ?: return t
        var h = m.groupValues[1].toInt()
        val mm = m.groupValues[2]
        val ap = m.groupValues[3].uppercase()
        if (ap == "PM" && h != 12) h += 12
        if (ap == "AM" && h == 12) h = 0
        return "${h.toString().padStart(2, '0')}:$mm"
    }

    private fun applyFilters() {
        val q = binding.inputSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val statusSel = currentStatus

        val filtered = allReports.asSequence()
            .filter { r ->
                currentTab == ReportKind.ALL || r.kind == currentTab
            }
            .filter { r ->
                // Same logic as dropdown: "Any" = no filter
                statusSel == "Any" || r.status.equals(statusSel, ignoreCase = true)
            }
            .filter { r ->
                q.isBlank() || r.location.lowercase().contains(q)
            }
            .toList()

        adapter.submitList(filtered)
    }

    private fun openDetails(r: Report) {
        Toast.makeText(
            requireContext(),
            "${r.kind}: ${r.location}\n${r.status} • ${r.date} ${r.time}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
