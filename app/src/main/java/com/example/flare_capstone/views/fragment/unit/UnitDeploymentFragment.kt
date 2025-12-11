package com.example.flare_capstone.views.fragment.unit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.flare_capstone.R
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

        // 🔹 Attach a SupportMapFragment into the mapContainer (same pattern as UnitHomeFragment)
        val existing = childFragmentManager.findFragmentById(binding.mapContainer.id) as? SupportMapFragment
        val mapFragment = existing ?: SupportMapFragment.newInstance().also {
            childFragmentManager.beginTransaction()
                .replace(binding.mapContainer.id, it)
                .commit()
            childFragmentManager.executePendingTransactions()
        }
        mapFragment.getMapAsync(this)

        // 🔹 Header text (if you have a TextView in the layout)
        // Example: binding.headerTitle.text = "Deployment"

        // 🔹 Bottom action button
        // If your layout uses "completed" as the id:
        val button = binding.root.findViewById<View>(R.id.completed)
            ?: binding.root.findViewById(R.id.btnAction) // fallback if you renamed it

        button?.setOnClickListener {
            Toast.makeText(requireContext(), "No active deployment selected.", Toast.LENGTH_SHORT).show()
        }

        // 🔹 Initial info text
        binding.selectedInfo.text = "No active deployments"
    }

    override fun onMapReady(map: GoogleMap) {
        gMap = map

        // Basic map UI
        gMap?.uiSettings?.isZoomControlsEnabled = true
        gMap?.uiSettings?.isCompassEnabled = true

        // Default center (example: Tagum or your target city)
        val defaultCenter = LatLng(7.4478, 125.8096) // change to your desired default
        gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCenter, 13f))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
