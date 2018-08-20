package com.timweng.lib.rxaidl.exception

import java.lang.Exception

class ServiceNotSupportedException(current: Long, min: Long, max: Long)
    : Exception("Service is not support: current version is $current, require version is $min to $max")