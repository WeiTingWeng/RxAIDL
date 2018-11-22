# RxAIDL

## Synopsis
An easy method for providing the reactive interface between client and service

## Concept
<img src="/art/concept.png" width="800" height="400">

## How to use IPC

#### 1. Create Client which extends BaseRxClient
```kotlin
class SampleClient : BaseRxClient {
    constructor(context: Context) : super(context)
    companion object {
        const val SERVICE_PACKAGE_NAME: String = "com.timweng.lib.rxaidl.sample"
        const val SERVICE_CLASSNAME: String = "com.timweng.lib.rxaidl.sample.SampleService"
    }
    override fun getVersion(): Long {
        return 1L
    }
    override fun getPackageName(): String {
        return SERVICE_PACKAGE_NAME
    }
    override fun getClassName(): String {
        return SERVICE_CLASSNAME
    }
    fun requestSample(request: SampleRequest): Observable<SampleCallback> {
        return requestObservable(request, SampleRequest::class.java, SampleCallback::class.java,
                minServiceVersion = 1, maxServiceVersion = 10)
    }
}
```
#### 2. Create Service which extends BaseRxService
```kotlin
class SampleService : BaseRxService() {
    override fun getVersion(): Long {
        return 1L
    }
    @Keep
    @RequestRequirement(minClientVersion = 0, maxClientVersion = 10)
    fun requestTestObservable(request: SampleRequest): Observable<SampleCallback> {
        val callback = SampleCallback()
        callback.requestName = request.name
        return Observable.create<SampleCallback> { e ->
            while (callback.number < request.count) {
                if (!e.isDisposed) {
                    Timber.d("onNext: $callback")
                    e.onNext(callback.copy())
                } else {
                    break
                }
                callback.number++
            }
            e.onComplete()
        }.subscribeOn(Schedulers.newThread())
    }
}
```
#### 3. Define Request class
```kotlin
data class SampleRequest(var name: String = "SampleRequest",
                         var count: Int = 3)
```
#### 4. Define Callback class
```kotlin
data class SampleCallback(var requestName: String = "TEMP",
                          var number: Int = 0)
```
#### 5. Add Service in Manifest and exported it
```xml
<service
    android:name=".SampleService"
    android:exported="true" />
```
#### 6. Request service by client
```kotlin
    val client = SampleClient(this)
    val request = SampleRequest("Observer0", 10)
    val observable = client.requestSample(request)

    observable.observeOn(AndroidSchedulers.mainThread()).subscribe(
            { n -> updateLog("onNext:\n$n") },
            { e -> updateLog("onError:\n$e") },
            { updateLog("onComplete") })
```