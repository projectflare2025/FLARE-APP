package com.example.flare_capstone.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.flare_capstone.R
import com.example.flare_capstone.data.model.InvestigatorReport
import com.example.flare_capstone.databinding.ItemInvReportBinding
import java.text.SimpleDateFormat
import java.util.*

class InvestigatorReportAdapter(
    private var reportList: List<InvestigatorReport>,
    private val onItemClick: (InvestigatorReport) -> Unit
) : RecyclerView.Adapter<InvestigatorReportAdapter.ReportViewHolder>() {

    inner class ReportViewHolder(
        private val binding: ItemInvReportBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(report: InvestigatorReport) {

            // ---- STATUS: DB → UI ----
            val statusRaw = report.status?.trim()?.lowercase(Locale.ROOT) ?: "ongoing"
            val (statusLabel, chipColorRes) = when (statusRaw) {
                "ongoing", "pending" -> "Ongoing" to R.color.warningYellow
                "completed", "complete", "resolved" -> "Completed" to R.color.successGreen
                "cancelled", "canceled" -> "Cancelled" to R.color.errorRed
                else -> statusRaw.replaceFirstChar { it.titlecase(Locale.ROOT) } to R.color.gray
            }

            binding.reportStatusText.text = statusLabel
            binding.reportStatusText.setTextColor(
                ContextCompat.getColor(binding.root.context, android.R.color.white)
            )
            binding.reportStatusText.background =
                ContextCompat.getDrawable(binding.root.context, R.drawable.status_chip_background)

            // ---- Station / header line ----
            binding.reporterNameText.text =
                "Reporter: ${report.reporterName ?: "Unknown reporter"}"

            // ---- Type / category line ----
            binding.reportTypeText.text = when (report.reportType) {
                "FireReport" -> "Fire Report"
                "OtherEmergencyReport" -> "Other Emergency"
                "EmergencyMedicalServicesReport" -> "EMS Report"
                "SmsReport" -> "SMS Report"
                else -> report.reportType ?: "Report"
            }

            // ---- Date & time ----
            val acceptedAt = report.acceptedAt
            if (acceptedAt != null) {
                val sdf = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
                binding.reportDateTimeText.text = sdf.format(Date(acceptedAt))
            } else if (!report.date.isNullOrBlank() || !report.time.isNullOrBlank()) {
                binding.reportDateTimeText.text =
                    listOfNotNull(report.date, report.time).joinToString(" • ")
            } else {
                binding.reportDateTimeText.text = "Unknown time"
            }

            // ---- Location line ----
            val loc = report.location ?: report.stationId ?: "Unknown location"
            binding.reportLocationText.text = loc

            // ---- Click ----
            binding.root.setOnClickListener {
                onItemClick(report)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val binding = ItemInvReportBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReportViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(reportList[position])
    }

    override fun getItemCount(): Int = reportList.size

    fun updateList(newList: List<InvestigatorReport>) {
        reportList = newList
        notifyDataSetChanged()
    }
}
