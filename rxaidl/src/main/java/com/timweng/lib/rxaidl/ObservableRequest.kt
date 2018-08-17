package com.timweng.lib.rxaidl

import io.reactivex.ObservableEmitter

internal data class ObservableRequest(val emitter: ObservableEmitter<*>, val requestContent: String,
                                      val requestClass: Class<*>, val callbackClass: Class<*>,
                                      val methodName: String?, val minVersion: Long, val maxVersion: Long)