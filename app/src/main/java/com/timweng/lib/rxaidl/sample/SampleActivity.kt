package com.timweng.lib.rxaidl.sample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.timweng.lib.rxaidl.sample.model.SampleRequest
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_sample.*
import timber.log.Timber

class SampleActivity : AppCompatActivity() {

    private val client = SampleClient(this)
    private lateinit var disposable: Disposable
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        val request = SampleRequest("Observer0", 10)
        val observable = client.requestSample(request)

        requestButton.setOnClickListener {
            disposable = observable.observeOn(AndroidSchedulers.mainThread()).subscribe(
                    { n -> updateLog("onNext:\n$n") },
                    { e -> updateLog("onError:\n$e") },
                    { updateLog("onComplete") })

            compositeDisposable.add(disposable)
        }

        disposeButton.setOnClickListener { disposable.dispose() }

        disconnectButton.setOnClickListener {
            client.disposeAll()
        }
    }

    override fun onDestroy() {
        compositeDisposable.dispose()
        super.onDestroy()
    }

    private fun updateLog(log: String) {
        Timber.d(log)
        logTextView.text = log
    }
}
