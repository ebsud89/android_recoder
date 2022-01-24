package com.onethefull.frequency.data

import com.google.gson.annotations.SerializedName

data class FrequencyDataList(
    @SerializedName("frequency_list")
    var frequencyList: List<FrequencyData>? = listOf(),
    @SerializedName("flag")
    var flag: String? = ""
)
