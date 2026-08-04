package com.jbgsoft.ambio.core.common.resources

import android.content.Context
import androidx.annotation.StringRes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves string resources outside Compose, where `stringResource()` is unavailable —
 * ViewModels building media notification text, for example.
 */
interface StringProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
}

@Singleton
class AndroidStringProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) : StringProvider {
    override fun get(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StringProviderModule {
    @Binds
    abstract fun bindStringProvider(impl: AndroidStringProvider): StringProvider
}
