package com.timweng.lib.rxaidl

import io.reactivex.disposables.Disposable
import timber.log.Timber

internal class ClientDataManager {
    private val clientId2ClientMap: MutableMap<String, ClientData> = mutableMapOf()
    private val requestId2ClientMap: MutableMap<Long, ClientData> = mutableMapOf()

    fun addClient(id: String, callback: IBaseCallback): Boolean {
        if (clientId2ClientMap.containsKey(id)) {
            return false
        }
        var clientData = ClientData(id, callback)
        clientId2ClientMap[id] = clientData
        return true
    }

    fun removeClient(id: String): Boolean {
        if (!clientId2ClientMap.containsKey(id)) {
            return false
        }
        val clientData = clientId2ClientMap.remove(id)
        if (clientData != null && !clientData.disposableMap.isEmpty()) {
            for (requestId in clientData.disposableMap.keys) {
                requestId2ClientMap.remove(requestId)
                val disposable = clientData.disposableMap.remove(requestId)
                if (disposable != null && !disposable.isDisposed) {
                    disposable.dispose()
                }
            }
            clientData.disposableMap.clear()
        }
        return true
    }

    fun clearClient() {
        for (clientData: ClientData in clientId2ClientMap.values) {
            for (disObserver in clientData.disposableMap.values) {
                if (!disObserver.isDisposed) {
                    disObserver.dispose()
                }
            }
        }
        clientId2ClientMap.clear()
        requestId2ClientMap.clear()
    }

    fun getClient(id: String): ClientData? {
        return clientId2ClientMap[id]
    }

    fun addRequestId(requestId: Long, clientId: String, disposable: Disposable): Boolean {
        Timber.d("addRequestId: $requestId to $clientId")

        if (requestId2ClientMap.containsKey(requestId)) {
            Timber.e("addRequestId error: requestId already exist")
            return false
        }
        val clientData = clientId2ClientMap[clientId]
        if (clientData != null) {
            requestId2ClientMap[requestId] = clientData
            clientData.disposableMap[requestId] = disposable
        } else {
            Timber.e("addRequestId error: clientId not exist")
            return false
        }
        return true
    }

    fun removeRequestId(requestId: Long): Boolean {
        Timber.d("removeRequestId: $requestId")
        if (!requestId2ClientMap.containsKey(requestId)) {
            Timber.e("removeRequestId error: requestId not exist")
            return false
        }

        var clientData = requestId2ClientMap.remove(requestId)
        if (clientData != null) {
            val disposable = clientData.disposableMap.remove(requestId)
            if (disposable != null && !disposable.isDisposed) {
                disposable.dispose()
            }
        } else {
            Timber.e("removeRequestId error: can not find clientData")
            return false
        }
        return true
    }

    fun getClientByRequestId(requestId: Long): ClientData? {
        Timber.d("getClientByRequestId: $requestId")
        return requestId2ClientMap[requestId]
    }
}