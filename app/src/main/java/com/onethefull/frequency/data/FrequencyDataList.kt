package com.onethefull.frequency.data

import com.google.gson.annotations.SerializedName

data class FrequencyDataList(

    @SerializedName("freq_list")
    var freqList: List<FrequencyData>? = listOf(),
    @SerializedName("flag")
    var flag: String? = ""
)
