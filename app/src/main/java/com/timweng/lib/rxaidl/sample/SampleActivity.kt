package com.timweng.lib.rxaidl.sample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.timweng.lib.rxaidl.sample.model.SampleRequest
import kotlinx.android.synthetic.main.activity_sample.*
import timber.log.Timber

class SampleActivity : AppCompatActivity() {

    private val mClient = SampleClient(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        val request = SampleRequest("Observer0", 3)

        requestButton.setOnClickListener {
            mClient.requestSample(request).subscribe(
                    { n -> Timber.d("$n") },
                    { e -> Timber.d("onError: $e") },
                    { Timber.d("onComplete") })
        }

        disconnectButton.setOnClickListener {
            mClient.disconnect()
        }
    }
}
