package com.netrik.core.knock

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class KnockModule {
    @Binds
    abstract fun bindKnockSender(impl: SocketKnockSender): KnockSender
}
