package com.timweng.lib.rxaidl

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.lang.Exception


abstract class BaseRxService : Service() {
    private companion object {
        var requestCount = 0L
    }

    private var clientDataManager: ClientDataManager = ClientDataManager()
    private val tempRid2ClientMap: MutableMap<Long, ClientData> = mutableMapOf()
    private val gson = Gson()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        Timber.d("onCreate")
        super.onCreate()
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        clientDataManager.clearClient()
        super.onDestroy()
    }

    private val binder = object : IBaseInterface.Stub() {
        override fun register(clientId: String, callback: IBaseCallback): Boolean {
            synchronized(this@BaseRxService) {
                Timber.d("register: $clientId, $callback")
                return clientDataManager.addClient(clientId, callback)
            }
        }

        override fun requestObservable(clientId: String, requestContent: String,
                                       requestClass: String, callbackClass: String, methodName: String): Long {
            synchronized(this@BaseRxService) {
                Timber.d("request: $clientId, $requestContent")
                Timber.d("request: $requestClass, $callbackClass, $methodName")

                var clientData = clientDataManager.getClient(clientId)
                if (clientData != null) {
                    val cbClass: Class<*>? = ReflectionUtil.getClassFromName(callbackClass)
                    if (cbClass == null) {
                        Timber.e("requestObservable failed: Can not find callback Class")
                        return -1
                    }
                    requestCount++
                    tempRid2ClientMap[requestCount] = clientData
                    val isOk = onRequestObservable(requestCount, requestContent, requestClass, cbClass, methodName)
                    tempRid2ClientMap.remove(requestCount)
                    if (!isOk) {
                        Timber.e("requestObservable failed: onRequestObservable error")
                        return -1
                    }
                    return requestCount
                } else {
                    Timber.e("requestObservable failed: Can not find clientData")
                }
                return -1
            }
        }

        override fun dispose(clientId: String?, requestId: Long): Boolean {
            Timber.d("dispose1 CID = $clientId, RID = $requestId")
            synchronized(this@BaseRxService) {
                Timber.d("dispose: CID = $clientId, RID = $requestId")
                return clientDataManager.removeRequestId(requestId)
            }
        }

        override fun unregister(clientId: String): Boolean {
            synchronized(this@BaseRxService) {
                Timber.d("unregister: $clientId")
                return clientDataManager.removeClient(clientId)
            }
        }
    }

    private fun <C> onRequestObservable(requestId: Long, requestContent: String,
                                        requestClassString: String, callbackClass: Class<C>, methodName: String): Boolean {
        val requestClass: Class<*>? = ReflectionUtil.getClassFromName(requestClassString)
        if (requestClass == null) {
            return false
        }
        var targetMethod = ReflectionUtil.findObservableMethod(javaClass, requestClass, callbackClass, methodName)
        if (targetMethod == null) {
            return false
        }

        var request = parseToObject(requestContent, requestClass)
        var observable: Observable<C>?
        try {
            @Suppress("UNCHECKED_CAST")
            observable = targetMethod.invoke(this, request) as Observable<C>
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return connectObservable(requestId, observable)
    }

    private fun <C> connectObservable(requestId: Long, observable: Observable<C>): Boolean {
        return connectObservable(requestId, observable, Schedulers.newThread())
    }

    private fun <C> connectObservable(requestId: Long, observable: Observable<C>, subscribeScheduler: Scheduler): Boolean {
        val clientData: ClientData? = tempRid2ClientMap[requestId]
        val callback = clientData?.callback
        if (clientData == null || callback == null) {
            Timber.e("connectObservable failed: clientData = $clientData, callback = $clientData")
            return false
        }

        val observer = object : Observer<C> {
            override fun onSubscribe(disposable: Disposable) {
                synchronized(this@BaseRxService) {
                    clientDataManager.addRequestId(requestId, clientData.id, disposable)
                }
            }

            override fun onComplete() {
                var isOk = true
                try {
                    callback.onCallback(requestId, BaseConstant.STATE_COMPLETE, null)
                } catch (e: RemoteException) {
                    // connect to client error: remove the client
                    e.printStackTrace()
                    isOk = false
                }
                synchronized(this@BaseRxService) {
                    clientDataManager.removeRequestId(requestId)
                    if (!isOk) {
                        clientDataManager.removeClient(clientData.id)
                    }
                }
            }

            override fun onNext(t: C) {
                var isOk = true
                try {
                    callback.onCallback(requestId, BaseConstant.STATE_NEXT, gson.toJson(t))
                } catch (e: RemoteException) {
                    // connect to client error: remove the client
                    e.printStackTrace()
                    isOk = false
                }

                if (!isOk) {
                    synchronized(this@BaseRxService) {
                        clientDataManager.removeClient(clientData.id)
                    }
                }
            }

            override fun onError(e: Throwable) {
                var isOk = true
                try {
                    callback.onCallback(requestId, BaseConstant.STATE_ERROR, e.message)
                } catch (e: RemoteException) {
                    // connect to client error: remove the client
                    e.printStackTrace()
                    Timber.e("onError error: remove client ${clientData.id}: ${e.message}")
                    isOk = false
                }
                synchronized(this@BaseRxService) {
                    clientDataManager.removeRequestId(requestId)
                    if (!isOk) {
                        clientDataManager.removeClient(clientData.id)
                    }
                }
            }
        }

        observable.subscribeOn(subscribeScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(observer)
        return true
    }

    private fun <C> parseToObject(content: String, contentClass: Class<C>): C {
        return gson.fromJson(content, contentClass)
    }
}