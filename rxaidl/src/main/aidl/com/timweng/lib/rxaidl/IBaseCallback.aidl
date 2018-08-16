// IBaseCallback.aidl
package com.timweng.lib.rxaidl;

interface IBaseCallback {
    oneway void onCallback(long requestId, int state, String callbackContent);
}