// IBaseInterface.aidl
package com.timweng.lib.rxaidl;

import com.timweng.lib.rxaidl.IBaseCallback;

interface IBaseInterface {
     long register(String clientId, long version, String option, IBaseCallback callback);
     long request(String clientId, int requestType, String requestContent, String requestClass, String callbackClass, String methodName);
     boolean dispose(String clientId, long requestId);
     boolean unregister(String clientId);
}
