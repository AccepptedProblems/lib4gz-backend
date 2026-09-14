package com.example.lib4gz.auth.service

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

/** Base64 HMAC key long enough for HS256; same shape as the application.yaml default. */
const val TEST_JWT_SECRET = "404E635266556A586E3272357F4428472B4B6250645367566B5970"

/** Kotlin-safe `any()`: Mockito's returns null, which a non-null parameter rejects. */
@Suppress("UNCHECKED_CAST")
fun <T> anyValue(): T {
    ArgumentMatchers.any<T>()
    return null as T
}

/** A TransactionTemplate whose `execute` simply runs the callback, so services run without a database. */
fun passThroughTransactionTemplate(): TransactionTemplate {
    val tx = mock(TransactionTemplate::class.java)
    `when`(tx.execute(ArgumentMatchers.any<TransactionCallback<Any>>())).thenAnswer {
        (it.arguments[0] as TransactionCallback<*>).doInTransaction(mock(TransactionStatus::class.java))
    }
    return tx
}
