package com.timweng.lib.rxaidl

internal class BaseConstant {
    companion object {
        const val STATE_NEXT: Int = 0
        const val STATE_ERROR: Int = 1
        const val STATE_COMPLETE: Int = 2

        const val REQUEST_TYPE_OBSERVABLE: Int = 0
        const val REQUEST_TYPE_SINGLE: Int = 1
        const val REQUEST_TYPE_COMPLEREABLEC: Int = 2
        const val REQUEST_TYPE_MAYBE: Int = 3

        const val REQUEST_ERROR_CLIENT_NOT_SUPPORTED: Long = -1L

        const val NULL_METHOD: String = "NULL_METHOD"
    }
}