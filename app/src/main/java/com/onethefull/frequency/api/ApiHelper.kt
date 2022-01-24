package com.onethefull.frequency.api

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class ApiHelper: ApiBase() {

    val nullOnEmptyConverterFactory = object : Converter.Factory() {
        fun converterFactory() = this
        override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit) = object :
            Converter<ResponseBody, Any?> {
            val nextResponseBodyConverter = retrofit.nextResponseBodyConverter<Any?>(converterFactory(), type, annotations)
            override fun convert(value: ResponseBody) = if (value.contentLength() != 0L) nextResponseBodyConverter.convert(value) else null
        }
    }

    // 네트뭐크 통신에 사용할 클라이언트 객체를 생성합니다.
    private fun provideOkHttpClient(interceptor: HttpLoggingInterceptor): OkHttpClient {
        val b = OkHttpClient.Builder()
            // 이 클라이언트를 통해 오고 가는 네트워크 요청/응답을 로그로 표시하도록 합니다.
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        b.addInterceptor(interceptor)
        return b.build()
    }

    // 네트워크 요청/응답을 로그에 표시하는 Interceptor 객체를 생성합니다.
    private fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        return interceptor
    }

    val provideApiService = Retrofit.Builder()
        .baseUrl(DB_ADDRESS)
        // 네트워크 요청 로그를 표시해 줍니다.
        .client(provideOkHttpClient(provideLoggingInterceptor()))
        .addConverterFactory(nullOnEmptyConverterFactory)
        // 받은 응답을 옵서버블 형태로 변환해 줍니다.
        .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
        // 서버에서 json 형식으로 데이터를 보내고 이를 파싱해서 받아옵니다.
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)


    companion object {
        private val DB_ADDRESS = "https://www.naver.com"

        @Volatile
        private var instance: ApiHelper? = null

        fun getInstance(): ApiHelper? {
            if (null == instance) {
                synchronized(ApiHelper::class.java) {
                    if (null == instance) {
                        instance = ApiHelper()
                    }
                }
            }
            return instance
        }
    }
}