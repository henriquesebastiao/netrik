package com.netrik.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        OuiPrefixEntity::class, OuiMetaEntity::class, OuiHistoryEntity::class, TargetHistoryEntity::class,
        SshGroupEntity::class, SshHostEntity::class, KnownHostEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: target history of the tools (Ping, Traceroute, Port Scanner)
        AutoMigration(from = 1, to = 2),
        // v3: SSH hosts and groups and trusted host keys
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class NetrikDatabase : RoomDatabase() {
    abstract fun ouiDao(): OuiDao
    abstract fun targetHistoryDao(): TargetHistoryDao
    abstract fun sshDao(): SshDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NetrikDatabase =
        Room.databaseBuilder(context, NetrikDatabase::class.java, "netrik.db").build()

    @Provides
    fun provideOuiDao(database: NetrikDatabase): OuiDao = database.ouiDao()

    @Provides
    fun provideTargetHistoryDao(database: NetrikDatabase): TargetHistoryDao = database.targetHistoryDao()

    @Provides
    fun provideSshDao(database: NetrikDatabase): SshDao = database.sshDao()
}
