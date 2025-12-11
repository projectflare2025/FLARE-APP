package com.example.flare_capstone.views.activity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.example.flare_capstone.R
import com.example.flare_capstone.databinding.ActivityDashboardFireFighterBinding
import com.example.flare_capstone.views.fragment.bfp.FireFighterResponseActivity
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import np.com.susanthapa.curved_bottom_navigation.CbnMenuItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FirefighterActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "FF-Notif"
        const val NOTIF_REQ_CODE = 9001

        // one channel per type
        const val CH_FIRE  = "ff_fire"
        const val CH_OTHER = "ff_other"
        const val CH_EMS   = "ff_ems"
        const val CH_SMS   = "ff_sms"

        const val OLD_CHANNEL_ID = "ff_incidents"

        const val CH_MSG  = "ff_admin_msg"

        // 🔥 NEW: location permission request code
        const val LOC_REQ_CODE = 9002
    }

    private lateinit var binding: ActivityDashboardFireFighterBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var prefs: SharedPreferences

    // 🔹 Unit ID (from login session) for unitReports.unitId AND DeploymentRoot.units.unitId
    private var myUnitId: String = ""

    // 🔹 For station-based AdminMessages (still using the old tree)
    private var stationAccountKey: String? = null

    // Dedupe + lifecycle
    private val shownKeys = mutableSetOf<String>()
    private val liveListeners = mutableListOf<Pair<Query, ChildEventListener>>()
    private val liveValueListeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    private var unreadAdminCount = 0

    // 🔥 NEW: Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDashboardFireFighterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()

        database = FirebaseDatabase.getInstance()
        prefs = getSharedPreferences("ff_notifs", MODE_PRIVATE)

        // 🔹 Unit ID used in unitReports.unitId and DeploymentRoot.units.unitId
        val sessionPrefs = getSharedPreferences("flare_session", MODE_PRIVATE)
        myUnitId = sessionPrefs.getString("unitId", null)
            ?: FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        Log.d(TAG, "myUnitId=$myUnitId")

        // Station mapping only for AdminMessages
        val email = FirebaseAuth.getInstance().currentUser?.email?.lowercase()
        stationAccountKey = stationAccountForEmail(email)
        Log.d(TAG, "email=$email stationAccountKey=$stationAccountKey")

        createNotificationChannels()
        maybeRequestPostNotifPermission()

        // 🔥 init fused location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // 🔥 start location updates (will request permission if needed)
        maybeStartLocationUpdates()

        // 🔹 Listen for dispatches assigned to THIS UNIT
        listenUnitDispatches("FireReport",                     "New FIRE report")
        listenUnitDispatches("OtherEmergencyReport",           "New OTHER emergency")
        listenUnitDispatches("EmergencyMedicalServicesReport", "New EMS report")
        listenUnitDispatches("SmsReport",                      "New SMS emergency")

        // Handle tap from a notification when app is cold-started
        handleIntent(intent)

        stationAccountKey?.let { acct ->
            watchAdminUnreadCount(acct)
            listenAdminMessagesForNotifications(acct)
        }
    }

    private fun setupBottomNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_firefighter) as NavHostFragment
        val navController = navHostFragment.navController

        val menuItems = arrayOf(
            CbnMenuItem(R.drawable.ic_home, R.drawable.avd_home , R.id.homeFireFighterFragment),
            CbnMenuItem(R.drawable.ic_services, R.drawable.avd_services, R.id.fireFighterReportFragment),
            CbnMenuItem(R.drawable.ic_dashboard, R.drawable.avd_dashboard, R.id.inboxFireFighterFragment),
            CbnMenuItem(R.drawable.ic_profile, R.drawable.avd_profile, R.id.profileFireFighterFragment)
        )

        binding.bottomNavigationFirefighter.setMenuItems(menuItems, 0)
        binding.bottomNavigationFirefighter.setupWithNavController(navController)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /* -------------------------------------------------------------
     *  🔥 LOCATION UPDATES → DeploymentRoot
     * ------------------------------------------------------------- */

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun maybeStartLocationUpdates() {
        if (myUnitId.isBlank()) {
            Log.w(TAG, "No unitId; skipping location tracking")
            return
        }

        if (!hasLocationPermission()) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOC_REQ_CODE
            )
            return
        }

        startLocationUpdatesInternal()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startLocationUpdatesInternal() {
        if (!hasLocationPermission()) return
        if (locationCallback != null) return  // already started

        Log.d(TAG, "Starting location updates for unitId=$myUnitId")

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000L              // 10s between updates (tune as needed)
        )
            .setMinUpdateIntervalMillis(5_000L)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val lat = loc.latitude
                val lng = loc.longitude
                Log.d(TAG, "New location: lat=$lat, lng=$lng")
                updateUnitLocationInDeployments(lat, lng)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback!!,
            mainLooper
        )
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { cb ->
            fusedLocationClient.removeLocationUpdates(cb)
        }
        locationCallback = null
    }

    /**
     * 🔑 Core logic:
     * For every deployment under DeploymentRoot where
     * units/{someKey}/unitId == myUnitId,
     * update latitude & longitude for that unit node.
     */
    private fun updateUnitLocationInDeployments(lat: Double, lng: Double) {
        if (myUnitId.isBlank()) return

        val depRootRef = database.getReference("DeploymentRoot")

        depRootRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { depSnap ->
                    val unitsSnap = depSnap.child("units")
                    unitsSnap.children.forEach { unitSnap ->
                        val unitIdInNode =
                            unitSnap.child("unitId").getValue(String::class.java)
                        if (unitIdInNode == myUnitId) {
                            // ✅ This is the same unit as the logged-in firefighter
                            Log.d(
                                TAG,
                                "Updating location for deployment=${depSnap.key}, unit=${unitSnap.key}"
                            )
                            unitSnap.ref.child("latitude").setValue(lat)
                            unitSnap.ref.child("longitude").setValue(lng)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "updateUnitLocationInDeployments cancelled: ${error.message}")
            }
        })
    }

    /* -------------------------------------------------------------
     *  ADMIN MESSAGES (station-based, not per-unit)
     * ------------------------------------------------------------- */

    private fun watchAdminUnreadCount(accountKey: String) {
        val path =
            "TagumCityCentralFireStation/FireFighter/AllFireFighterAccount/$accountKey/AdminMessages"
        val ref = database.getReference(path)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var cnt = 0
                snapshot.children.forEach { msgSnap ->
                    val sender = msgSnap.child("sender").getValue(String::class.java) ?: ""
                    val isRead = msgSnap.child("isRead").getValue(Boolean::class.java) ?: true
                    if (sender.equals("admin", ignoreCase = true) && !isRead) cnt++
                }
                unreadAdminCount = cnt
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)
        liveValueListeners += (ref to listener)
    }

    private fun listenAdminMessagesForNotifications(accountKey: String) {
        val path =
            "TagumCityCentralFireStation/FireFighter/AllFireFighterAccount/$accountKey/AdminMessages"
        val ref = database.getReference(path)

        ref.orderByChild("timestamp").limitToLast(200)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var baseTs = 0L
                    snapshot.children.forEach { c ->
                        val ts = c.child("timestamp").getValue(Long::class.java) ?: 0L
                        if (ts > baseTs) baseTs = ts
                    }
                    attachAdminRealtime(ref, baseTs)
                }

                override fun onCancelled(error: DatabaseError) {
                    attachAdminRealtime(ref, 0L)
                }
            })
    }

    private fun attachAdminRealtime(ref: DatabaseReference, baseTsMs: Long) {
        val l = object : ChildEventListener {
            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                handleAdminMessageSnap(snap, baseTsMs)
            }

            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onChildChanged(snap: DataSnapshot, prev: String?) {
                handleAdminMessageSnap(snap, baseTsMs)
            }

            override fun onChildRemoved(snap: DataSnapshot) {}
            override fun onChildMoved(snap: DataSnapshot, prev: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addChildEventListener(l)
        liveListeners += (ref to l)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleAdminMessageSnap(snap: DataSnapshot, baseTsMs: Long) {
        val id = snap.key ?: return
        val sender = snap.child("sender").getValue(String::class.java) ?: ""
        val isRead = snap.child("isRead").getValue(Boolean::class.java) ?: false
        val ts = snap.child("timestamp").getValue(Long::class.java) ?: 0L

        if (!sender.equals("admin", ignoreCase = true)) return
        if (isRead) return
        if (ts <= baseTsMs) return

        val key = "adminmsg::$id"
        if (alreadyShown(key)) return

        showAdminMessageNotification(snap)
        markShown(key)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showAdminMessageNotification(snap: DataSnapshot) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val messageText = snap.child("text").getValue(String::class.java)
        val hasImage = snap.hasChild("imageBase64")
        val hasAudio = snap.hasChild("audioBase64")

        val preview = when {
            !messageText.isNullOrBlank() -> messageText
            hasImage -> "Admin sent a photo"
            hasAudio -> "Admin sent a voice message"
            else -> "New message from Admin"
        }

        val intent = Intent(this, FireFighterResponseActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_notification_admin", true)
        }
        val reqCode = ("adminmsg::${snap.key}").hashCode()
        val pending = PendingIntent.getActivity(
            this, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CH_MSG)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("New message from Admin")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)

        try {
            val notifId = ("adminmsg::${snap.key}").hashCode()
            NotificationManagerCompat.from(this).notify(notifId, builder.build())
            Log.d(TAG, "NOTIFY(admin) id=$notifId msg=$preview")
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted: ${e.message}")
        }
    }

    fun markStationAdminRead(stationId: String) {
        val path =
            "TagumCityCentralFireStation/FireFighter/AllFireFighterAccount/$stationAccountKey/AdminMessages"
        val ref = database.getReference(path)
        ref.orderByChild("sender").equalTo("admin")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    for (msg in snap.children) {
                        val isRead = msg.child("isRead").getValue(Boolean::class.java) ?: true
                        if (!isRead) {
                            msg.ref.child("isRead").setValue(true)
                        }
                    }
                }

                override fun onCancelled(err: DatabaseError) {}
            })
    }

    /* -------------------------------------------------------------
     *  UNIT INCIDENT NOTIFICATIONS (AllReport + unitReports)
     * ------------------------------------------------------------- */

    private fun listenUnitDispatches(typeNode: String, title: String) {
        if (myUnitId.isBlank()) {
            Log.w(TAG, "No unitId; skipping unitReports listener for $typeNode")
            return
        }

        val ref = database.getReference("unitReports").child(typeNode)
        val q = ref.orderByChild("unitId").equalTo(myUnitId)

        Log.d(TAG, "listenUnitDispatches attach type=$typeNode unitId=$myUnitId")

        q.limitToLast(200).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var baseTs = 0L
                snapshot.children.forEach { c ->
                    val rawTs = c.child("acceptedAt").getValue(Long::class.java) ?: 0L
                    if (rawTs > baseTs) baseTs = rawTs
                }
                Log.d(TAG, "[unitReports/$typeNode] initial done; baseTs=$baseTs")
                attachUnitRealtime(q, typeNode, title, baseTs)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(
                    TAG,
                    "[unitReports/$typeNode] initial cancelled: ${error.message} (fallback baseTs=0)"
                )
                attachUnitRealtime(q, typeNode, title, 0L)
            }
        })
    }

    private fun attachUnitRealtime(
        q: Query,
        typeNode: String,
        title: String,
        baseTsMs: Long
    ) {
        val l = object : ChildEventListener {
            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                handleUnitDispatchSnap(snap, typeNode, title, baseTsMs)
            }

            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onChildChanged(snap: DataSnapshot, prev: String?) {
                handleUnitDispatchSnap(snap, typeNode, title, baseTsMs)
            }

            override fun onChildRemoved(snap: DataSnapshot) {}
            override fun onChildMoved(snap: DataSnapshot, prev: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "[unitReports/$typeNode] realtime cancelled: ${error.message}")
            }
        }

        q.addChildEventListener(l)
        liveListeners += (q to l)
        Log.d(TAG, "[unitReports/$typeNode] realtime attached (unitId=$myUnitId)")
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleUnitDispatchSnap(
        snap: DataSnapshot,
        typeNode: String,
        title: String,
        baseTsMs: Long
    ) {
        val dispatchId = snap.key ?: return
        val status = snap.child("status").getValue(String::class.java)
        val rawTs = snap.child("acceptedAt").getValue(Long::class.java) ?: 0L
        val ts = rawTs

        Log.d(TAG, "UNIT[$typeNode] dispatch=$dispatchId status=$status ts=$ts baseTs=$baseTsMs")

        if (!statusIsOngoingRaw(status)) return
        if (ts <= baseTsMs) {
            Log.d(TAG, "→ skip unit dispatch (old vs baseTs)")
            return
        }

        val reportId = snap.child("reportId").getValue(String::class.java) ?: return

        val key = "unit::$typeNode::$reportId"
        if (alreadyShown(key)) {
            Log.d(TAG, "→ skip unit dispatch (already shown) key=$key")
            return
        }

        val reportPath = "AllReport/$typeNode/$reportId"
        val reportRef = database.getReference(reportPath)

        reportRef.addListenerForSingleValueEvent(object : ValueEventListener {
            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onDataChange(incidentSnap: DataSnapshot) {
                if (!incidentSnap.exists()) {
                    Log.w(TAG, "Missing AllReport node for $reportPath")
                    return
                }
                val pathForNotif = reportPath
                showIncidentNotification(title, incidentSnap, pathForNotif)
                markShown(key)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to load AllReport for $reportPath: ${error.message}")
            }
        })
    }

    /* -------------------------------------------------------------
     *  INCIDENT NOTIFICATION BUILDER
     * ------------------------------------------------------------- */

    private fun channelForPath(path: String): String = when {
        path.endsWith("FireReport") -> CH_FIRE
        path.endsWith("OtherEmergencyReport") -> CH_OTHER
        path.endsWith("EmergencyMedicalServicesReport") -> CH_EMS
        path.endsWith("SmsReport") -> CH_SMS
        else -> CH_OTHER
    }

    private fun sourceForPath(path: String): String = when {
        path.endsWith("FireReport") -> "FIRE"
        path.endsWith("OtherEmergencyReport") -> "OTHER"
        path.endsWith("EmergencyMedicalServicesReport") -> "EMS"
        path.endsWith("SmsReport") -> "SMS"
        else -> "OTHER"
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showIncidentNotification(title: String, snap: DataSnapshot, path: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled at OS level")
            return
        }

        val id = snap.key ?: return
        val exactLocation = snap.child("exactLocation").getValue(String::class.java)
            ?: snap.child("location").getValue(String::class.java)
            ?: "Unknown location"
        val message = "Station: ${friendlyStationLabel()} • $exactLocation"

        val channelId = channelForPath(path)
        val srcStr = sourceForPath(path)

        val intent = Intent(this, FirefighterActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_notification", true)
            putExtra("select_source", srcStr)
            putExtra("select_id", id)
        }

        val reqCode = ("$path::$id").hashCode()

        val pending = PendingIntent.getActivity(
            this, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            when (channelId) {
                CH_FIRE -> {
                    builder.setSound(rawSoundUri(R.raw.fire_report))
                    builder.setVibrate(longArrayOf(0, 600, 200, 600, 200, 600))
                }

                CH_OTHER -> {
                    builder.setSound(rawSoundUri(R.raw.other_emergency_report))
                    builder.setVibrate(longArrayOf(0, 400, 150, 400))
                }

                CH_EMS -> {
                    builder.setSound(rawSoundUri(R.raw.emergecy_medical_services_report))
                    builder.setVibrate(longArrayOf(0, 400, 150, 400))
                }

                CH_SMS -> {
                    builder.setSound(rawSoundUri(R.raw.sms_report))
                    builder.setVibrate(longArrayOf(0, 400, 150, 400))
                }
            }
        }

        try {
            val notifId = ("$path::$id").hashCode()
            NotificationManagerCompat.from(this).notify(notifId, builder.build())
            Log.d(TAG, "NOTIFY id=$notifId ch=$channelId title=$title msg=$message")
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted: ${e.message}")
        }
    }

    /* -------------------------------------------------------------
     *  CHANNELS
     * ------------------------------------------------------------- */

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        runCatching { nm.deleteNotificationChannel(OLD_CHANNEL_ID) }

        recreateChannel(
            id = CH_FIRE,
            name = "Firefighter • FIRE",
            soundUri = rawSoundUri(R.raw.fire_report),
            useDefault = false
        )
        recreateChannel(
            id = CH_OTHER,
            name = "Firefighter • OTHER",
            soundUri = rawSoundUri(R.raw.other_emergency_report),
            useDefault = false
        )
        recreateChannel(
            id = CH_EMS,
            name = "Firefighter • EMS",
            soundUri = rawSoundUri(R.raw.emergecy_medical_services_report),
            useDefault = false
        )
        recreateChannel(
            id = CH_SMS,
            name = "Firefighter • SMS",
            soundUri = rawSoundUri(R.raw.sms_report),
            useDefault = true
        )
        recreateChannel(
            id = CH_MSG,
            name = "Firefighter • Admin Messages",
            soundUri = rawSoundUri(R.raw.message_notif),
            useDefault = true
        )
    }

    private fun recreateChannel(id: String, name: String, soundUri: Uri?, useDefault: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        runCatching { nm.deleteNotificationChannel(id) }

        val ch = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)

        val aa = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (useDefault) {
            val defaultUri =
                soundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ch.setSound(defaultUri, aa)
        } else if (soundUri != null) {
            ch.setSound(soundUri, aa)
        }

        nm.createNotificationChannel(ch)
        Log.d(TAG, "channel created ($id) sound=${soundUri?.toString() ?: "default"}")
    }

    private fun rawSoundUri(@RawRes res: Int): Uri =
        Uri.parse("android.resource://$packageName/$res")

    /* -------------------------------------------------------------
     *  HELPERS
     * ------------------------------------------------------------- */

    private fun stationAccountForEmail(email: String?): String? {
        val e = email ?: return null
        return when (e) {
            "tcwestfiresubstation@gmail.com" -> "MabiniFireFighterAccount"
            "lafilipinafire@gmail.com"       -> "LaFilipinaFireFighterAccount"
            "bfp_tagumcity@yahoo.com"        -> "CanocotanFireFighterAccount"
            else -> null
        }
    }

    private fun friendlyStationLabel(): String {
        return when (stationAccountKey) {
            "MabiniFireFighterAccount"     -> "Mabini"
            "LaFilipinaFireFighterAccount" -> "La Filipina"
            "CanocotanFireFighterAccount"  -> "Canocotan"
            else -> "Unknown"
        }
    }

    private fun statusIsOngoingRaw(raw: String?): Boolean {
        val norm = raw?.trim()?.replace("-", "")?.lowercase() ?: return false
        return norm == "ongoing"
    }

    private fun statusIsOngoing(snap: DataSnapshot): Boolean {
        val raw = snap.child("status").getValue(String::class.java)
        return statusIsOngoingRaw(raw)
    }

    private fun getLongRelaxed(node: DataSnapshot, key: String): Long? {
        val v = node.child(key).value
        return when (v) {
            is Number -> v.toLong()
            is String -> v.trim().toLongOrNull()
            else -> null
        }
    }

    private fun getEpochFromDateTime(node: DataSnapshot): Long? {
        val dateStr = node.child("date").getValue(String::class.java)?.trim()
        val timeStr = node.child("time").getValue(String::class.java)?.trim()
        if (dateStr.isNullOrEmpty() || timeStr.isNullOrEmpty()) return null
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            fmt.timeZone = TimeZone.getDefault()
            fmt.parse("$dateStr $timeStr")?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun readTimestampMillis(node: DataSnapshot): Long? {
        val raw = getLongRelaxed(node, "acceptedAt")
            ?: getLongRelaxed(node, "timeStamp")
            ?: getLongRelaxed(node, "timestamp")
            ?: getLongRelaxed(node, "time")
            ?: getEpochFromDateTime(node)
            ?: return null
        val ms = if (raw in 1..9_999_999_999L) raw * 1000 else raw
        return if (ms > 0) ms else null
    }

    private fun alreadyShown(key: String): Boolean =
        shownKeys.contains(key) || prefs.getBoolean(key, false)

    private fun markShown(key: String) {
        shownKeys.add(key)
        prefs.edit().putBoolean(key, true).apply()
        Log.d(TAG, "markShown $key")
    }

    private fun maybeRequestPostNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "notif permission granted=$granted")
            if (!granted) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIF_REQ_CODE
                )
            }
        }
    }

    /* -------------------------------------------------------------
     *  DELIVER SELECTION TO HOME MAP
     * ------------------------------------------------------------- */

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("from_notification", false) == true) {
            val srcStr = intent.getStringExtra("select_source")
            val id = intent.getStringExtra("select_id")
            if (!srcStr.isNullOrBlank() && !id.isNullOrBlank()) {
                Log.d(TAG, "deliverSelectionToHome src=$srcStr id=$id")
                deliverSelectionToHome(srcStr, id)
            }
        }
    }

    private fun deliverSelectionToHome(srcStr: String, id: String) {
        supportFragmentManager.setFragmentResult(
            "select_incident",
            Bundle().apply {
                putString("source", srcStr)
                putString("id", id)
            }
        )
    }

    /* -------------------------------------------------------------
     *  PERMISSION RESULT
     * ------------------------------------------------------------- */

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOC_REQ_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted, starting updates")
                startLocationUpdatesInternal()
            } else {
                Log.w(TAG, "Location permission denied → no live tracking")
            }
        }
        // NOTIF_REQ_CODE is handled implicitly; nothing specific needed here
    }

    /* -------------------------------------------------------------
     *  CLEANUP
     * ------------------------------------------------------------- */

    override fun onDestroy() {
        super.onDestroy()
        liveListeners.forEach { (q, l) -> q.removeEventListener(l) }
        liveValueListeners.forEach { (ref, l) -> ref.removeEventListener(l) }
        liveListeners.clear()
        liveValueListeners.clear()

        // 🔥 stop GPS when activity is destroyed
        stopLocationUpdates()
    }
}
