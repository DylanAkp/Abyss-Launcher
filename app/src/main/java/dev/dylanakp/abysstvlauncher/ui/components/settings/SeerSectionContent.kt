package dev.dylanakp.abysstvlauncher.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import dev.dylanakp.abysstvlauncher.model.SeerConfig
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIcon
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType

@Composable
fun SeerSectionContent(
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
        SettingsGroup(title = stringResource(R.string.settings_section_seer)) {
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
            SettingsGroup(title = stringResource(R.string.section_library_status)) {
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
                        text = stringResource(R.string.seer_status_connected),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = Color(0xFF34D399)
                    )
                }
            }
        }
    }
}
