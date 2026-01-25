package com.jbgsoft.ambio.core.common.di

import javax.inject.Qualifier

/**
 * Qualifier for the default dispatcher used for CPU-intensive work.
 * Maps to Dispatchers.Default in production.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Qualifier for the IO dispatcher used for disk/network operations.
 * Maps to Dispatchers.IO in production.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Qualifier for the Main dispatcher used for UI operations.
 * Maps to Dispatchers.Main in production.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
