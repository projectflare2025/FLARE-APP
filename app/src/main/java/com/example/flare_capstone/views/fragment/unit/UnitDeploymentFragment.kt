package com.example.flare_capstone.views.fragment.unit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flare_capstone.R
import com.example.flare_capstone.adapter.DeploymentAdapter
import com.example.flare_capstone.adapter.DeploymentItem
import com.example.flare_capstone.data.database.UserSession
import com.example.flare_capstone.databinding.FragmentUniDeploymentBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.database.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.gms.maps.model.Marker



class UnitDeploymentFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentUniDeploymentBinding? = null
    private val binding get() = _binding!!

    // Map
    private var gMap: GoogleMap? = null
    private var mapReady = false

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationUpdatesStarted = false
    private var cameraFittedOnce = false
    private var userLatitude = 0.0
    private var userLongitude = 0.0

    // Recycler
    private lateinit var adapter: DeploymentAdapter
    private var currentDeployments: List<DeploymentItem> = emptyList()

    // Firebase
    private val deploymentRootRef: DatabaseReference by lazy {
        FirebaseDatabase.getInstance().getReference("DeploymentRoot")
    }
    private var deploymentListener: ValueEventListener? = null

    // OSRM route
    private var activePolyline: Polyline? = null
    private var activeDistanceMeters: Long? = null
    private var activeDurationSec: Long? = null
    private val routeRequestSeq = AtomicInteger(0)

    private val DEFAULT_CENTER = LatLng(7.4478, 125.8096)
    private val DEFAULT_ZOOM = 13f
    private val ROUTE_COLOR = Color.BLUE
    private val ROUTE_WIDTH = 10f

    // Marker icons
    private var iconCurrentLocation: com.google.android.gms.maps.model.BitmapDescriptor? = null
    private var iconDeployment: com.google.android.gms.maps.model.BitmapDescriptor? = null

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    // Keep track of markers so we can clear them without touching the route
    private val deploymentMarkers = mutableListOf<Marker>()
    private var meMarker: Marker? = null


    /* =========================================================
     * Permissions launcher
     * ========================================================= */
    private val locationPermsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            enableMyLocationSafely()
            startLocationUpdatesSafely()
            primeLocationOnce()
        } else {
            toast("Location permission denied")
        }
    }

    /* =========================================================
     * Lifecycle
     * ========================================================= */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUniDeploymentBinding.inflate(inflater, container, false)

        // Make sure UserSession is initialized
        UserSession.init(requireContext().applicationContext)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvHeadline.text = "Deployment Overview"

        // --- Map ---
        val existing =
            childFragmentManager.findFragmentById(R.id.inboxMapContainer) as? SupportMapFragment
        val mapFragment = existing ?: SupportMapFragment.newInstance().also {
            childFragmentManager.beginTransaction()
                .replace(R.id.inboxMapContainer, it)
                .commit()
        }
        mapFragment.getMapAsync(this)

        // --- Recycler ---
        adapter = DeploymentAdapter(childFragmentManager)
        binding.recentReportRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@UnitDeploymentFragment.adapter
        }

        // initially show empty text until we load
        binding.emptyStateText.visibility = View.VISIBLE
        binding.emptyStateText.text = "Loading deployments…"

        // Load deployments for the logged-in unit
        loadDeploymentsForThisUnit()
    }

    /* =========================================================
     * Map callbacks
     * ========================================================= */
    @RequiresPermission(
        allOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    override fun onMapReady(map: GoogleMap) {
        gMap = map
        mapReady = true

        ensureIcons()  // 👉 make sure icons exist

        gMap?.uiSettings?.isZoomControlsEnabled = true
        gMap?.uiSettings?.isMyLocationButtonEnabled = true

        if (hasLocationPermission()) {
            enableMyLocationSafely()
            startLocationUpdatesSafely()
            primeLocationOnce()
        } else {
            requestLocationPerms()
        }

        // Click on deployment marker => draw route
        gMap?.setOnMarkerClickListener { marker ->
            val tag = marker.tag
            val deployment = tag as? DeploymentItem ?: return@setOnMarkerClickListener false

            if (userLatitude == 0.0 && userLongitude == 0.0) {
                toast("Waiting for your location…")
                return@setOnMarkerClickListener true
            }

            val destLat = deployment.latitude
            val destLng = deployment.longitude
            if (destLat == null || destLng == null) {
                toast("Deployment has no coordinates.")
                return@setOnMarkerClickListener true
            }

            val origin = LatLng(userLatitude, userLongitude)
            val dest = LatLng(destLat, destLng)

            drawRouteOSRM(origin, dest, deployment.purpose) {
                val km = (activeDistanceMeters ?: 0) / 1000.0
                val mins = (activeDurationSec ?: 0) / 60.0
                toast("${deployment.purpose} • ${"%.1f".format(km)} km • ${"%.0f".format(mins)} min")
            }

            true
        }

        // If deployments already loaded before map was ready, push them to map now
        updateMapMarkers(currentDeployments)
    }

    private fun ensureIcons() {
        if (iconCurrentLocation == null) {
            // solid blue circle for current location
            iconCurrentLocation = bitmapFromDrawable(
                R.drawable.ic_unit_marker,  // you create this
                tintColor = Color.parseColor("#1E88E5"), // blue
                widthDp = 26,
                heightDp = 26
            )
        }

        if (iconDeployment == null) {
            // any custom icon for deployments (no pin)
            iconDeployment = bitmapFromDrawable(
                R.drawable.ic_event,         // you create this
                tintColor = null,                       // or give a tint if you want
                widthDp = 26,
                heightDp = 26
            )
        }
    }

    private fun bitmapFromDrawable(
        @DrawableRes resId: Int,
        tintColor: Int?,
        widthDp: Int,
        heightDp: Int
    ): com.google.android.gms.maps.model.BitmapDescriptor {
        val ctx = requireContext()
        val drawable = AppCompatResources.getDrawable(ctx, resId)!!.mutate()

        tintColor?.let { DrawableCompat.setTint(drawable, it) }

        val wPx = widthDp.dp()
        val hPx = heightDp.dp()
        drawable.setBounds(0, 0, wPx, hPx)

        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.draw(canvas)

        return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bmp)
    }


    /* =========================================================
     * Load deployments for this unit
     * ========================================================= */
    private fun loadDeploymentsForThisUnit() {
        val unitId = UserSession.getUnitId()

        if (unitId.isNullOrEmpty()) {
            adapter.setItems(emptyList())
            binding.emptyStateText.visibility = View.VISIBLE
            binding.emptyStateText.text = "No unit session found."
            return
        }

        // remove old listener if any
        deploymentListener?.let { deploymentRootRef.removeEventListener(it) }

        deploymentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<DeploymentItem>()

                for (depSnap in snapshot.children) {
                    val purpose = depSnap.child("purpose").getValue(String::class.java) ?: continue
                    val date = depSnap.child("date").getValue(String::class.java) ?: ""
                    val lat = depSnap.child("latitude").getValue(Double::class.java)
                    val lng = depSnap.child("longitude").getValue(Double::class.java)

                    val unitsSnap = depSnap.child("units")
                    var assigned = false

                    for (unitSnap in unitsSnap.children) {
                        val uId = unitSnap.child("unitId").getValue(String::class.java)
                        if (uId == unitId) {
                            assigned = true
                            break
                        }
                    }

                    if (!assigned) continue

                    val deploymentId = depSnap.key ?: continue

                    list += DeploymentItem(
                        deploymentId = deploymentId,
                        purpose = purpose,
                        date = date,
                        latitude = lat,
                        longitude = lng
                    )
                }

                currentDeployments = list
                adapter.setItems(list)

                if (list.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.emptyStateText.text = "No deployments assigned to this unit."
                } else {
                    binding.emptyStateText.visibility = View.GONE
                }

                updateMapMarkers(list)
            }

            override fun onCancelled(error: DatabaseError) {
                adapter.setItems(emptyList())
                binding.emptyStateText.visibility = View.VISIBLE
                binding.emptyStateText.text = "Failed to load deployments."
            }
        }

        deploymentRootRef.addValueEventListener(deploymentListener as ValueEventListener)
    }

    /* =========================================================
     * Map markers
     * ========================================================= */
    private fun updateMapMarkers(deployments: List<DeploymentItem>) {
        val map = gMap ?: return

        // 👇 ONLY remove old markers, do NOT clear the whole map, do NOT clear route
        deploymentMarkers.forEach { it.remove() }
        deploymentMarkers.clear()
        meMarker?.remove()
        meMarker = null

        // Deployment markers
        deployments.forEach { item ->
            val lat = item.latitude
            val lng = item.longitude
            if (lat != null && lng != null) {
                val pos = LatLng(lat, lng)
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .title(item.purpose)
                        .snippet("Date: ${item.date}")
                        .icon(iconDeployment)
                        .anchor(0.5f, 0.5f)
                )
                if (marker != null) {
                    marker.tag = item
                    deploymentMarkers += marker
                }
            }
        }

        // Current unit location marker
        if (userLatitude != 0.0 || userLongitude != 0.0) {
            val me = LatLng(userLatitude, userLongitude)
            meMarker = map.addMarker(
                MarkerOptions()
                    .position(me)
                    .title("Your Location")
                    .icon(iconCurrentLocation)
                    .anchor(0.5f, 0.5f)
            )

            if (!cameraFittedOnce) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(me, DEFAULT_ZOOM))
                cameraFittedOnce = true
            }
        } else if (deployments.isNotEmpty()) {
            // Fallback: center to first deployment
            val first = deployments.first()
            if (first.latitude != null && first.longitude != null) {
                val pos = LatLng(first.latitude, first.longitude)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, DEFAULT_ZOOM))
                cameraFittedOnce = true
            }
        } else {
            // Nothing -> default center
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM))
        }
    }


    /* =========================================================
     * OSRM route helpers (OpenStreetMap)
     * ========================================================= */
    private fun drawRouteOSRM(origin: LatLng, dest: LatLng, title: String, onDone: (() -> Unit)? = null) {
        val seq = routeRequestSeq.incrementAndGet()
        clearActiveRoute()

        Thread {
            var points: List<LatLng> = emptyList()
            var meters = 0L
            var seconds = 0L

            try {
                val url =
                    "https://router.project-osrm.org/route/v1/driving/" +
                            "${origin.longitude},${origin.latitude};" +
                            "${dest.longitude},${dest.latitude}" +
                            "?overview=full&geometries=polyline"

                val conn = URL(url).openConnection() as HttpURLConnection
                val code = conn.responseCode
                val input = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = BufferedReader(InputStreamReader(input)).use { it.readText() }
                val json = JSONObject(text)
                conn.disconnect()

                if (json.optString("code") == "Ok") {
                    val route = json.getJSONArray("routes").getJSONObject(0)
                    meters = route.getDouble("distance").toLong()
                    seconds = route.getDouble("duration").toLong()
                    val polyline = route.getString("geometry")
                    points = decodePolyline(polyline)
                }
            } catch (e: Exception) {
                postToMain { toast("Route error: ${e.message}") }
            }

            postToMain {
                if (routeRequestSeq.get() != seq) return@postToMain
                val map = gMap ?: return@postToMain

                if (points.isNotEmpty()) {
                    activePolyline = map.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .width(ROUTE_WIDTH)
                            .color(ROUTE_COLOR)
                    )
                }

                activeDistanceMeters = meters
                activeDurationSec = seconds
                onDone?.invoke()
            }
        }.start()
    }

    private fun clearActiveRoute() {
        activePolyline?.remove()
        activePolyline = null
        activeDistanceMeters = null
        activeDurationSec = null
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng
            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    /* =========================================================
     * Location helpers
     * ========================================================= */
    private fun hasLocationPermission(): Boolean {
        val ctx = context ?: return false
        val fine = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestLocationPerms() {
        locationPermsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationSafely() {
        val map = gMap ?: return
        if (!hasLocationPermission()) return
        try {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        } catch (_: SecurityException) {
        }
    }

    @RequiresPermission(
        allOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    private fun startLocationUpdates() {
        if (!isAdded || locationUpdatesStarted || !hasLocationPermission()) return
        locationUpdatesStarted = true

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10_000L
        ).setMinUpdateIntervalMillis(5_000L).build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun startLocationUpdatesSafely() {
        if (!isAdded || locationUpdatesStarted) return
        if (!hasLocationPermission()) {
            requestLocationPerms()
            return
        }
        try {
            startLocationUpdates()
        } catch (e: SecurityException) {
            toast("Cannot start location updates: permission denied")
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            userLatitude = loc.latitude
            userLongitude = loc.longitude

            if (!cameraFittedOnce) {
                val me = LatLng(userLatitude, userLongitude)
                gMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        me,
                        DEFAULT_ZOOM
                    )
                )
                cameraFittedOnce = true
            }

            // Update markers so the "Your Location" marker moves
            updateMapMarkers(currentDeployments)
        }
    }

    @SuppressLint("MissingPermission")
    private fun primeLocationOnce() {
        if (!hasLocationPermission()) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLatitude = location.latitude
                userLongitude = location.longitude
                val me = LatLng(userLatitude, userLongitude)
                gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(me, DEFAULT_ZOOM))
                cameraFittedOnce = true
                updateMapMarkers(currentDeployments)
            } else {
                // fallback after short delay
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!cameraFittedOnce) {
                        gMap?.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                DEFAULT_CENTER,
                                DEFAULT_ZOOM
                            )
                        )
                    }
                }, 2000)
            }
        }
    }

    /* =========================================================
     * Misc helpers
     * ========================================================= */
    private fun toast(msg: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun postToMain(block: () -> Unit) {
        if (!isAdded) return
        requireActivity().runOnUiThread(block)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        deploymentListener?.let { deploymentRootRef.removeEventListener(it) }
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Clean markers & route
        deploymentMarkers.forEach { it.remove() }
        deploymentMarkers.clear()
        meMarker?.remove()
        meMarker = null
        clearActiveRoute()

        _binding = null
    }

}
