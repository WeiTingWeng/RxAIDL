package com.timweng.lib.rxaidl

import io.reactivex.disposables.Disposable

internal class ClientData(id: String, callback: IBaseCallback) {
    val id: String = id
    val callback: IBaseCallback = callback
    val disposableMap: MutableMap<Long, Disposable> = mutableMapOf()
}