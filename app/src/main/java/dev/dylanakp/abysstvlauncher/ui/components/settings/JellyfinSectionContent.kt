package dev.dylanakp.abysstvlauncher.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.JellyfinConfig
import dev.dylanakp.abysstvlauncher.model.PlayerPreference
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType

@Composable
fun JellyfinSectionContent(
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
        SettingsGroup(title = stringResource(R.string.section_connection)) {
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
            SettingsGroup(title = stringResource(R.string.section_library_status)) {
                val stats = stringResource(R.string.jf_stats_template, config.syncedMoviesCount, config.syncedSeriesCount)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF14291F))
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

        SettingsGroup(title = stringResource(R.string.section_player_prefs)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoogleTvRadioRow(
                    title = stringResource(R.string.player_abyss_title),
                    subtitle = stringResource(R.string.player_abyss_subtitle),
                    icon = AbyssIconType.PLAY,
                    isSelected = config.playerPreference == PlayerPreference.ABYSS_INTERNAL,
                    onClick = { onSelectPlayerPreference(PlayerPreference.ABYSS_INTERNAL) }
                )
                GoogleTvRadioRow(
                    title = stringResource(R.string.player_external_title),
                    subtitle = stringResource(R.string.player_external_subtitle),
                    icon = AbyssIconType.EXTERNAL_LINK,
                    isSelected = config.playerPreference == PlayerPreference.EXTERNAL_PLAYER,
                    onClick = { onSelectPlayerPreference(PlayerPreference.EXTERNAL_PLAYER) }
                )
                GoogleTvRadioRow(
                    title = stringResource(R.string.player_always_ask_title),
                    subtitle = stringResource(R.string.player_always_ask_subtitle),
                    icon = AbyssIconType.SETTINGS,
                    isSelected = config.playerPreference == PlayerPreference.SYSTEM_CHOOSER,
                    onClick = { onSelectPlayerPreference(PlayerPreference.SYSTEM_CHOOSER) }
                )
            }
        }
    }
}
