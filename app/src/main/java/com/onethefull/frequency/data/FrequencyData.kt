package com.onethefull.frequency.data

import com.google.gson.annotations.SerializedName

data class FrequencyData (
    @SerializedName("freqHz")
    var freqHz: Int? = 0,
    @SerializedName("freqSize")
    var freqSize: Double? = 0.0,
    @SerializedName("timestamp")
    // action type : 'start' or 'end'
    var timestamp: String? = "",
    @SerializedName("seqNum")
    var seqNum: Int? = 0
)