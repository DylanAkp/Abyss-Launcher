package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import dev.dylanakp.abysstvlauncher.model.TvAppInfo
import dev.dylanakp.abysstvlauncher.ui.components.prewarmAppBitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppRepository {

    suspend fun loadInstalledApps(
        context: Context,
        pinnedPackages: List<String>,
        hiddenPackages: Set<String>
    ): List<TvAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPkg = context.packageName

        val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val standardIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val leanbackResolved = pm.queryIntentActivities(leanbackIntent, 0)
        val standardResolved = pm.queryIntentActivities(standardIntent, 0)

        val leanbackPkgSet = leanbackResolved.map { it.activityInfo.packageName }.toSet()
        val combinedMap = linkedMapOf<String, ResolveInfo>()

        for (info in leanbackResolved) {
            val pkg = info.activityInfo.packageName
            if (pkg != selfPkg) {
                combinedMap[pkg] = info
            }
        }
        for (info in standardResolved) {
            val pkg = info.activityInfo.packageName
            if (pkg != selfPkg && !combinedMap.containsKey(pkg)) {
                combinedMap[pkg] = info
            }
        }

        val result = mutableListOf<TvAppInfo>()

        for ((pkg, resolveInfo) in combinedMap) {
            val rawLabel = try {
                resolveInfo.loadLabel(pm).toString().ifBlank { pkg }
            } catch (_: Exception) {
                pkg
            }
            val label = if (pkg == "io.gh.reisxd.tizentube.cobalt" || pkg.contains("tizentube", ignoreCase = true)) {
                "YouTube"
            } else {
                rawLabel
            }
            val banner = try {
                if (leanbackPkgSet.contains(pkg)) {
                    resolveInfo.activityInfo.loadBanner(pm)
                        ?: pm.getApplicationBanner(pkg)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
            val icon = try {
                resolveInfo.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            val pinIndex = pinnedPackages.indexOf(pkg)
            val isPinned = pinIndex != -1
            val isHidden = hiddenPackages.contains(pkg)

            result.add(
                TvAppInfo(
                    packageName = pkg,
                    label = label,
                    icon = icon,
                    banner = banner,
                    isLeanback = leanbackPkgSet.contains(pkg),
                    isPinned = isPinned,
                    isHidden = isHidden,
                    pinOrder = if (isPinned) pinIndex else Int.MAX_VALUE,
                    isSystemSettingsOrUtility = pkg.contains("settings", ignoreCase = true)
                )
            )
        }

        // If fewer than 3 pinned apps exist on a clean emulator, auto-pin the first few discovered apps
        val hasAnyRealPin = result.count { it.isPinned && !it.isHidden } > 1
        val finalResult = if (!hasAnyRealPin) {
            result.mapIndexed { index, app ->
                if (index < 6 && !app.isHidden) {
                    app.copy(isPinned = true, pinOrder = index)
                } else {
                    app
                }
            }
        } else {
            result
        }

        val sorted = finalResult.sortedWith(
            compareBy<TvAppInfo> { !it.isPinned }
                .thenBy { it.pinOrder }
                .thenBy { it.label.lowercase() }
        )
        prewarmAppBitmaps(sorted)
        sorted
    }
}
