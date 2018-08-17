package com.timweng.lib.rxaidl.exception

import java.lang.Exception

class ClientNotSupportException : Exception("Can not find support method on the service")