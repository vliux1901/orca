package org.vliux.orca.prototype.cloudflow

import android.util.Log

private const val TAG = "OrcaPrototype"

object PrototypeLog {
    fun info(message: String) {
        Log.i(TAG, message)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun error(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }

    fun tag(): String = TAG
}
