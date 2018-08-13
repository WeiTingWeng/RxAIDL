package com.timweng.lib.rxaidl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.subjects.Subject
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

    private val observableRequestSet: MutableSet<ObservableRequest> = mutableSetOf()
    private val observableEmitterMap: MutableMap<Long, ObservableEmitter<*>> = mutableMapOf()
    private val callbackClassMap: MutableMap<Long, Class<*>> = mutableMapOf()      // RequestId to Callback Class

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCounting = false

    protected abstract fun getPackageName(): String
    protected abstract fun getClassName(): String

    @Synchronized
    fun disconnect() {
        Timber.d("disconnect")
        if (isConnecting) {
            isRequestDisconnect = true
            return
        }
        if (!isConnected) {
            return
        }
        context.unbindService(serviceConnection)
        onDisconnect()
    }

    protected open fun getAutoDisconnectTime(): Long {
        return 0L
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(this@BaseRxClient) {
                Timber.d("onServiceConnected.name = $name")
                isConnecting = false

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
                    isConnected = true
                    // Handle the the requests which sent before connected to service
                    for (request in observableRequestSet) {
                        var requestId: Long
                        try {
                            requestId = iBaseInterface.requestObservable(uuidString, request.requestContent,
                                    request.requestClass.name, request.callbackClass.name, request.methodName)
                        } catch (e: RemoteException) {
                            request.emitter.onError(e)
                            return
                        }
                        if (requestId < 0) {
                            val e = RuntimeException("onServiceConnected: requestId < 0 failed")
                            request.emitter.onError(e)
                            return
                        }
                        observableEmitterMap[requestId] = request.emitter
                        callbackClassMap[requestId] = request.callbackClass
                        checkAutoDisconnect()
                    }
                    observableRequestSet.clear()
                }
                // Request disconnect when is connecting
                if (isRequestDisconnect) {
                    isRequestDisconnect = false
                    disconnect()
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

    private fun onDisconnect() {
        Timber.d("onDisconnect.observableRequestSet.size = ${observableRequestSet.size}")
        Timber.d("onDisconnect.observableEmitterMap.size = ${observableEmitterMap.size}")

        uuidString = UUID.randomUUID().toString()
        isConnecting = false
        isConnected = false
        isCounting = false
        for (request in observableRequestSet) {
            val e = RuntimeException("Service disconnected.")
            request.emitter.onError(e)
        }
        for (emitter in observableEmitterMap.values) {
            val e = RuntimeException("Service disconnected.")
            emitter.onError(e)
        }
        observableRequestSet.clear()
        callbackClassMap.clear()
        observableEmitterMap.clear()
        mainHandler.removeCallbacks(mAutoDisconnectRunnable)
    }

    private val mIBaseCallback = object : IBaseCallback.Stub() {
        override fun onCallback(requestId: Long, state: Int, callbackContent: String?) {
            synchronized(this@BaseRxClient) {
                Timber.d("onCallback = $requestId, $state, $callbackContent")
                val callbackClass = callbackClassMap[requestId]

                if (callbackClass != null) {
                    emitCallback(requestId, state, callbackContent, callbackClass)
                } else {
                    Timber.e("onCallback: can not find callbackClass")
                }
            }
        }
    }

    private fun <C> emitCallback(requestId: Long, state: Int, callbackContent: String?, callbackClass: Class<C>) {
        val emitter = observableEmitterMap[requestId]
        if (emitter == null) {
            Timber.e("emitCallback: can not find subject")
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
                emitter2.onError(RuntimeException(callbackContent))
                observableEmitterMap.remove(requestId)
                callbackClassMap.remove(requestId)
                checkAutoDisconnect()
            }
            BaseConstant.STATE_COMPLETE -> {
                emitter2.onComplete()
                observableEmitterMap.remove(requestId)
                callbackClassMap.remove(requestId)
                checkAutoDisconnect()
            }
        }
    }

    protected fun <R, C> requestObservable(request: R,
                                           requestClass: Class<R>, callbackClass: Class<C>,
                                           methodName: String = BaseConstant.NULL_METHOD): Observable<C> {
        return Observable.create { emitter ->
            synchronized(this@BaseRxClient) {
                if (isConnected && !isConnecting) {
                    // Is connected, send the request directly
                    var content = gson.toJson(request)
                    var requestId: Long
                    try {
                        requestId = iBaseInterface.requestObservable(uuidString, content,
                                requestClass.name, callbackClass.name, methodName)
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                        emitter.onError(e)
                        return@create
                    }
                    if (requestId < 0) {
                        emitter.onError(RuntimeException("requestObservable failed: requestId < 0"))
                        return@create
                    }
                    observableEmitterMap[requestId] = emitter
                    callbackClassMap[requestId] = callbackClass
                    checkAutoDisconnect()
                    Timber.d("$requestId , ${observableEmitterMap.containsKey(requestId)}")
                } else {
                    // Is not connected, add request to pendingRequestSet
                    var content = gson.toJson(request)

                    observableRequestSet.add(ObservableRequest(emitter, content, requestClass, callbackClass, methodName))
                    if (!isConnecting) {
                        Timber.d("connect")
                        isConnecting = true
                        var intent = Intent()
                        intent.setClassName(getPackageName(), getClassName())
                        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                    }
                }
            }
        }
    }

    private fun checkAutoDisconnect() {
        if (getAutoDisconnectTime() < 0) {
            return
        }
        if (observableEmitterMap.isEmpty()) {
            if (!isCounting) {
                if (getAutoDisconnectTime() == 0L) {
                    disconnect()
                } else {
                    isCounting = true
                    mainHandler.postDelayed(mAutoDisconnectRunnable, getAutoDisconnectTime())
                }
            }
        } else {
            if (isCounting) {
                mainHandler.removeCallbacks(mAutoDisconnectRunnable)
                isCounting = false
            }
        }
    }

    private val mAutoDisconnectRunnable = Runnable {
        disconnect()
    }
}