package com.timweng.lib.rxaidl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import timber.log.Timber
import java.util.*

abstract class BaseRxClient(context: Context) {
    private val context: Context = context
    private var gson = Gson()

    private var uuidString: String = UUID.randomUUID().toString()
    private lateinit var iBaseInterface: IBaseInterface
    private var isConnecting = false
    private var isConnected = false
    private var isRequestDisconnect = false

    private val pendingObservableRequests: MutableSet<ObservableRequest> = mutableSetOf()
    private val rid2ObservableEmitterMap: MutableMap<Long, ObservableEmitter<*>> = mutableMapOf()
    private val observableEmitter2RidMap: MutableMap<ObservableEmitter<*>, Long> = mutableMapOf()
    private val rid2callbackClassMap: MutableMap<Long, Class<*>> = mutableMapOf()      // RequestId to Callback Class

    protected abstract fun getPackageName(): String
    protected abstract fun getClassName(): String

    fun disposeAll() {
        disconnect()
    }

    @Synchronized
    private fun connect() {
        isRequestDisconnect = false
        if (!isConnected && !isConnecting) {
            Timber.d("connect")
            isConnecting = true
            var intent = Intent()
            intent.setClassName(getPackageName(), getClassName())
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            Timber.d("connect fail: isConnected = $isConnected, isConnecting = $isConnecting")
        }
    }

    @Synchronized
    private fun disconnect() {
        if (isConnecting) {
            Timber.d("disconnect pending")
            isRequestDisconnect = true
            return
        }
        if (!isConnected) {
            Timber.d("disconnect fail: service is not connected")
            return
        }
        Timber.d("disconnect")
        context.unbindService(serviceConnection)
        onDisconnect()
    }

    private fun onDisconnect() {
        Timber.d("onDisconnect.pendingObservableRequests.size = ${pendingObservableRequests.size}")
        Timber.d("onDisconnect.rid2ObservableEmitterMap.size = ${rid2ObservableEmitterMap.size}")

        uuidString = UUID.randomUUID().toString()
        isConnecting = false
        isConnected = false
        for (request in pendingObservableRequests) {
            val e = RuntimeException("Service disconnected.")
            if (!request.emitter.isDisposed) {
                request.emitter.onError(e)
            }
        }
        for (entry in rid2ObservableEmitterMap.entries) {
            val e = RuntimeException("Service disconnected.")
            if (!entry.value.isDisposed) {
                observableEmitter2RidMap.remove(entry.value)
                entry.value.onError(e)
            }
        }

        pendingObservableRequests.clear()
        rid2callbackClassMap.clear()
        rid2ObservableEmitterMap.clear()
        observableEmitter2RidMap.clear()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(this@BaseRxClient) {
                Timber.d("onServiceConnected.name = $name")
                isConnecting = false
                isConnected = true

                // Request disconnect when is connecting
                if (isRequestDisconnect) {
                    isRequestDisconnect = false
                    disconnect()
                    return
                }

                iBaseInterface = IBaseInterface.Stub.asInterface(service)

                var isOk = false
                try {
                    isOk = iBaseInterface.register(uuidString, mIBaseCallback)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
                Timber.d("onServiceConnected.isOk = $isOk")
                if (!isOk) {
                    disconnect()
                } else {
                    // Handle the the requests which sent before connected to service
                    for (request in pendingObservableRequests) {
                        requestObservableFromAidl(request.emitter, request.requestContent,
                                request.requestClass, request.callbackClass, request.methodName)
                    }
                    pendingObservableRequests.clear()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(this@BaseRxClient) {
                Timber.d("onServiceDisconnected.name = $name")
                onDisconnect()
            }
        }
    }

    private val mIBaseCallback = object : IBaseCallback.Stub() {
        override fun onCallback(requestId: Long, state: Int, callbackContent: String?) {
            synchronized(this@BaseRxClient) {
                Timber.d("onCallback = $requestId, $state, $callbackContent")
                val callbackClass = rid2callbackClassMap[requestId]

                if (callbackClass != null) {
                    emitCallback(requestId, state, callbackContent, callbackClass)
                } else {
                    Timber.e("onCallback: can not find callbackClass")
                }
            }
        }
    }

    private fun <C> emitCallback(requestId: Long, state: Int, callbackContent: String?, callbackClass: Class<C>) {
        val emitter = rid2ObservableEmitterMap[requestId]
        if (emitter == null) {
            Timber.e("emitCallback: can not find emitter[$requestId]")
            return
        }
        if (emitter.isDisposed) {
            Timber.e("emitCallback: emitter[$requestId] is Disposed")
            return
        }
        @Suppress("UNCHECKED_CAST")
        val emitter2: ObservableEmitter<C> = emitter as ObservableEmitter<C>
        when (state) {
            BaseConstant.STATE_NEXT -> {
                val callback: C = gson.fromJson(callbackContent, callbackClass)
                emitter2.onNext(callback)
            }
            BaseConstant.STATE_ERROR -> {
                val e = rid2ObservableEmitterMap.remove(requestId)
                observableEmitter2RidMap.remove(e)
                rid2callbackClassMap.remove(requestId)
                emitter2.onError(RuntimeException(callbackContent))
                checkAutoDisconnect()
            }
            BaseConstant.STATE_COMPLETE -> {
                val e = rid2ObservableEmitterMap.remove(requestId)
                observableEmitter2RidMap.remove(e)
                rid2callbackClassMap.remove(requestId)
                emitter2.onComplete()
                checkAutoDisconnect()
            }
        }
    }

    protected fun <R, C> requestObservable(request: R,
                                           requestClass: Class<R>, callbackClass: Class<C>,
                                           methodName: String = BaseConstant.NULL_METHOD): Observable<C> {
        val oos = ObservableOnSubscribe<C> { emitter ->
            synchronized(this@BaseRxClient) {
                if (isConnected && !isConnecting) {
                    // Is connected, send the request directly
                    var content = gson.toJson(request)

                    requestObservableFromAidl(emitter, content, requestClass, callbackClass, methodName)
                } else {
                    // Is not connected, add request to pendingRequestSet
                    var content = gson.toJson(request)

                    pendingObservableRequests.add(ObservableRequest(emitter, content, requestClass, callbackClass, methodName))
                    connect()
                }
                return@ObservableOnSubscribe
            }
        }

        return Observable.create(oos)
                .doFinally {
                    synchronized(this@BaseRxClient) {
                        val iterate = observableEmitter2RidMap.iterator()
                        while (iterate.hasNext()) {
                            val entity = iterate.next()
                            val emitter = entity.key
                            val requestId = entity.value
                            if (emitter.isDisposed) {
                                Timber.d("doFinally: emitter[$requestId] is disposed")
                                rid2ObservableEmitterMap.remove(requestId)
                                rid2callbackClassMap.remove(requestId)
                                iterate.remove()
                                try {
                                    iBaseInterface.dispose(uuidString, requestId)
                                } catch (e: RemoteException) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        val iterate2 = pendingObservableRequests.iterator()
                        while (iterate2.hasNext()) {
                            val request = iterate2.next()
                            if (request.emitter.isDisposed) {
                                Timber.d("doFinally: pending emitter is disposed")
                                iterate2.remove()
                            }
                        }
                        checkAutoDisconnect()
                    }
                }
    }

    private fun requestObservableFromAidl(emitter: ObservableEmitter<*>, requestContent: String,
                                          requestClass: Class<*>, callbackClass: Class<*>, methodName: String?): Long {
        var requestId: Long
        try {
            requestId = iBaseInterface.requestObservable(uuidString, requestContent,
                    requestClass.name, callbackClass.name, methodName)
        } catch (e: RemoteException) {
            e.printStackTrace()
            emitter.onError(e)
            return -1
        }
        if (requestId < 0) {
            val e = RuntimeException("onServiceConnected: requestId < 0 failed")
            emitter.onError(e)
            return -1
        }
        rid2ObservableEmitterMap[requestId] = emitter
        observableEmitter2RidMap[emitter] = requestId
        rid2callbackClassMap[requestId] = callbackClass
        checkAutoDisconnect()

        Timber.d("requestObservableFromAidl success: request = $requestId , ${rid2ObservableEmitterMap.containsKey(requestId)}")
        return requestId
    }

    private fun checkAutoDisconnect() {
        if (rid2ObservableEmitterMap.isEmpty() && pendingObservableRequests.isEmpty()) {
            disconnect()
        }
    }
}