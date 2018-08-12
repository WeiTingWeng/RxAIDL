package com.timweng.lib.rxaidl

import io.reactivex.observers.DisposableObserver

internal class ClientData(id: String, callback: IBaseCallback) {
    val id: String = id
    val callback: IBaseCallback = callback
    val disposableMap: MutableMap<Long, DisposableObserver<*>> = mutableMapOf()
}