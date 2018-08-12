// IBaseCallback.aidl
package com.timweng.lib.rxaidl;

interface IBaseCallback {
    void onCallback(long requestId, int state, String callbackContent);
}