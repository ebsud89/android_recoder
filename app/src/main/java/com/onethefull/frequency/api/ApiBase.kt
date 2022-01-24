package com.onethefull.frequency.api

import android.util.Log
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketException
import java.util.*
import java.util.concurrent.TimeUnit

abstract class ApiBase {

    private var mCompositeDisposableMap: MutableMap<Any, CompositeDisposable>? = null

    internal inner class ApiLogger : HttpLoggingInterceptor.Logger {
        override fun log(message: String) {
            Log.i("TEST", message)
        }
    }

    protected fun <T> createApiService(service: Class<T>, baseUrl: String): T {
        val okhttpBuilder = OkHttpClient.Builder()
        okhttpBuilder.addInterceptor(
            HttpLoggingInterceptor(ApiLogger()).setLevel(
                HttpLoggingInterceptor.Level.BODY
            )
        )
        okhttpBuilder.connectTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)
        okhttpBuilder.readTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)
        okhttpBuilder.writeTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)

        val retrofit = Retrofit.Builder()
            .client(okhttpBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .baseUrl(baseUrl)
            .build()
        mCompositeDisposableMap = HashMap()
        return retrofit.create(service)
    }


    protected fun <T> toSubscribe(
        tag: Any,
        observable: Observable<T>,
        responseCallback: BaseResponseCallback.Listener<T>,
        errorListener: BaseResponseCallback.ErrorListener
    ) {

        observable.observeOn(Schedulers.io())
            .unsubscribeOn(Schedulers.io())
            .subscribeOn(Schedulers.newThread())
            .subscribe(object : Observer<T> {

                var disp: Disposable? = null

                override fun onSubscribe(d: Disposable) {
                    Log.d("TEST", "ApiBase ==> onSubscribe")
                    disp = d
                    addDisposal(tag, disp)
                }

                override fun onNext(value: T) {
                    Log.d("TEST", "ApiBase ==> onNext $value")
                    responseCallback.onResponse(value)

                    RxJavaPlugins.setErrorHandler { e ->
                        var error = e
                        if (error is UndeliverableException) {
                            error = e.cause
                        }
                        if (error is IOException || error is SocketException) {
                            // fine, irrelevant network problem or API that throws on cancellation
                            return@setErrorHandler
                        }
                        if (error is InterruptedException) {
                            // fine, some blocking code was interrupted by a dispose call
                            return@setErrorHandler
                        }
                        if (error is NullPointerException || error is IllegalArgumentException) {
                            // that's likely a bug in the application
                            Thread.currentThread().uncaughtExceptionHandler
                                .uncaughtException(Thread.currentThread(), error)
                            return@setErrorHandler
                        }
                        if (error is IllegalStateException) {
                            // that's a bug in RxJava or in a custom operator
                            Thread.currentThread().uncaughtExceptionHandler
                                .uncaughtException(Thread.currentThread(), error)
                            return@setErrorHandler
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.d("TEST", "ApiBase ==> onError ${e.message}")
                    errorListener.onErrorResponse(e)


                }

                override fun onComplete() {
                    Log.d("TEST", "ApiBase ==> onComplete")
                    removeDisposal(tag, disp)
                }


            })
    }

    protected fun <T> toSubscribeMain(
        tag: Any,
        observable: Observable<T>,
        responseCallback: BaseResponseCallback.Listener<T>,
        errorListener: BaseResponseCallback.ErrorListener
    ) {

        observable.observeOn(Schedulers.io())
            .unsubscribeOn(Schedulers.io())
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<T> {

                var disp: Disposable? = null

                override fun onSubscribe(d: Disposable) {
                    Log.d("TEST", "ApiBase ==> onSubscribe")
                    disp = d
                    addDisposal(tag, disp)
                }

                override fun onNext(value: T) {
                    Log.d("TEST", "ApiBase ==> onNext $value")
                    responseCallback.onResponse(value)

                    RxJavaPlugins.setErrorHandler { e ->
                        var error = e
                        if (error is UndeliverableException) {
                            error = e.cause
                        }
                        if (error is IOException || error is SocketException) {
                            // fine, irrelevant network problem or API that throws on cancellation
                            return@setErrorHandler
                        }
                        if (error is InterruptedException) {
                            // fine, some blocking code was interrupted by a dispose call
                            return@setErrorHandler
                        }
                        if (error is NullPointerException || error is IllegalArgumentException) {
                            // that's likely a bug in the application
                            Thread.currentThread().uncaughtExceptionHandler
                                .uncaughtException(Thread.currentThread(), error)
                            return@setErrorHandler
                        }
                        if (error is IllegalStateException) {
                            // that's a bug in RxJava or in a custom operator
                            Thread.currentThread().uncaughtExceptionHandler
                                .uncaughtException(Thread.currentThread(), error)
                            return@setErrorHandler
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.d("TEST", "ApiBase ==> onError ${e.message}")
                    errorListener.onErrorResponse(e)
                }

                override fun onComplete() {
                    Log.d("TEST", "ApiBase ==> onComplete")
                    removeDisposal(tag, disp)
                }
            })
    }

    private fun removeDisposal(tag: Any, disp: Disposable?) {
        val compo = mCompositeDisposableMap!![tag]
        compo?.remove(disp!!)
    }

    private fun addDisposal(tag: Any, disp: Disposable?) {
        var compo = mCompositeDisposableMap!![tag]
        if (compo == null) {
            compo = CompositeDisposable()
            mCompositeDisposableMap!![tag] = compo
        }
        compo.add(disp!!)
    }

    fun stopAllRequest() {
        val allCompo = ArrayList(mCompositeDisposableMap!!.values)
        mCompositeDisposableMap!!.clear()
        for (compo in allCompo) {
            if (compo.size() > 0) {
                compo.clear()
            }
        }
    }

    companion object {
        private val TAG = ApiBase::class.java.simpleName
        private val DEFAULT_TIMEOUT = 35
    }
}

class BaseResponseCallback {
    interface ErrorListener {
        fun onErrorResponse(e: Throwable)
    }

    interface Listener<T> {
        fun onResponse(value: T)
    }
}