package com.cyron.manager.di

import android.content.Context
import androidx.room.Room
import com.cyron.manager.data.CyronDatabase
import com.cyron.manager.data.DataStoreSettingsRepository
import com.cyron.manager.data.RoomDownloadRepository
import com.cyron.manager.download.ConnectionCountProvider
import com.cyron.manager.download.DownloadEngine
import com.cyron.manager.download.OkHttpDownloadEngine
import com.cyron.manager.download.SourceTransferSnapshots
import com.cyron.manager.domain.repository.DownloadRepository
import com.cyron.manager.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import android.content.ContentResolver
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.IOException

private object HttpsOrigin

@Singleton
class FolderAccessCoordinator @Inject constructor() {
    private val mutex = Mutex()
    suspend fun <T> withFolderLock(block: suspend () -> T): T = mutex.withLock { block() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {
    @Binds @Singleton abstract fun downloads(implementation: RoomDownloadRepository): DownloadRepository
    @Binds @Singleton abstract fun settings(implementation: DataStoreSettingsRepository): SettingsRepository
    @Binds @Singleton abstract fun engine(implementation: OkHttpDownloadEngine): DownloadEngine
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CyronDatabase =
        Room.databaseBuilder(context, CyronDatabase::class.java, "cyron_manager.db")
            .addMigrations(CyronDatabase.MIGRATION_1_2, CyronDatabase.MIGRATION_2_3, CyronDatabase.MIGRATION_3_4, CyronDatabase.MIGRATION_4_5, CyronDatabase.MIGRATION_5_6)
            .build()

    @Provides @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder().header("Accept-Encoding", "identity")
            if (original.url.isHttps) builder.tag(HttpsOrigin::class.java, HttpsOrigin)
            chain.proceed(builder.build())
        }
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (request.tag(HttpsOrigin::class.java) != null && !request.url.isHttps) {
                throw IOException("Refusing an HTTPS to HTTP downgrade.")
            }
            chain.proceed(request)
        }
        .build()

    /**
     * Transfer work blocks on socket reads. Keep it off Dispatchers.IO so pause,
     * cancel, and UI work never wait behind 100+ blocked transfer goroutines.
     */
    @Provides @Singleton fun ioDispatcher(): CoroutineDispatcher {
        // The settings allow up to 8 concurrent downloads × 16 ranges, plus probes.
        // A fixed pool of 32 left most ranges waiting while the UI still said
        // "downloading"; grow on demand up to the real upper bound and retire idle
        // threads after a minute.
        val executor = java.util.concurrent.ThreadPoolExecutor(
            16,
            160,
            60L,
            java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.SynchronousQueue(),
            { runnable -> Thread(runnable, "cyron-transfer").apply { isDaemon = true } },
            java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
        )
        return executor.asCoroutineDispatcher()
    }

    @Provides @Singleton
    fun connectionCountProvider() = ConnectionCountProvider { download ->
        download.connectionCount.coerceIn(1, 16)
    }
}
