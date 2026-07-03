package org.vliux.orca.logging

import android.util.Log

private const val TAG = "OrcaApp"

object OrcaLog {
    fun info(message: String) {
        invoke(
            fallback = { println("INFO/$TAG: $message") },
            block = { Log.i(TAG, message) },
        )
    }

    fun warn(message: String, throwable: Throwable? = null) {
        invoke(
            fallback = { System.err.println("WARN/$TAG: $message${throwable?.let { " ${it.message}" } ?: ""}") },
            block = { Log.w(TAG, message, throwable) },
        )
    }

    fun error(message: String, throwable: Throwable? = null) {
        invoke(
            fallback = { System.err.println("ERROR/$TAG: $message${throwable?.let { " ${it.message}" } ?: ""}") },
            block = { Log.e(TAG, message, throwable) },
        )
    }

    fun tag(): String = TAG

    private inline fun invoke(
        fallback: () -> Unit,
        block: () -> Int,
    ) {
        try {
            block()
        } catch (runtimeException: RuntimeException) {
            if (runtimeException.message?.contains("not mocked") != true) {
                throw runtimeException
            }
            fallback()
        }
    }
}
