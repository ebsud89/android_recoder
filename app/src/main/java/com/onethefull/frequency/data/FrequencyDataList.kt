package com.onethefull.frequency.data

import com.google.gson.annotations.SerializedName

data class FrequencyDataList(
    @SerializedName("face_info")
    var faceInfo: List<FrequncyData>? = listOf(),
    @SerializedName("flag")
    var flag: String? = ""
)
