package com.example.flare_capstone.data.model

data class OtherEmergency(
    val type: String,
    val name: String,
    val contact: String,
    val date: String,
    val reportTime: String,
    val latitude: String,
    val longitude: String,
    val location: String,
    val exactLocation: String = "",
    val lastReportedTime: Long,
    val timestamp: Long,
    var status: String = "Pending",
    var read: Boolean,
    var fireStationName: String = "",
    var photoBase64: String = "",
    val category: String = ""// ✅ add this line// <-- new field
)