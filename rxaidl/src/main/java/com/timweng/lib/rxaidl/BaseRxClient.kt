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
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import timber.log.Timber
import java.util.*

open abstract class BaseRxClient(context: Context) {
    private val context: Context = context
    private var gson = Gson()

    private var uuidString: String = UUID.randomUUID().toString()
    private lateinit var iBaseInterface: IBaseInterface
    private var isConnecting = false
    private var isConnected = false
    private var isRequestDisconnect = false

    private val subjectMap: MutableMap<Long, Subject<*>> = mutableMapOf()  // RequestId to Subject
    private val classMap: MutableMap<Long, Class<*>> = mutableMapOf()      // RequestId to Callback Class
    private val pendingRequestSet: MutableSet<PendingRequest> = mutableSetOf()

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
        context.unbindService(mServiceConnection)
        onDisconnect()
    }

    protected open fun getAutoDisconnectTime(): Long {
        return 0L
    }

    private val mServiceConnection = object : ServiceConnection {
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
                    for (request in pendingRequestSet) {
                        var requestId: Long
                        try {
                            requestId = iBaseInterface.requestObservable(uuidString, request.requestContent,
                                    request.requestClass.name, request.callbackClass.name, request.methodName)
                        } catch (e: RemoteException) {
                            request.subject.onError(e)
                            return
                        }
                        if (requestId < 0) {
                            val e = RuntimeException("onServiceConnected: requestId < 0 failed")
                            request.subject.onError(e)
                            return
                        }
                        subjectMap[requestId] = request.subject
                        classMap[requestId] = request.callbackClass
                        checkAutoDisconnect()
                    }
                    pendingRequestSet.clear()
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
        Timber.d("onDisconnect.pendingRequestSet.size = ${pendingRequestSet.size}")
        Timber.d("onDisconnect.subjectMap.size = ${subjectMap.size}")

        uuidString = UUID.randomUUID().toString()
        isConnecting = false
        isConnected = false
        for (request in pendingRequestSet) {
            val e = RuntimeException("Service disconnected.")
            request.subject.onError(e)
        }
        for (subject in subjectMap.values) {
            val e = RuntimeException("Service disconnected.")
            subject.onError(e)
        }
        subjectMap.clear()
        classMap.clear()
        pendingRequestSet.clear()
        mainHandler.removeCallbacks(mAutoDisconnectRunnable)
    }

    private val mIBaseCallback = object : IBaseCallback.Stub() {
        override fun onCallback(requestId: Long, state: Int, callbackContent: String?) {
            synchronized(this@BaseRxClient) {
                Timber.d("onCallback = $requestId, $state, $callbackContent")
                val callbackClass = classMap[requestId]

                if (callbackClass != null) {
                    emitCallback(requestId, state, callbackContent, callbackClass)
                } else {
                    Timber.e("onCallback: can not find callbackClass")
                }
            }
        }
    }

    private fun <C> emitCallback(requestId: Long, state: Int, callbackContent: String?, callbackClass: Class<C>) {
        val subject = subjectMap[requestId]
        if (subject == null) {
            Timber.e("emitCallback: can not find subject")
            return
        }
        val subject2: Subject<C> = subject as Subject<C>
        when (state) {
            BaseConstant.STATE_NEXT -> {
                val callback: C = gson.fromJson(callbackContent, callbackClass)
                subject2.onNext(callback)
            }
            BaseConstant.STATE_ERROR -> {
                subject2.onError(RuntimeException(callbackContent))
                subjectMap.remove(requestId)
                classMap.remove(requestId)
                checkAutoDisconnect()
            }
            BaseConstant.STATE_COMPLETE -> {
                subject2.onComplete()
                subjectMap.remove(requestId)
                classMap.remove(requestId)
                checkAutoDisconnect()
            }
        }
    }

    @Synchronized
    protected fun <R, C> requestObservable(request: R,
                                           requestClass: Class<R>, callbackClass: Class<C>,
                                           methodName: String = BaseConstant.NULL_METHOD): Observable<C> {
        if (isConnected && !isConnecting) {
            // Is connected, send the request directly
            var content = gson.toJson(request)
            var requestId = -1L
            try {
                requestId = iBaseInterface.requestObservable(uuidString, content,
                        requestClass.name, callbackClass.name, methodName)
            } catch (e: RemoteException) {
                e.printStackTrace()
                return Observable.error(e)
            }
            if (requestId < 0) {
                return Observable.error(RuntimeException("requestObservable failed: requestId < 0"))
            }
            var subject: Subject<C> = PublishSubject.create<C>()
            Timber.d("$content, $requestId, $subject")
            subjectMap.put(requestId, subject)
            classMap.put(requestId, callbackClass)
            checkAutoDisconnect()
            Timber.d("$requestId , ${subjectMap.containsKey(requestId)}")

            return subject
        } else {
            // Is not connected, add request to pendingRequestSet
            var content = gson.toJson(request)
            var subject: Subject<C> = PublishSubject.create<C>()

            pendingRequestSet.add(PendingRequest(subject, content, requestClass, callbackClass, methodName))

            if (!isConnecting) {
                Timber.d("connect")
                isConnecting = true
                var intent = Intent()
                intent.setClassName(getPackageName(), getClassName())
                context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
            }

            return subject
        }
    }

    private fun checkAutoDisconnect() {
        if (getAutoDisconnectTime() < 0) {
            return
        }
        if (subjectMap.isEmpty()) {
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