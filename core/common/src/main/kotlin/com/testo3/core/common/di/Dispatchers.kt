package com.testo3.core.common.di

import javax.inject.Qualifier

/**
 * Qualifier for dispatchers — lets us inject the IO dispatcher (or others)
 * by type, and swap to TestDispatcher in unit tests.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val testo3Dispatcher: AppDispatcher)

enum class AppDispatcher {
    Default,
    IO,
}
