package com.timweng.lib.rxaidl.sample

import android.content.Context
import com.timweng.lib.rxaidl.BaseRxClient
import com.timweng.lib.rxaidl.sample.model.SampleCallback
import com.timweng.lib.rxaidl.sample.model.SampleRequest
import io.reactivex.Observable

class SampleClient : BaseRxClient {

    constructor(context: Context) : super(context)

    companion object {
        const val SERVICE_PACKAGE_NAME: String = "com.timweng.lib.rxaidl.sample"
        const val SERVICE_CLASSNAME: String = "com.timweng.lib.rxaidl.sample.SampleService"
    }

    override fun getVersion(): Long {
        return 1L
    }

    override fun getPackageName(): String {
        return SERVICE_PACKAGE_NAME
    }

    override fun getClassName(): String {
        return SERVICE_CLASSNAME
    }

    fun requestSample(request: SampleRequest): Observable<SampleCallback> {
        return requestObservable(request, SampleRequest::class.java, SampleCallback::class.java,
                minServiceVersion = 1, maxServiceVersion = 10)
    }
}