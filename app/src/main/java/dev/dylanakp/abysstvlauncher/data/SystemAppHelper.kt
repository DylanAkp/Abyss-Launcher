package dev.dylanakp.abysstvlauncher.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.PlayerPreference

object SystemAppHelper {

    private val KNOWN_EXTERNAL_PLAYERS = listOf(
        "com.brouken.player",     // Just (Video) Player
        "org.videolan.vlc",       // VLC for Android TV
        "net.gtvbox.videoplayer", // Vimu Media Player
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro"
    )

    fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: RuntimeException) {
            Log.w("SystemAppHelper", "Package check failed for $packageName", e)
            false
        }
    }

    fun playMediaExternal(
        context: Context,
        item: MediaItem,
        preference: PlayerPreference
    ): Boolean {
        val streamUrl = item.streamUrl.trim()
            .replace(Regex("([?&])api_key=[^&]*&?"), "$1")
            .trimEnd('&', '?')
        if (streamUrl.isBlank()) return false
        val uri = Uri.parse(streamUrl)

        if (preference == PlayerPreference.EXTERNAL_PLAYER) {
            for (pkg in KNOWN_EXTERNAL_PLAYERS) {
                if (isPackageInstalled(context.packageManager, pkg)) {
                    val extIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/*")
                        setPackage(pkg)
                        putExtra("title", item.title)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(extIntent)
                        return true
                    } catch (e: ActivityNotFoundException) {
                        Log.w("SystemAppHelper", "External player activity not found: $pkg", e)
                    } catch (e: SecurityException) {
                        Log.w("SystemAppHelper", "External player launch denied: $pkg", e)
                    }
                }
            }
        }

        val genericView = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            putExtra("title", item.title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (preference == PlayerPreference.SYSTEM_CHOOSER) {
                val chooser = Intent.createChooser(genericView, item.title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } else {
                context.startActivity(genericView)
            }
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("SystemAppHelper", "No activity found for external media intent", e)
            false
        } catch (e: SecurityException) {
            Log.w("SystemAppHelper", "Security error launching external media intent", e)
            false
        }
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("SystemAppHelper", "No activity found for external media intent", e)
            false
        } catch (e: SecurityException) {
            Log.w("SystemAppHelper", "Security error launching external media intent", e)
            false
        }
    }

    fun openAndroidSettings(context: Context) {
        safeStartIntent(context, Intent(Settings.ACTION_SETTINGS))
    }

    fun openWifiSettings(context: Context) {
        if (!safeStartIntent(context, Intent(Settings.ACTION_WIFI_SETTINGS))) {
            safeStartIntent(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    fun openBluetoothSettings(context: Context) {
        safeStartIntent(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun openDisplaySettings(context: Context) {
        if (!safeStartIntent(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))) {
            safeStartIntent(context, Intent(Settings.ACTION_SOUND_SETTINGS))
        }
    }

    fun openManageAppsSettings(context: Context) {
        safeStartIntent(context, Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
    }

    fun openDefaultHomeSelector(context: Context) {
        if (!safeStartIntent(context, Intent(Settings.ACTION_HOME_SETTINGS))) {
            val homeChooser = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            safeStartIntent(context, Intent.createChooser(homeChooser, "Abyss TV"))
        }
    }

    fun openAppDetails(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        safeStartIntent(context, intent)
    }

    private fun safeStartIntent(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("SystemAppHelper", "No activity found for external media intent", e)
            false
        } catch (e: SecurityException) {
            Log.w("SystemAppHelper", "Security error launching external media intent", e)
            false
        }
    }
}
