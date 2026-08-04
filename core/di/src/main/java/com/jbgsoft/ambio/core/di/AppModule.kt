package com.jbgsoft.ambio.core.di

import android.content.Context
import com.jbgsoft.ambio.core.common.audio.ChimePlayer
import com.jbgsoft.ambio.core.common.di.DefaultDispatcher
import com.jbgsoft.ambio.core.common.di.IoDispatcher
import com.jbgsoft.ambio.core.common.di.MainDispatcher
import com.jbgsoft.ambio.core.common.haptics.HapticManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Provides Hilt bindings for the app's cross-cutting singletons.
 *
 * Rule: even when the type being provided belongs to another `core` module — as with
 * [HapticManager] and [ChimePlayer], both owned by `core:common` — its Hilt binding still lives
 * here in `core:di`, not inside the owning module. This keeps DI wiring discoverable in one
 * place rather than scattered across every `core` module, each of which would otherwise need
 * its own Hilt module (and most already apply the Hilt plugin regardless, so nothing technical
 * forces the split either way — see below).
 *
 * Known exception: `StringProviderModule`, which binds `StringProvider` inside `core:common`
 * itself (`core/common/.../resources/StringProvider.kt`). That predates this rule being written
 * down and is left as-is rather than moved here as part of documenting the convention. New
 * bindings should follow the pattern below, not that exception.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHapticManager(
        @ApplicationContext context: Context
    ): HapticManager = HapticManager(context)

    @Provides
    @Singleton
    fun provideChimePlayer(
        @ApplicationContext context: Context
    ): ChimePlayer = ChimePlayer(context)

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
