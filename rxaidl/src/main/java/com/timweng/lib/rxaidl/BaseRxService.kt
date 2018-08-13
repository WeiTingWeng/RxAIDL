package com.timweng.lib.rxaidl

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.lang.Exception

open abstract class BaseRxService : Service() {
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
        var observable: Observable<C>? = null
        try {
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
        var observer = object : DisposableObserver<C>() {

            override fun onComplete() {
                synchronized(this@BaseRxService) {
                    try {
                        callback.onCallback(requestId, BaseConstant.STATE_COMPLETE, null)
                        clientDataManager.removeRequestId(requestId)
                    } catch (e: RemoteException) {
                        // connect to client error: remove the client
                        e.printStackTrace()
                        Timber.e("onComplete error: remove client ${clientData.id}")
                        clientDataManager.removeClient(clientData.id)
                    }
                }
            }

            override fun onNext(t: C) {
                synchronized(this@BaseRxService) {
                    try {
                        callback.onCallback(requestId, BaseConstant.STATE_NEXT, gson.toJson(t))
                    } catch (e: RemoteException) {
                        // connect to client error: remove the client
                        e.printStackTrace()
                        Timber.e("onNext error: remove client ${clientData.id}")
                        clientDataManager.removeClient(clientData.id)
                    }
                }
            }

            override fun onError(e: Throwable) {
                synchronized(this@BaseRxService) {
                    try {
                        callback.onCallback(requestId, BaseConstant.STATE_ERROR, e.message)
                        clientDataManager.removeRequestId(requestId)
                    } catch (e: RemoteException) {
                        // connect to client error: remove the client
                        e.printStackTrace()
                        Timber.e("onError error: remove client ${clientData.id}")
                        clientDataManager.removeClient(clientData.id)
                    }
                }
            }
        }

        val isOk = clientDataManager.addRequestId(requestId, clientData.id, observer)
        if (isOk) {
            observable.subscribeOn(subscribeScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(observer)
            return true
        }

        return false
    }

    private fun <C> parseToObject(content: String, contentClass: Class<C>): C {
        return gson.fromJson(content, contentClass)
    }
}