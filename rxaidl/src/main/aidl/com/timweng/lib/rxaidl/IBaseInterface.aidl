// IBaseInterface.aidl
package com.timweng.lib.rxaidl;

import com.timweng.lib.rxaidl.IBaseCallback;

interface IBaseInterface {
     boolean register(String clientId, IBaseCallback callback);
     long requestObservable(String clientId, String requestContent, String requestClass, String callbackClass, String methodName);
     boolean dispose(String clientId, long requestId);
     boolean unregister(String clientId);
}
