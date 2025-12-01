package com.example.flare_capstone.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.flare_capstone.data.model.Report
import com.example.flare_capstone.data.model.ReportKind

class FireFighterReportAdapter(
    private val onClick: (Report) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Report, ReportVH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Report>() {
            override fun areItemsTheSame(a: Report, b: Report) = a.id == b.id && a.kind == b.kind
            override fun areContentsTheSame(a: Report, b: Report) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportVH {
        val v = LayoutInflater.from(parent.context).inflate(_root_ide_package_.com.example.flare_capstone.R.layout.item_report, parent, false)
        return ReportVH(v)
    }

    override fun onBindViewHolder(holder: ReportVH, position: Int) {
        holder.bind(getItem(position), onClick)
    }
}

class ReportVH(v: View) : RecyclerView.ViewHolder(v) {
    private val title = v.findViewById<TextView>(_root_ide_package_.com.example.flare_capstone.R.id.txtTitle)
    private val sub   = v.findViewById<TextView>(_root_ide_package_.com.example.flare_capstone.R.id.txtSub)
    private val chipT = v.findViewById<TextView>(_root_ide_package_.com.example.flare_capstone.R.id.chipType)
    private val chipS = v.findViewById<TextView>(_root_ide_package_.com.example.flare_capstone.R.id.chipStatus)

    fun bind(r: Report, onClick: (Report) -> Unit) {
        title.text = r.location.ifBlank { "No location" }
        sub.text   = listOfNotNull(r.date.takeIf { !it.isNullOrBlank() }, r.time.takeIf { !it.isNullOrBlank() })
            .joinToString(" ")
        chipT.text = when (r.kind) {
            ReportKind.FIRE  -> "Fire"
            ReportKind.OTHER -> "Other"
            ReportKind.EMS   -> "EMS"
            ReportKind.SMS   -> "SMS"
            ReportKind.ALL   -> "All"
        }
        chipS.text = r.status
        itemView.setOnClickListener { onClick(r) }
    }
}
