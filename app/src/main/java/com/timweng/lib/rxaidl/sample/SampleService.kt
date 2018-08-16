package com.timweng.lib.rxaidl.sample

import android.support.annotation.Keep
import com.timweng.lib.rxaidl.BaseRxService
import com.timweng.lib.rxaidl.sample.model.SampleCallback
import com.timweng.lib.rxaidl.sample.model.SampleRequest
import io.reactivex.Observable

class SampleService : BaseRxService() {
    @Keep
    fun requestTestObservable(request: SampleRequest): Observable<SampleCallback> {
        val callback = SampleCallback()
        callback.requestName = request.name
        return Observable.create { e ->
            while (callback.number < request.count) {
                e.onNext(callback.copy())
                callback.number++

                try {
                    Thread.sleep(1000)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            e.onComplete()
        }
    }
}