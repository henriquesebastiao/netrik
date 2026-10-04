package com.netrik.core.security

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun bindPinHasher(impl: KeystorePinHasher): PinHasher

    @Binds
    abstract fun bindAppLockStore(impl: DataStoreAppLockStore): AppLockStore
}
