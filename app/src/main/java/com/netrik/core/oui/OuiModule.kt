package com.netrik.core.oui

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OuiModule {

    @Binds
    abstract fun bindOuiRepository(impl: DefaultOuiRepository): OuiRepository

    @Binds
    abstract fun bindOuiSource(impl: AndroidOuiSource): OuiSource
}
