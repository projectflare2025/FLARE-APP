package com.example.flare_capstone.data.model

data class FireReport(
    val name: String = "",
    var email: String = "",        // 👈 add email
    val contact: String = "",
    val type: String = "",
    val date: String = "",
    val reportTime: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val exactLocation: String = "",
    val location: String = "",
    val photoBase64: String = "",
    val timeStamp: Long = 0L,
    val status: String = "Pending",
    var fireStationName: String = "Canocotan Fire Station",
    val adminNotif:Boolean = false,
    val isRead: Boolean = false,
    val category: String = "",
    var fireStationId: String,
    val isMyLocation:Boolean = false,
    var userDocId: String = "" // 👈 add this
)