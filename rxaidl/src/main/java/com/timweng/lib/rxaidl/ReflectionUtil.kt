package com.timweng.lib.rxaidl

import io.reactivex.Observable
import timber.log.Timber
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

internal class ReflectionUtil {

    companion object {
        private val cacheMethodMap: MutableMap<String, Method> = mutableMapOf()

        fun <T, R, C> findObservableMethod(targetClass: Class<T>,
                                           requestClass: Class<R>,
                                           callbackClass: Class<C>,
                                           methodName: String): Method? {
            val key = targetClass.canonicalName + requestClass.canonicalName + callbackClass.canonicalName + methodName
            if (cacheMethodMap.containsKey(key)) {
                return cacheMethodMap[key]
            }

            if (!methodName.equals(BaseConstant.NULL_METHOD)) {
                var tempMethod: Method?
                try {
                    tempMethod = targetClass.getMethod(methodName, requestClass)
                } catch (e: NoSuchMethodException) {
                    e.printStackTrace()
                    return null
                }

                var returnType = tempMethod.returnType
                var genericReturnType = tempMethod.genericReturnType

                if (returnType == null || !returnType.isAssignableFrom(Observable::class.java)) {
                    return null
                }

                if (genericReturnType != null && genericReturnType is ParameterizedType) {
                    val arguments = genericReturnType.actualTypeArguments
                    if (arguments != null && arguments.size == 1 && arguments[0].toString().equals(callbackClass.toString())) {
                        cacheMethodMap[key] = tempMethod
                        return tempMethod
                    }
                }
                return null
            }

            var returnMethod: Method? = null

            val targetMethods = targetClass.methods
            for (method in targetMethods) {
                var paramTypes = method.parameterTypes
                var returnType = method.returnType
                var genericReturnType = method.genericReturnType

                if (paramTypes == null || paramTypes.size != 1 || !paramTypes[0].equals(requestClass)) {
                    continue
                }
                if (returnType == null || !returnType.isAssignableFrom(Observable::class.java)) {
                    continue
                }

                if (genericReturnType != null && genericReturnType is ParameterizedType) {
                    val arguments = genericReturnType.actualTypeArguments
                    if (arguments != null && arguments.size == 1 && arguments[0].toString().equals(callbackClass.toString())) {
                        returnMethod = method
                        break
                    }
                }
            }

            if (returnMethod != null) {
                cacheMethodMap[key] = returnMethod
            }
            return returnMethod
        }

        fun getClassFromName(name: String): Class<*>? {
            return try {
                Class.forName(name)
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
                null
            }
        }
    }
}