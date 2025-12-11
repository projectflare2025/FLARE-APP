package com.example.flare_capstone.adapter

import androidx.fragment.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.flare_capstone.R
import com.example.flare_capstone.views.fragment.unit.UnitDeploymentChatDialogFragment

class DeploymentAdapter(
    private val fragmentManager: FragmentManager
) : RecyclerView.Adapter<DeploymentAdapter.DeploymentViewHolder>() {

    // 🔹 Static sample items
    private val staticPurpose = listOf(
        "Fire Suppression - Area A",
        "Rescue Operation - Highway",
        "Medical Assistance - District 3"
    )

    private val staticDates = listOf(
        "Jan 20, 2025",
        "Jan 21, 2025",
        "Jan 22, 2025"
    )

    inner class DeploymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPurpose: TextView = itemView.findViewById(R.id.tvPurpose)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeploymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.deployment_item, parent, false)
        return DeploymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeploymentViewHolder, position: Int) {
        val purpose = staticPurpose[position]
        val date = staticDates[position]

        holder.tvPurpose.text = purpose
        holder.tvDate.text = "Date: $date"

        // 💬 Open dialog on item click
        holder.itemView.setOnClickListener {
            val dialog = UnitDeploymentChatDialogFragment.newInstance(
                purpose = purpose,
                date = date
            )
            dialog.show(fragmentManager, "deployment_chat_dialog")
        }
    }

    override fun getItemCount(): Int = staticPurpose.size
}
