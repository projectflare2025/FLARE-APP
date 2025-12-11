package com.example.flare_capstone.data.model

// Firebase needs an empty constructor → use default values
data class InvestigatorReport(
    var acceptedAt: Long? = null,
    var investigatorId: String? = null,
    var reportId: String? = null,
    var reportType: String? = null,  // FireReport, OtherEmergencyReport, etc.
    var stationId: String? = null,
    var status: String? = null,

    // Extra fields from AllReport/{typeCategory}/{reportId}
    var location: String? = null,
    var date: String? = null,
    var time: String? = null,
    var reporterName: String? = null   // ⭐ NEW
)
