package com.checkingcontainer.core.common.di

import javax.inject.Qualifier

/**
 * Qualifier for dispatchers — lets us inject the IO dispatcher (or others)
 * by type, and swap to TestDispatcher in unit tests.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val appDispatcher: AppDispatcher)

enum class AppDispatcher {
    Default,
    IO,
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
