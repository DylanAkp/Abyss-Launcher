package dev.dylanakp.abysstvlauncher.ui.components

import dev.dylanakp.abysstvlauncher.BuildConfig
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.ui.components.settings.AppearanceSectionContent
import dev.dylanakp.abysstvlauncher.ui.components.settings.JellyfinSectionContent
import dev.dylanakp.abysstvlauncher.ui.components.settings.RemoteSectionContent
import dev.dylanakp.abysstvlauncher.ui.components.settings.SeerSectionContent
import dev.dylanakp.abysstvlauncher.ui.components.settings.SettingsTrackPicker
import dev.dylanakp.abysstvlauncher.ui.components.settings.TRACK_AUDIO_OPTIONS
import dev.dylanakp.abysstvlauncher.ui.components.settings.TRACK_SUBTITLE_OPTIONS
import dev.dylanakp.abysstvlauncher.ui.components.settings.TrackPickerKind
import dev.dylanakp.abysstvlauncher.ui.components.settings.audioChoice
import dev.dylanakp.abysstvlauncher.ui.components.settings.subtitleChoice
import dev.dylanakp.abysstvlauncher.ui.components.settings.withAudioChoice
import dev.dylanakp.abysstvlauncher.ui.components.settings.withSubtitleChoice
import dev.dylanakp.abysstvlauncher.ui.components.settings.SettingsGroup
import dev.dylanakp.abysstvlauncher.data.WeatherInfo
import dev.dylanakp.abysstvlauncher.model.AppLanguage
import dev.dylanakp.abysstvlauncher.model.TemperatureUnit
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.PlayerPreference
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.model.TrackPreference
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import kotlinx.coroutines.delay

private enum class SettingsSection {
    JELLYFIN,
    SEER,
    APPEARANCE,
    REMOTE
}

@Composable
fun SettingsDrawer(
    isOpen: Boolean,
    navInitialFocusRequester: FocusRequester? = null,
    jellyfinConfig: JellyfinConfig,
    seerConfig: SeerConfig,
    trackPreference: TrackPreference,
    isSyncing: Boolean,
    isTestingSeer: Boolean = false,
    currentLanguage: AppLanguage,
    showHiddenApps: Boolean,
    weatherCity: String = "Paris",
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    weatherInfo: WeatherInfo? = null,
    onClose: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onSaveAndConnectJellyfin: (String, String, String) -> Unit,
    onSaveAndTestSeer: (String, String) -> Unit,
    onSelectPlayerPreference: (PlayerPreference) -> Unit,
    onSelectTrackPreference: (TrackPreference) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleShowHiddenApps: (Boolean) -> Unit,
    onUpdateWeatherCity: (String) -> Unit = {},
    onSelectTemperatureUnit: (TemperatureUnit) -> Unit = {},
    isCheckingUpdates: Boolean = false,
    onCheckForUpdates: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150))
    ) {
        var activeSection by remember { mutableStateOf(SettingsSection.JELLYFIN) }
        var trackPicker by remember { mutableStateOf<TrackPickerKind?>(null) }

        LaunchedEffect(isOpen) {
            if (!isOpen) trackPicker = null
        }

        val fallbackNavJfFocusRequester = remember { FocusRequester() }
        val navJfFocusRequester = navInitialFocusRequester ?: fallbackNavJfFocusRequester
        val navSeerFocusRequester = remember { FocusRequester() }
        val navAppearanceFocusRequester = remember { FocusRequester() }
        val navRemoteFocusRequester = remember { FocusRequester() }

        RequestModalInitialFocus(navJfFocusRequester, isOpen, enabled = isOpen)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121316))
                .focusGroup()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 24.dp)
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SettingsBackButton(
                            label = stringResource(R.string.btn_back),
                            onClick = onClose
                        )

                        Column {
                            Text(
                                text = stringResource(R.string.settings_header_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    letterSpacing = 0.2.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.settings_header_subtitle),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color(0xFF8E918F)
                            )
                        }
                    }

                    // Top Right Status & Gear
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isConnected = jellyfinConfig.isConfigured && jellyfinConfig.accessToken.isNotBlank()
                        val statusText = stringResource(
                            if (isConnected) R.string.status_jellyfin_online else R.string.status_jellyfin_offline
                        )
                        val statusColor = if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E2025))
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFFE8EAED)
                            )
                        }

                        SettingsHeaderGearButton(
                            onClick = onOpenAndroidSettings,
                            contentDescription = stringResource(R.string.sys_android_settings)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Split Layout: Left Navigation + Right Content
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Left Nav Column (Google TV Clean List)
                    Column(
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.section_basics).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp
                            ),
                            color = Color(0xFF8E918F),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        SettingsNavTab(
                            label = "Jellyfin",
                            subLabel = stringResource(R.string.settings_section_jellyfin),
                            icon = AbyssIconType.SERVER,
                            isSelected = activeSection == SettingsSection.JELLYFIN,
                            focusRequester = navJfFocusRequester,
                            onClick = { activeSection = SettingsSection.JELLYFIN }
                        )

                        SettingsNavTab(
                            label = "Seer (Jellyseerr)",
                            subLabel = stringResource(R.string.settings_section_seer),
                            icon = AbyssIconType.GLOBE,
                            isSelected = activeSection == SettingsSection.SEER,
                            focusRequester = navSeerFocusRequester,
                            onClick = { activeSection = SettingsSection.SEER }
                        )

                        SettingsNavTab(
                            label = stringResource(R.string.settings_section_appearance),
                            subLabel = stringResource(R.string.appearance_language_label),
                            icon = AbyssIconType.DISPLAY,
                            isSelected = activeSection == SettingsSection.APPEARANCE,
                            focusRequester = navAppearanceFocusRequester,
                            onClick = { activeSection = SettingsSection.APPEARANCE }
                        )

                        SettingsNavTab(
                            label = stringResource(R.string.settings_section_remote),
                            subLabel = stringResource(R.string.settings_section_remote_sub),
                            icon = AbyssIconType.REMOTE,
                            isSelected = activeSection == SettingsSection.REMOTE,
                            focusRequester = navRemoteFocusRequester,
                            onClick = { activeSection = SettingsSection.REMOTE }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.section_system).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp
                            ),
                            color = Color(0xFF8E918F),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        SettingsSidebarActionButton(
                            label = stringResource(R.string.sys_android_settings),
                            subLabel = stringResource(R.string.sys_android_settings_sub),
                            icon = AbyssIconType.SETTINGS,
                            onClick = onOpenAndroidSettings
                        )

                        SettingsSidebarActionButton(
                            label = stringResource(R.string.update_check_button),
                            subLabel = if (isCheckingUpdates) stringResource(R.string.update_checking) else "v${BuildConfig.VERSION_NAME}",
                            icon = if (isCheckingUpdates) AbyssIconType.RESTART else AbyssIconType.INFO,
                            onClick = onCheckForUpdates
                        )
                    }

                    // Right Content Panel (Google TV Solid Surface)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (activeSection) {
                            SettingsSection.JELLYFIN -> {
                                JellyfinSectionContent(
                                    config = jellyfinConfig,
                                    trackPreference = trackPreference,
                                    isSyncing = isSyncing,
                                    onSaveAndConnect = onSaveAndConnectJellyfin,
                                    onSelectPlayerPreference = onSelectPlayerPreference,
                                    onOpenTrackPicker = { trackPicker = it }
                                )
                            }
                            SettingsSection.SEER -> {
                                SeerSectionContent(
                                    config = seerConfig,
                                    isTesting = isTestingSeer,
                                    onSaveAndTest = onSaveAndTestSeer
                                )
                            }
                            SettingsSection.APPEARANCE -> {
                                AppearanceSectionContent(
                                    currentLanguage = currentLanguage,
                                    showHiddenApps = showHiddenApps,
                                    weatherCity = weatherCity,
                                    temperatureUnit = temperatureUnit,
                                    weatherInfo = weatherInfo,
                                    onSelectLanguage = onSelectLanguage,
                                    onToggleShowHiddenApps = onToggleShowHiddenApps,
                                    onUpdateWeatherCity = onUpdateWeatherCity,
                                    onSelectTemperatureUnit = onSelectTemperatureUnit
                                )
                            }
                            SettingsSection.REMOTE -> {
                                RemoteSectionContent()
                            }
                        }
                    }
                }
            }

            trackPicker?.let { kind ->
                SettingsTrackPicker(
                    kind = kind,
                    options = if (kind == TrackPickerKind.AUDIO) TRACK_AUDIO_OPTIONS else TRACK_SUBTITLE_OPTIONS,
                    selected = if (kind == TrackPickerKind.AUDIO) trackPreference.audioChoice() else trackPreference.subtitleChoice(),
                    onSelect = { choice ->
                        onSelectTrackPreference(
                            if (kind == TrackPickerKind.AUDIO) {
                                trackPreference.withAudioChoice(choice)
                            } else {
                                trackPreference.withSubtitleChoice(choice)
                            }
                        )
                    },
                    onDismiss = { trackPicker = null }
                )
            }
        }
    }
}
