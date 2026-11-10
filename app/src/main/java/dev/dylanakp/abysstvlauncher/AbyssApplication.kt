package dev.dylanakp.abysstvlauncher

import android.app.Application
import dev.dylanakp.abysstvlauncher.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class AbyssApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AbyssApplication)
            modules(appModule)
        }
    }
}
