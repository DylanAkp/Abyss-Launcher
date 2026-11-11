package dev.dylanakp.abysstvlauncher.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dylanakp.abysstvlauncher.data.AppUpdate
import dev.dylanakp.abysstvlauncher.data.AppUpdateRepository
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import dev.dylanakp.abysstvlauncher.domain.repository.JellyfinRepository
import dev.dylanakp.abysstvlauncher.domain.repository.MediaLibraryCache
import dev.dylanakp.abysstvlauncher.domain.repository.SeerRepository
import dev.dylanakp.abysstvlauncher.model.AbyssAccentPreset
import dev.dylanakp.abysstvlauncher.model.AppLanguage
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.model.TemperatureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val jellyfinConfig: JellyfinConfig = JellyfinConfig(),
    val seerConfig: SeerConfig = SeerConfig(),
    val language: AppLanguage = AppLanguage.AUTO,
    val accentPreset: AbyssAccentPreset = AbyssAccentPreset.ABYSS_VIOLET,
    val weatherCity: String = "Paris",
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val isTestingJellyfin: Boolean = false,
    val isTestingSeer: Boolean = false,
    val isSyncingJellyfin: Boolean = false,
    val pendingUpdate: AppUpdate? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val updateError: String? = null
)

class SettingsViewModel(
    private val preferences: LauncherPreferences,
    private val jellyfinRepo: JellyfinRepository,
    private val seerRepo: SeerRepository,
    private val mediaCache: MediaLibraryCache,
    private val appUpdateRepo: AppUpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            jellyfinConfig = preferences.jellyfinConfig,
            seerConfig = preferences.seerConfig,
            language = preferences.language,
            accentPreset = preferences.accentPreset,
            weatherCity = preferences.weatherCity,
            temperatureUnit = preferences.temperatureUnit
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateJellyfinConfig(config: JellyfinConfig) {
        preferences.jellyfinConfig = config
        _uiState.update { it.copy(jellyfinConfig = config) }
    }

    fun updateSeerConfig(config: SeerConfig) {
        preferences.seerConfig = config
        _uiState.update { it.copy(seerConfig = config) }
    }

    fun setLanguage(lang: AppLanguage) {
        preferences.language = lang
        _uiState.update { it.copy(language = lang) }
    }

    fun setAccentPreset(preset: AbyssAccentPreset) {
        preferences.accentPreset = preset
        _uiState.update { it.copy(accentPreset = preset) }
    }

    fun testJellyfinConnection(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingJellyfin = true) }
            val config = _uiState.value.jellyfinConfig
            val authRes = if (config.accessToken.isBlank() && config.username.isNotBlank()) {
                jellyfinRepo.authenticate(config)
            } else {
                Result.success(config)
            }

            val res = authRes.fold(
                onSuccess = { authedConfig ->
                    if (authedConfig.accessToken != config.accessToken) {
                        updateJellyfinConfig(authedConfig)
                    }
                    jellyfinRepo.testConnection(authedConfig)
                },
                onFailure = { Result.failure(it) }
            )

            _uiState.update { it.copy(isTestingJellyfin = false) }
            onResult(res)
        }
    }

    fun testSeerConnection(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingSeer = true) }
            val res = seerRepo.testConnection(_uiState.value.seerConfig)
            _uiState.update { it.copy(isTestingSeer = false) }
            onResult(res)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true, updateError = null) }
            val res = appUpdateRepo.checkLatestRelease()
            _uiState.update {
                it.copy(
                    isCheckingUpdate = false,
                    pendingUpdate = res.getOrNull()
                )
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context, update: AppUpdate) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingUpdate = true,
                    updateDownloadProgress = 0f,
                    updateError = null
                )
            }

            val apkResult = appUpdateRepo.downloadApk(context, update.apkUrl) { progress ->
                _uiState.update { it.copy(updateDownloadProgress = progress) }
            }

            apkResult.onSuccess { apkFile ->
                _uiState.update { it.copy(isDownloadingUpdate = false) }
                appUpdateRepo.triggerInstall(context, apkFile)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateError = err.message ?: "Failed to download update"
                    )
                }
            }
        }
    }
}
