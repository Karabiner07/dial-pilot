package com.dialpilot.di

import com.dialpilot.core.input.DialInputSource
import com.dialpilot.core.input.MediaKeyDialSource
import com.dialpilot.core.input.VolumeDialSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Registers every [DialInputSource]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class InputModule {
    @Binds @IntoSet
    abstract fun volumeDial(source: VolumeDialSource): DialInputSource

    @Binds @IntoSet
    abstract fun mediaKeys(source: MediaKeyDialSource): DialInputSource
}
