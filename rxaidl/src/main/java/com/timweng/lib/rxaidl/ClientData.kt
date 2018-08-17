package com.timweng.lib.rxaidl

import io.reactivex.disposables.Disposable

internal class ClientData(id: String, version: Long, callback: IBaseCallback) {
    val id: String = id
    val version: Long = version
    val callback: IBaseCallback = callback
    val disposableMap: MutableMap<Long, Disposable> = mutableMapOf()
}