package com.netrik.core.wifi

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WifiModule {
    @Binds
    abstract fun bindWifiScanRepository(impl: AndroidWifiScanRepository): WifiScanRepository

    @Binds
    abstract fun bindWifiLinkSource(impl: AndroidWifiLinkSource): WifiLinkSource

    @Binds
    abstract fun bindWifiRttRanger(impl: AndroidWifiRttRanger): WifiRttRanger
}
