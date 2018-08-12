package com.timweng.lib.rxaidl

import io.reactivex.subjects.Subject

internal class PendingRequest(subject: Subject<*>, requestContent: String,
                              requestClass: Class<*>, callbackClass: Class<*>, methodName: String?) {
    val subject: Subject<*> = subject
    val requestContent: String = requestContent
    val requestClass: Class<*> = requestClass
    val callbackClass: Class<*> = callbackClass
    val methodName: String? = methodName
}