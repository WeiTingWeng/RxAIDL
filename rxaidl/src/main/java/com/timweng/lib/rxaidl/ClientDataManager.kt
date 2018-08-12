package com.timweng.lib.rxaidl

import io.reactivex.observers.DisposableObserver
import timber.log.Timber

internal class ClientDataManager {
    private val mClientMap: MutableMap<String, ClientData> = mutableMapOf()
    private val mRequestId2ClientMap: MutableMap<Long, ClientData> = mutableMapOf()

    fun addClient(id: String, callback: IBaseCallback): Boolean {
        if (mClientMap.containsKey(id)) {
            return false
        }
        var clientData = ClientData(id, callback)
        mClientMap.put(id, clientData)
        return true
    }

    fun removeClient(id: String): Boolean {
        if (!mClientMap.containsKey(id)) {
            return false
        }
        val clientData = mClientMap.remove(id)
        if (clientData != null && !clientData.disposableMap.isEmpty()) {
            for (requestId in clientData.disposableMap.keys) {
                mRequestId2ClientMap.remove(requestId)
                val disObserver = clientData.disposableMap.remove(requestId)
                if (disObserver != null && !disObserver.isDisposed) {
                    disObserver.dispose()
                }
            }
            clientData.disposableMap.clear()
        }
        return true
    }

    fun clearClient() {
        for (clientData: ClientData in mClientMap.values) {
            for (disObserver in clientData.disposableMap.values) {
                if (disObserver != null && !disObserver.isDisposed) {
                    disObserver.dispose()
                }
            }
        }
        mClientMap.clear()
        mRequestId2ClientMap.clear()
    }

    fun getClient(id: String): ClientData? {
        return mClientMap.get(id)
    }

    fun addRequestId(requestId: Long, clientId: String, disObserver: DisposableObserver<*>): Boolean {
        Timber.d("addRequestId: $requestId to $clientId")

        if (mRequestId2ClientMap.containsKey(requestId)) {
            Timber.e("addRequestId error: requestId already exist")
            return false
        }
        val clientData = mClientMap.get(clientId)
        if (clientData != null) {
            mRequestId2ClientMap.put(requestId, clientData)
            clientData.disposableMap.put(requestId, disObserver)
        } else {
            Timber.e("addRequestId error: clientId not exist")
            return false
        }
        return true
    }

    fun removeRequestId(requestId: Long): Boolean {
        Timber.d("removeRequestId: $requestId")
        if (!mRequestId2ClientMap.containsKey(requestId)) {
            Timber.e("removeRequestId error: requestId not exist")
            return false
        }

        var clientData = mRequestId2ClientMap.remove(requestId)
        if (clientData != null) {
            val disObserver = clientData.disposableMap.remove(requestId)
            if (disObserver != null && !disObserver.isDisposed) {
                disObserver.dispose()
            }
        } else {
            Timber.e("removeRequestId error: can not find clientData")
            return false
        }
        return true
    }

    fun getClientByRequestId(requestId: Long): ClientData? {
        Timber.d("getClientByRequestId: $requestId")
        return mRequestId2ClientMap.get(requestId)
    }
}