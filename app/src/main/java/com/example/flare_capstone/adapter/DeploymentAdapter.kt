package com.example.flare_capstone.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.example.flare_capstone.R
import com.example.flare_capstone.views.fragment.unit.UnitDeploymentChatDialogFragment

// One deployment card
data class DeploymentItem(
    val deploymentId: String,
    val purpose: String,
    val date: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

class DeploymentAdapter(
    private val fragmentManager: FragmentManager
) : RecyclerView.Adapter<DeploymentAdapter.DeploymentViewHolder>() {

    private val items = mutableListOf<DeploymentItem>()

    inner class DeploymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPurpose: TextView = itemView.findViewById(R.id.tvPurpose)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val messageIcon: ImageView = itemView.findViewById(R.id.messageIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeploymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.deployment_item, parent, false)
        return DeploymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeploymentViewHolder, position: Int) {
        val item = items[position]

        holder.tvPurpose.text = item.purpose
        holder.tvDate.text = "Date: ${item.date}"

        // Whole card opens chat
        val openChat: (View) -> Unit = {
            val dialog = UnitDeploymentChatDialogFragment.newInstance(
                purpose = item.purpose,
                date = item.date
                // you can pass deploymentId here too if your dialog needs it
            )
            dialog.show(fragmentManager, "deployment_chat_dialog")
        }

        holder.itemView.setOnClickListener(openChat)
        holder.messageIcon.setOnClickListener(openChat)
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<DeploymentItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
