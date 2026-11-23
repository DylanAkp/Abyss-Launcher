package dev.dylanakp.abysstvlauncher.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {
    private val preferences: LauncherPreferences by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = preferences.dataFlow.first()
                if (prefs[REMOTE_ENABLED_KEY] ?: true) {
                    RemoteBridgeService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val REMOTE_ENABLED_KEY = booleanPreferencesKey("remote_enabled")
    }
}
