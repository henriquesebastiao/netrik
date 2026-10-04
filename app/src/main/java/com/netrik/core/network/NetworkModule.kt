package com.netrik.core.network

import com.netrik.core.network.ping.HostResolver
import com.netrik.core.network.ping.PingRunner
import com.netrik.core.network.ping.ProcessPingRunner
import com.netrik.core.network.ping.SystemHostResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    abstract fun bindNetworkInfoRepository(impl: AndroidNetworkInfoRepository): NetworkInfoRepository

    @Binds
    abstract fun bindPublicIpRepository(impl: IpifyPublicIpRepository): PublicIpRepository

    @Binds
    abstract fun bindPingRunner(impl: ProcessPingRunner): PingRunner

    @Binds
    abstract fun bindHostResolver(impl: SystemHostResolver): HostResolver
}
