package com.timweng.lib.rxaidl.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequestRequirement(
        val minClientVersion: Long = 0L,
        val maxClientVersion: Long = Long.MAX_VALUE
)