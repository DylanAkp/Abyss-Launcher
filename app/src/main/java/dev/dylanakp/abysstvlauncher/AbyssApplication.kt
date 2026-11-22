package dev.dylanakp.abysstvlauncher

import android.app.Application
import android.util.Log
import dev.dylanakp.abysstvlauncher.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class AbyssApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@AbyssApplication)
                modules(appModule)
            }
        } catch (e: Exception) {
            Log.e("AbyssApplication", "Failed to start Koin", e)
        }
    }
}
