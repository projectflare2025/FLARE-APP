// com/example/flare_capstone/views/fragment/investigator/InvestigatorFormViewModel.kt
package com.example.flare_capstone.views.fragment.investigator

import androidx.lifecycle.ViewModel

class InvestigatorFormViewModel : ViewModel() {

    // From adapter / investigatorReports
    var incidentId: String? = null
    var reportType: String? = null      // FireReport, OtherEmergencyReport, etc.

    // From AllReport (base info)
    var baseDate: String? = null        // report date from AllReport
    var baseCallerName: String? = null  // e.g. reporter or caller from AllReport

    // STEP 1 (Incident info)
    var incidentDate: String? = null          // usually same as baseDate
    var fireCaller: String? = null
    var caller: String? = null
    var alarmStatus: String? = null
    var causeOfFire: String? = null
    var investigationDetails: String? = null

    // STEP 2 (Evidence)
    val evidenceImagesBase64 = mutableListOf<String>() // multiple photos
    var evidenceFileName: String? = null               // optional file name

    // STEP 4 (Response details)
    var timeDeparted: String? = null
    var timeArrival: String? = null
    var fireUnderControlTime: String? = null
    var fireOutTime: String? = null
    var groundCommander: String? = null
    var firetrucksResponded: String? = null
    var listOfResponders: String? = null
    var fuelConsumedLiters: String? = null
    var distanceOfFireSceneKm: String? = null

    // STEP 5 (Location / property)
    var establishmentName: String? = null
    var ownerName: String? = null
    var occupantName: String? = null
    var landAreaInvolved: String? = null
}
