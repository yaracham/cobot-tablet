package com.example.cobot

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Utility function to run blocking code in a coroutine context
 */
object runBlocking {
    suspend fun <T : Any> suspendCancellableCoroutine(block: (suspendCancellableCoroutine<T>) -> Unit): suspendCancellableCoroutine<suspendCancellableCoroutine<T>> {
        return com.example.cobot.suspendCancellableCoroutine(block)
    }
}

/**
 * Simplified version of suspendCancellableCoroutine for use in this project
 */
class suspendCancellableCoroutine<T>(private val continuation: (T) -> Unit) {
    fun resume(value: T) {
        continuation(value)
    }
}

