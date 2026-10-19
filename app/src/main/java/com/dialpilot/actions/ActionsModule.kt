package com.dialpilot.actions

import com.dialpilot.core.action.DialAction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Registers every [DialAction]. Adding a feature is one class plus one `@Binds @IntoSet` here.
 * Actions are singletons because some keep small amounts of state (e.g. cooldown timestamps).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActionsModule {
    @Binds @IntoSet @Singleton
    abstract fun verticalScroll(action: VerticalScrollAction): DialAction

    @Binds @IntoSet @Singleton
    abstract fun horizontalScroll(action: HorizontalScrollAction): DialAction

    @Binds @IntoSet @Singleton
    abstract fun swipeItems(action: SwipeItemsAction): DialAction

    @Binds @IntoSet @Singleton
    abstract fun pageScroll(action: PageScrollAction): DialAction

    @Binds @IntoSet @Singleton
    abstract fun zoom(action: ZoomAction): DialAction

    @Binds @IntoSet @Singleton
    abstract fun mediaTracks(action: MediaTracksAction): DialAction

    @Binds @IntoSet @Singleton
    abstract fun volume(action: VolumeAction): DialAction
}
