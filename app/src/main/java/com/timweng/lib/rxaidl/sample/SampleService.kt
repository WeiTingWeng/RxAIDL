package com.timweng.lib.rxaidl.sample

import com.timweng.lib.rxaidl.BaseRxService
import com.timweng.lib.rxaidl.sample.model.SampleCallback
import com.timweng.lib.rxaidl.sample.model.SampleRequest
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe

class SampleService : BaseRxService() {
    fun requestTestObservable(request: SampleRequest): Observable<SampleCallback> {
        val callback = SampleCallback()
        callback.requestName = request.name
        val observable = Observable.create(ObservableOnSubscribe<SampleCallback> { e ->
            while (callback.number < request.count) {
                e.onNext(callback.copy())
                callback.number++
            }
            e.onComplete()
        })
        return observable
    }
}