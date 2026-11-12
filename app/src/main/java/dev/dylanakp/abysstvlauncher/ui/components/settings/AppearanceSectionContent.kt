package dev.dylanakp.abysstvlauncher.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.WeatherInfo
import dev.dylanakp.abysstvlauncher.model.AppLanguage
import dev.dylanakp.abysstvlauncher.model.TemperatureUnit
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import dev.dylanakp.abysstvlauncher.ui.components.WeatherIcon

@Composable
fun AppearanceSectionContent(
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsGroup(title = stringResource(R.string.appearance_language_label)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoogleTvRadioRow(
                    title = "Auto (Système / System)",
                    subtitle = stringResource(R.string.lang_auto_subtitle),
                    icon = AbyssIconType.GLOBE,
                    isSelected = currentLanguage == AppLanguage.AUTO,
                    onClick = { onSelectLanguage(AppLanguage.AUTO) }
                )
                GoogleTvRadioRow(
                    title = "Français",
                    subtitle = stringResource(R.string.lang_fr_subtitle),
                    icon = AbyssIconType.GLOBE,
                    isSelected = currentLanguage == AppLanguage.FR,
                    onClick = { onSelectLanguage(AppLanguage.FR) }
                )
                GoogleTvRadioRow(
                    title = "English",
                    subtitle = stringResource(R.string.lang_en_subtitle),
                    icon = AbyssIconType.GLOBE,
                    isSelected = currentLanguage == AppLanguage.EN,
                    onClick = { onSelectLanguage(AppLanguage.EN) }
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_section_weather)) {
            var inputCity by remember(weatherCity) { mutableStateOf(weatherCity) }
            val currentPreview = weatherInfo?.let { "${it.cityName} : ${it.displayString(temperatureUnit)}" } ?: "$weatherCity"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (weatherInfo != null) {
                    WeatherIcon(
                        type = weatherInfo.weatherIconType,
                        color = Color.White,
                        size = 14.dp
                    )
                }
                Text(
                    text = stringResource(R.string.weather_preview_format, currentPreview),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color(0xFFBDC1C6)
                )
            }

            SettingsGlassInputField(
                label = stringResource(R.string.weather_city_label),
                value = inputCity,
                onValueChange = { inputCity = it },
                placeholder = "Paris"
            )

            SettingsPrimaryActionButton(
                label = stringResource(R.string.weather_city_save_btn),
                onClick = { onUpdateWeatherCity(inputCity.trim()) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.weather_unit_label),
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
                    subtitle = stringResource(R.string.weather_celsius_sub),
                    isSelected = temperatureUnit == TemperatureUnit.CELSIUS,
                    onClick = { onSelectTemperatureUnit(TemperatureUnit.CELSIUS) }
                )
                GoogleTvRadioRow(
                    title = "Fahrenheit (°F)",
                    subtitle = stringResource(R.string.weather_fahrenheit_sub),
                    isSelected = temperatureUnit == TemperatureUnit.FAHRENHEIT,
                    onClick = { onSelectTemperatureUnit(TemperatureUnit.FAHRENHEIT) }
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.section_app_drawer)) {
            GoogleTvSwitchRow(
                title = stringResource(R.string.appearance_show_hidden_apps),
                subtitle = stringResource(R.string.appearance_show_hidden_apps_sub),
                icon = AbyssIconType.APPS_GRID,
                isChecked = showHiddenApps,
                onClick = { onToggleShowHiddenApps(!showHiddenApps) }
            )
        }
    }
}
