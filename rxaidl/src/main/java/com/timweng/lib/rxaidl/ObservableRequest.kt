package com.timweng.lib.rxaidl

import io.reactivex.ObservableEmitter

internal class ObservableRequest(emitter: ObservableEmitter<*>, requestContent: String,
                                 requestClass: Class<*>, callbackClass: Class<*>, methodName: String?) {
    val emitter: ObservableEmitter<*> = emitter
    val requestContent: String = requestContent
    val requestClass: Class<*> = requestClass
    val callbackClass: Class<*> = callbackClass
    val methodName: String? = methodName
}