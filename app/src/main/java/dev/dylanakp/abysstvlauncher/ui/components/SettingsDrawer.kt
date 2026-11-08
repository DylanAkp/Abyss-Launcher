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
import androidx.compose.material3.CircularProgressIndicator
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
import dev.dylanakp.abysstvlauncher.data.WeatherInfo
import dev.dylanakp.abysstvlauncher.model.AppLanguage
import dev.dylanakp.abysstvlauncher.model.TemperatureUnit
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.PlayerPreference
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import kotlinx.coroutines.delay

private enum class SettingsSection {
    JELLYFIN,
    SEER,
    APPEARANCE
}

@Composable
fun SettingsDrawer(
    isOpen: Boolean,
    jellyfinConfig: JellyfinConfig,
    seerConfig: SeerConfig,
    isSyncing: Boolean,
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
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleShowHiddenApps: (Boolean) -> Unit,
    onUpdateWeatherCity: (String) -> Unit = {},
    onSelectTemperatureUnit: (TemperatureUnit) -> Unit = {},
    onCheckForUpdates: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150))
    ) {
        var activeSection by remember { mutableStateOf(SettingsSection.JELLYFIN) }

        val navJfFocusRequester = remember { FocusRequester() }
        val navSeerFocusRequester = remember { FocusRequester() }
        val navAppearanceFocusRequester = remember { FocusRequester() }

        LaunchedEffect(isOpen) {
            if (isOpen) {
                delay(60L)
                try {
                    navJfFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121316))
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
                        val statusText = if (isConnected) "Jellyfin Online" else "Jellyfin Offline"
                        val statusColor = if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E2025))
                                .border(1.dp, Color(0xFF2C3038), RoundedCornerShape(8.dp))
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
                            .fillMaxHeight(),
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
                            subLabel = "v${BuildConfig.VERSION_NAME}",
                            icon = AbyssIconType.INFO,
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
                                    isSyncing = isSyncing,
                                    onSaveAndConnect = onSaveAndConnectJellyfin,
                                    onSelectPlayerPreference = onSelectPlayerPreference
                                )
                            }
                            SettingsSection.SEER -> {
                                SeerSectionContent(
                                    config = seerConfig,
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M3ExpressiveGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            ),
            color = Color(0xFF8AB4F8),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2025))
                .border(1.dp, Color(0xFF2C3038), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun JellyfinSectionContent(
    config: JellyfinConfig,
    isSyncing: Boolean,
    onSaveAndConnect: (String, String, String) -> Unit,
    onSelectPlayerPreference: (PlayerPreference) -> Unit
) {
    var serverUrl by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var username by remember(config.username) { mutableStateOf(config.username) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    val connectButtonFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        M3ExpressiveGroup(title = stringResource(R.string.section_connection)) {
            Text(
                text = stringResource(R.string.jf_description),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = AbyssTextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            SettingsGlassInputField(
                label = stringResource(R.string.jf_server_url_label),
                value = serverUrl,
                onValueChange = { serverUrl = it },
                placeholder = "http://192.168.1.100:8096"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SettingsGlassInputField(
                        label = stringResource(R.string.jf_username_label),
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "username"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    SettingsGlassInputField(
                        label = stringResource(R.string.jf_password_label),
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "••••••••",
                        isPassword = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            SettingsPrimaryActionButton(
                label = if (isSyncing) stringResource(R.string.jf_connecting_text) else stringResource(R.string.jf_save_and_connect_btn),
                isLoading = isSyncing,
                focusRequester = connectButtonFocusRequester,
                onClick = { onSaveAndConnect(serverUrl.trim(), username.trim(), password.trim()) }
            )
        }

        if (config.isConfigured && config.lastSyncTimestamp > 0L) {
            M3ExpressiveGroup(title = stringResource(R.string.section_library_status)) {
                val stats = stringResource(R.string.jf_stats_template, config.syncedMoviesCount, config.syncedSeriesCount)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF14291F))
                        .border(1.dp, Color(0xFF1B4332), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AbyssIcon(type = AbyssIconType.CHECK, color = Color(0xFF10B981), size = 18.dp)
                    Text(
                        text = "$stats (${config.serverName.ifBlank { "Connected" }})",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = Color(0xFF34D399)
                    )
                }
            }
        }

        M3ExpressiveGroup(title = stringResource(R.string.section_player_prefs)) {
            val isFrench = java.util.Locale.getDefault().language.startsWith("fr")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoogleTvRadioRow(
                    title = if (isFrench) "Lecteur Abyss (ExoPlayer)" else "Abyss Player (ExoPlayer)",
                    subtitle = if (isFrench) "Lecture fluide 4K native, reprise auto, sélection pistes audio & sous-titres" else "Smooth 4K native playback, auto-resume, audio & subtitle tracks",
                    icon = AbyssIconType.PLAY,
                    isSelected = config.playerPreference == PlayerPreference.ABYSS_INTERNAL,
                    onClick = { onSelectPlayerPreference(PlayerPreference.ABYSS_INTERNAL) }
                )
                GoogleTvRadioRow(
                    title = if (isFrench) "Lecteur externe (VLC / JustPlayer)" else "External Player (VLC / JustPlayer)",
                    subtitle = if (isFrench) "Délègue la lecture vidéo à VLC, JustPlayer, Nova ou Vimu" else "Delegates playback to VLC, JustPlayer, Nova or Vimu",
                    icon = AbyssIconType.EXTERNAL_LINK,
                    isSelected = config.playerPreference == PlayerPreference.EXTERNAL_PLAYER,
                    onClick = { onSelectPlayerPreference(PlayerPreference.EXTERNAL_PLAYER) }
                )
                GoogleTvRadioRow(
                    title = if (isFrench) "Toujours demander" else "Always Ask",
                    subtitle = if (isFrench) "Affiche la boîte de sélection d'application Android" else "Shows the Android system app chooser dialog",
                    icon = AbyssIconType.SETTINGS,
                    isSelected = config.playerPreference == PlayerPreference.SYSTEM_CHOOSER,
                    onClick = { onSelectPlayerPreference(PlayerPreference.SYSTEM_CHOOSER) }
                )
            }
        }
    }
}

@Composable
private fun SeerSectionContent(
    config: SeerConfig,
    onSaveAndTest: (String, String) -> Unit
) {
    var serverUrl by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var isTesting by remember { mutableStateOf(false) }

    val testButtonFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        M3ExpressiveGroup(title = stringResource(R.string.settings_section_seer)) {
            Text(
                text = stringResource(R.string.seer_description),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = AbyssTextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            SettingsGlassInputField(
                label = stringResource(R.string.seer_server_url_label),
                value = serverUrl,
                onValueChange = { serverUrl = it },
                placeholder = "http://192.168.1.100:5055"
            )

            SettingsGlassInputField(
                label = stringResource(R.string.seer_api_key_label),
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA="
            )

            Spacer(modifier = Modifier.height(4.dp))

            SettingsPrimaryActionButton(
                label = if (isTesting) stringResource(R.string.seer_testing_text) else stringResource(R.string.seer_save_and_test_btn),
                isLoading = isTesting,
                focusRequester = testButtonFocusRequester,
                onClick = {
                    isTesting = true
                    onSaveAndTest(serverUrl.trim(), apiKey.trim())
                    isTesting = false
                }
            )
        }

        if (config.isConfigured) {
            M3ExpressiveGroup(title = stringResource(R.string.section_library_status)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF14291F))
                        .border(1.dp, Color(0xFF1B4332), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AbyssIcon(type = AbyssIconType.CHECK, color = Color(0xFF10B981), size = 18.dp)
                    Text(
                        text = stringResource(R.string.seer_status_connected),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = Color(0xFF34D399)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSectionContent(
    currentLanguage: AppLanguage,
    showHiddenApps: Boolean,
    weatherCity: String,
    temperatureUnit: TemperatureUnit,
    weatherInfo: WeatherInfo?,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleShowHiddenApps: (Boolean) -> Unit,
    onUpdateWeatherCity: (String) -> Unit,
    onSelectTemperatureUnit: (TemperatureUnit) -> Unit
) {
    val scrollState = rememberScrollState()
    val isFrench = java.util.Locale.getDefault().language.startsWith("fr")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        M3ExpressiveGroup(title = stringResource(R.string.appearance_language_label)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoogleTvRadioRow(
                    title = "Auto (Système / System)",
                    subtitle = if (isFrench) "Suit automatiquement la langue de votre NVIDIA Shield" else "Matches your TV system language",
                    icon = AbyssIconType.GLOBE,
                    isSelected = currentLanguage == AppLanguage.AUTO,
                    onClick = { onSelectLanguage(AppLanguage.AUTO) }
                )
                GoogleTvRadioRow(
                    title = "Français",
                    subtitle = "Interface, menus et métadonnées en langue française",
                    icon = AbyssIconType.GLOBE,
                    isSelected = currentLanguage == AppLanguage.FR,
                    onClick = { onSelectLanguage(AppLanguage.FR) }
                )
                GoogleTvRadioRow(
                    title = "English",
                    subtitle = "User interface, menus and overviews in English",
                    icon = AbyssIconType.GLOBE,
                    isSelected = currentLanguage == AppLanguage.EN,
                    onClick = { onSelectLanguage(AppLanguage.EN) }
                )
            }
        }

        M3ExpressiveGroup(title = if (isFrench) "MÉTÉO (ÉCRAN D'ACCUEIL)" else "WEATHER (HOME SCREEN)") {
            var inputCity by remember(weatherCity) { mutableStateOf(weatherCity) }
            val currentPreview = weatherInfo?.let { "${it.cityName} : ${it.displayString(temperatureUnit)}" } ?: "$weatherCity (chargement…)"

            Text(
                text = if (isFrench) "Affichage météo en haut à gauche : $currentPreview"
                       else "Weather preview on top-left: $currentPreview",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color(0xFFBDC1C6)
            )

            SettingsGlassInputField(
                label = if (isFrench) "Ville pour la météo" else "City for weather",
                value = inputCity,
                onValueChange = { inputCity = it },
                placeholder = "Paris"
            )

            SettingsPrimaryActionButton(
                label = if (isFrench) "Enregistrer la ville" else "Save City",
                onClick = { onUpdateWeatherCity(inputCity.trim()) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isFrench) "Unité de température" else "Temperature Unit",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                color = Color(0xFF8E918F)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GoogleTvRadioRow(
                    title = "Celsius (°C)",
                    subtitle = if (isFrench) "Affichage de la température en degrés Celsius" else "Displays temperature in degrees Celsius",
                    isSelected = temperatureUnit == TemperatureUnit.CELSIUS,
                    onClick = { onSelectTemperatureUnit(TemperatureUnit.CELSIUS) }
                )
                GoogleTvRadioRow(
                    title = "Fahrenheit (°F)",
                    subtitle = if (isFrench) "Affichage de la température en degrés Fahrenheit" else "Displays temperature in degrees Fahrenheit",
                    isSelected = temperatureUnit == TemperatureUnit.FAHRENHEIT,
                    onClick = { onSelectTemperatureUnit(TemperatureUnit.FAHRENHEIT) }
                )
            }
        }

        M3ExpressiveGroup(title = stringResource(R.string.section_app_drawer)) {
            GoogleTvSwitchRow(
                title = stringResource(R.string.appearance_show_hidden_apps),
                subtitle = if (isFrench) "Afficher ou masquer les applications cachées dans la grille" else "Display hidden applications inside the apps catalog",
                icon = AbyssIconType.APPS_GRID,
                isChecked = showHiddenApps,
                onClick = { onToggleShowHiddenApps(!showHiddenApps) }
            )
        }
    }
}

@Composable
private fun SettingsSidebarActionButton(
    label: String,
    subLabel: String,
    icon: AbyssIconType,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(100),
        label = "actionBtnScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFFE8EAED) else Color.Transparent)
            .border(
                1.dp,
                if (isFocused) Color.White else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val badgeBg = if (isFocused) Color(0xFFCBD5E1) else Color(0xFF1E2025)
        val iconColor = if (isFocused) Color(0xFF131314) else Color(0xFF8E918F)
        val textColor = if (isFocused) Color(0xFF131314) else Color(0xFFE8EAED)
        val subColor = if (isFocused) Color(0xFF444746) else Color(0xFF8E918F)

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            AbyssIcon(
                type = icon,
                color = iconColor,
                size = 18.dp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = textColor
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = subColor
            )
        }

        AbyssIcon(
            type = AbyssIconType.EXTERNAL_LINK,
            color = iconColor,
            size = 14.dp
        )
    }
}

@Composable
private fun SettingsHeaderGearButton(
    onClick: () -> Unit,
    contentDescription: String
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFFE8EAED) else Color(0xFF1E2025))
            .border(1.dp, if (isFocused) Color.White else Color(0xFF2C3038), RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AbyssIcon(
            type = AbyssIconType.SETTINGS,
            color = if (isFocused) Color(0xFF131314) else Color(0xFFBDC1C6),
            size = 18.dp
        )
    }
}

@Composable
private fun SettingsNavTab(
    label: String,
    subLabel: String,
    icon: AbyssIconType,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(100),
        label = "navTabScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color(0xFFE8EAED)
                    isSelected -> Color(0xFF1E2025)
                    else -> Color.Transparent
                }
            )
            .border(
                1.dp,
                when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFF2C3038)
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && !isSelected) {
                    onClick()
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val badgeBg = when {
            isFocused -> Color(0xFFCBD5E1)
            isSelected -> Color(0xFF282B32)
            else -> Color(0xFF181A1F)
        }
        val iconColor = when {
            isFocused -> Color(0xFF131314)
            isSelected -> Color(0xFF8AB4F8)
            else -> Color(0xFF8E918F)
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            AbyssIcon(
                type = icon,
                color = iconColor,
                size = 18.dp
            )
        }

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = when {
                    isFocused -> Color(0xFF131314)
                    isSelected -> Color.White
                    else -> Color(0xFFBDC1C6)
                }
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = when {
                    isFocused -> Color(0xFF444746)
                    isSelected -> Color(0xFF8E918F)
                    else -> Color(0xFF5E6368)
                }
            )
        }
    }
}

@Composable
private fun SettingsBackButton(
    label: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFFE8EAED) else Color(0xFF1E2025))
            .border(1.dp, if (isFocused) Color.White else Color(0xFF2C3038), RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AbyssIcon(
            type = AbyssIconType.ARROW_LEFT,
            color = if (isFocused) Color(0xFF131314) else Color(0xFFE8EAED),
            size = 14.dp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = if (isFocused) Color(0xFF131314) else Color(0xFFE8EAED)
        )
    }
}

@Composable
private fun SettingsGlassInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = Color(0xFF8E918F)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF16181D))
                .border(1.dp, if (isFocused) Color(0xFF8AB4F8) else Color(0xFF2C3038), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                color = Color(0xFF5E6368),
                                fontSize = 13.sp
                            )
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun SettingsPrimaryActionButton(
    label: String,
    isLoading: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(100),
        label = "primaryBtnScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFFE8EAED) else Color(0xFF1A73E8))
            .border(
                1.dp,
                if (isFocused) Color.White else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(enabled = !isLoading) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = if (isFocused) Color(0xFF1A73E8) else Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = if (isFocused) Color(0xFF1A73E8) else Color.White
            )
        }
    }
}

@Composable
private fun GoogleTvRadioRow(
    title: String,
    subtitle: String,
    icon: AbyssIconType? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1.0f,
        animationSpec = tween(100),
        label = "radioRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color(0xFFE8EAED)
                    isSelected -> Color(0xFF252932)
                    else -> Color(0xFF181A1F)
                }
            )
            .border(
                1.dp,
                when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFF384357)
                    else -> Color(0xFF252932)
                },
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isFocused -> Color(0xFFCBD5E1)
                            isSelected -> Color(0xFF1E2430)
                            else -> Color(0xFF1E2025)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AbyssIcon(
                    type = icon,
                    color = when {
                        isFocused -> Color(0xFF131314)
                        isSelected -> Color(0xFF8AB4F8)
                        else -> Color(0xFF8E918F)
                    },
                    size = 18.dp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = if (isFocused) Color(0xFF131314) else Color.White
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = if (isFocused) Color(0xFF444746) else Color(0xFF8E918F)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    2.dp,
                    when {
                        isFocused && isSelected -> Color(0xFF1A73E8)
                        isFocused -> Color(0xFF8E918F)
                        isSelected -> Color(0xFF8AB4F8)
                        else -> Color(0xFF5E6368)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) Color(0xFF1A73E8) else Color(0xFF8AB4F8))
                )
            }
        }
    }
}

@Composable
private fun GoogleTvSwitchRow(
    title: String,
    subtitle: String,
    icon: AbyssIconType? = null,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1.0f,
        animationSpec = tween(100),
        label = "switchRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color(0xFFE8EAED)
                    isChecked -> Color(0xFF252932)
                    else -> Color(0xFF181A1F)
                }
            )
            .border(
                1.dp,
                when {
                    isFocused -> Color.White
                    isChecked -> Color(0xFF384357)
                    else -> Color(0xFF252932)
                },
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isFocused -> Color(0xFFCBD5E1)
                            isChecked -> Color(0xFF1E2430)
                            else -> Color(0xFF1E2025)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AbyssIcon(
                    type = icon,
                    color = when {
                        isFocused -> Color(0xFF131314)
                        isChecked -> Color(0xFF8AB4F8)
                        else -> Color(0xFF8E918F)
                    },
                    size = 18.dp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = if (isFocused) Color(0xFF131314) else Color.White
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = if (isFocused) Color(0xFF444746) else Color(0xFF8E918F)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(38.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    when {
                        isChecked && isFocused -> Color(0xFF1A73E8)
                        isChecked -> Color(0xFF8AB4F8)
                        isFocused -> Color(0xFFBDC1C6)
                        else -> Color(0xFF3C4043)
                    }
                )
                .padding(horizontal = 2.dp),
            contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isChecked && isFocused) Color.White else if (isChecked) Color(0xFF131314) else Color.White)
            )
        }
    }
}
