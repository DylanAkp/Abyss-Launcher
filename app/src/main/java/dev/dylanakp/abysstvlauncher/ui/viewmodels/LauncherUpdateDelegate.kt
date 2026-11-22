package dev.dylanakp.abysstvlauncher.ui.viewmodels

import android.content.Context
import android.util.Log
import dev.dylanakp.abysstvlauncher.BuildConfig
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.AppUpdateRepository
import dev.dylanakp.abysstvlauncher.data.InstallResult
import dev.dylanakp.abysstvlauncher.ui.LauncherUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class LauncherUpdateDelegate(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<LauncherUiState>,
    private val showStatusMessage: (Int, Array<out Any>) -> Unit
) {
    fun checkForUpdates(silent: Boolean = false) {
        if (uiState.value.isCheckingUpdates) return
        uiState.update { it.copy(isCheckingUpdates = true) }
        scope.launch {
            AppUpdateRepository.checkLatestRelease()
                .onSuccess { update ->
                    uiState.update { it.copy(isCheckingUpdates = false) }
                    if (update != null) {
                        uiState.update {
                            it.copy(
                                pendingUpdate = update,
                                updateError = null,
                                isDownloadingUpdate = false,
                                updateDownloadProgress = 0f
                            )
                        }
                    } else if (!silent) {
                        showStatusMessage(R.string.update_already_latest, arrayOf(BuildConfig.VERSION_NAME))
                    }
                }
                .onFailure {
                    uiState.update { it.copy(isCheckingUpdates = false) }
                    if (!silent) {
                        showStatusMessage(R.string.update_check_failed, emptyArray())
                    }
                }
        }
    }

    fun dismissUpdate() {
        uiState.update {
            it.copy(
                pendingUpdate = null,
                isDownloadingUpdate = false,
                updateDownloadProgress = 0f,
                updateError = null
            )
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val update = uiState.value.pendingUpdate ?: return
        if (uiState.value.isDownloadingUpdate) return

        uiState.update {
            it.copy(
                isDownloadingUpdate = true,
                updateDownloadProgress = 0f,
                updateError = null
            )
        }
        scope.launch {
            AppUpdateRepository.downloadApk(context, update.apkUrl) { progress ->
                uiState.update { it.copy(updateDownloadProgress = progress) }
            }.onSuccess { apkFile ->
                uiState.update { it.copy(isDownloadingUpdate = false) }
                when (val result = AppUpdateRepository.triggerInstall(context, apkFile)) {
                    InstallResult.Success -> dismissUpdate()
                    InstallResult.NeedsPermission ->
                        showStatusMessage(R.string.update_needs_permission, emptyArray())
                    is InstallResult.Failure -> {
                        Log.e("AbyssUpdate", "Install trigger failed", result.error)
                        uiState.update {
                            it.copy(updateError = result.error.localizedMessage ?: "Installation failed")
                        }
                    }
                }
            }.onFailure { error ->
                Log.e("AbyssUpdate", "Download failed", error)
                uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateError = error.localizedMessage ?: "Download failed"
                    )
                }
            }
        }
    }
}
