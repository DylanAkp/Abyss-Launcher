package dev.dylanakp.abysstvlauncher.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {
    private val preferences: LauncherPreferences by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && preferences.remoteServerEnabled) {
            RemoteBridgeService.start(context)
        }
    }
}
