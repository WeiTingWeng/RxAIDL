package com.timweng.lib.rxaidl.sample

import android.support.annotation.Keep
import com.timweng.lib.rxaidl.BaseRxService
import com.timweng.lib.rxaidl.annotation.RequestRequirement
import com.timweng.lib.rxaidl.sample.model.SampleCallback
import com.timweng.lib.rxaidl.sample.model.SampleRequest
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class SampleService : BaseRxService() {

    override fun getVersion(): Long {
        return 1L
    }

    @Keep
    @RequestRequirement(minClientVersion = 0, maxClientVersion = 10)
    fun requestTestObservable(request: SampleRequest): Observable<SampleCallback> {
        val callback = SampleCallback()
        callback.requestName = request.name

        return Observable.create<SampleCallback>(ObservableOnSubscribe { e ->
            while (callback.number < request.count) {
                if (!e.isDisposed) {
                    Timber.d("onNext: $callback")
                    e.onNext(callback.copy())
                } else {
                    break
                }
                callback.number++

                try {
                    Thread.sleep(1000)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            e.onComplete()
        }).subscribeOn(Schedulers.newThread())
    }
}