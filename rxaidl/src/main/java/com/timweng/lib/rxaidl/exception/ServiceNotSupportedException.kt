package com.timweng.lib.rxaidl.exception

import java.lang.Exception

class ServiceNotSupportedException(current: Long, min: Long, max: Long)
    : Exception("Service is not supported: Current version is $current, require version is $min to $max")