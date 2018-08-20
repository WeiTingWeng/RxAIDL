package com.timweng.lib.rxaidl.exception

import java.lang.Exception

class ClientNotSupportedException : Exception("Can not find support method on the service")