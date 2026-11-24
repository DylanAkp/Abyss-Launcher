package dev.dylanakp.abysstvlauncher.di

import dev.dylanakp.abysstvlauncher.data.AppUpdateRepository
import dev.dylanakp.abysstvlauncher.data.JellyfinRepository
import dev.dylanakp.abysstvlauncher.data.JellyfinWebSocketClient
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import dev.dylanakp.abysstvlauncher.data.MediaLibraryCache
import dev.dylanakp.abysstvlauncher.data.PlaybackHistoryRepository
import dev.dylanakp.abysstvlauncher.data.PreferIpv4Dns
import dev.dylanakp.abysstvlauncher.data.SeerRepository
import dev.dylanakp.abysstvlauncher.data.TmdbRepository
import dev.dylanakp.abysstvlauncher.data.WeatherRepository
import dev.dylanakp.abysstvlauncher.data.local.AbyssDatabase
import dev.dylanakp.abysstvlauncher.domain.repository.JellyfinRepository as IJellyfinRepository
import dev.dylanakp.abysstvlauncher.domain.repository.MediaLibraryCache as IMediaLibraryCache
import dev.dylanakp.abysstvlauncher.domain.repository.PlaybackHistoryRepository as IPlaybackHistoryRepository
import dev.dylanakp.abysstvlauncher.domain.repository.SeerRepository as ISeerRepository
import dev.dylanakp.abysstvlauncher.domain.repository.TmdbRepository as ITmdbRepository
import dev.dylanakp.abysstvlauncher.domain.repository.WeatherRepository as IWeatherRepository
import dev.dylanakp.abysstvlauncher.player.PlayerManager
import dev.dylanakp.abysstvlauncher.player.PlayerManagerImpl
import dev.dylanakp.abysstvlauncher.remote.RemoteHttpServer
import dev.dylanakp.abysstvlauncher.remote.TvCommandController
import dev.dylanakp.abysstvlauncher.ui.LauncherViewModel
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {
    single {
        OkHttpClient.Builder()
            .dns(PreferIpv4Dns)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "AbyssLauncher/2.0")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    single { AbyssDatabase.getInstance(androidContext()) }
    single { get<AbyssDatabase>().mediaLibraryDao() }
    single { get<AbyssDatabase>().playbackHistoryDao() }

    single { LauncherPreferences(androidContext()) }

    single { JellyfinRepository(get(), get(), get(), get(), get()) }
    single<IJellyfinRepository> { get<JellyfinRepository>() }
    single { JellyfinWebSocketClient(get(), get(), get()) }
    single { SeerRepository(get(), get(), get()) }
    single<ISeerRepository> { get<SeerRepository>() }
    single { TmdbRepository(get(), get()) }
    single<ITmdbRepository> { get<TmdbRepository>() }
    single { WeatherRepository(get(), get()) }
    single<IWeatherRepository> { get<WeatherRepository>() }
    single { MediaLibraryCache(get(), get()) }
    single<IMediaLibraryCache> { get<MediaLibraryCache>() }
    single { PlaybackHistoryRepository(get(), get()) }
    single<IPlaybackHistoryRepository> { get<PlaybackHistoryRepository>() }
    single { AppUpdateRepository(get(), get()) }

    single<PlayerManager> { PlayerManagerImpl(androidContext(), get()) }

    single { TvCommandController(androidContext(), get(), get(), get(), get(), get()) }
    single { RemoteHttpServer(androidContext(), get(), get()) }

    viewModelOf(::LauncherViewModel)
}
