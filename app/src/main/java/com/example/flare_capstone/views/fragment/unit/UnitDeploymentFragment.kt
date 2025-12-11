package com.example.flare_capstone.views.fragment.unit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flare_capstone.R
import com.example.flare_capstone.adapter.DeploymentAdapter
import com.example.flare_capstone.databinding.FragmentUniDeploymentBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng

class UnitDeploymentFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentUniDeploymentBinding? = null
    private val binding get() = _binding!!

    private var gMap: GoogleMap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUniDeploymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔹 Headline
        binding.tvHeadline.text = "Deployment Overview"

        // 🔹 Setup Google Map inside inboxMapContainer
        val existing = childFragmentManager.findFragmentById(R.id.inboxMapContainer) as? SupportMapFragment
        val mapFragment = existing ?: SupportMapFragment.newInstance().also {
            childFragmentManager.beginTransaction()
                .replace(R.id.inboxMapContainer, it)
                .commit()
        }
        mapFragment.getMapAsync(this)

        // 🔹 RecyclerView with static deployments + dialog on click
        binding.recentReportRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = DeploymentAdapter(childFragmentManager)   // << important
        }

        // Static data -> no empty state
        binding.emptyStateText.visibility = View.GONE
    }

    override fun onMapReady(map: GoogleMap) {
        gMap = map

        gMap?.uiSettings?.isZoomControlsEnabled = true
        gMap?.uiSettings?.isCompassEnabled = true

        // Default center (Tagum sample)
        val defaultCenter = LatLng(7.4478, 125.8096)
        gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCenter, 13f))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
