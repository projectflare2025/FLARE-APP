package com.example.flare_capstone.data.model

// Firebase needs an empty constructor → use default values
data class InvestigatorReport(
    var acceptedAt: Long? = null,
    var investigatorId: String? = null,
    var reportId: String? = null,
    var reportType: String? = null,
    var stationId: String? = null,
    var status: String? = null
)
