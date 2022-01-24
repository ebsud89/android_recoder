package com.onethefull.frequency.api

import com.onethefull.frequency.data.FrequencyDataList
import retrofit2.Response
import retrofit2.http.POST
import io.reactivex.Observable
import retrofit2.http.Body

interface ApiService {

    @POST("http://trackingeducollectflask.ktpaasta-v4.kr/collector/insert")
    fun sendJsonData(@Body body: FrequencyDataList): Observable<Response<Void>>

    // TODO: start, stop 전송 URL 정해지면 입력
//    @POST("http://trackingeducollectflask.ktpaasta-v4.kr/collector/timestamp")
//    fun sendSign(@Body body: Timestamp): Observable<Response<Void>>
}