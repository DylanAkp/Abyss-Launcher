package dev.dylanakp.abysstvlauncher.ui.components.settings

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import dev.dylanakp.abysstvlauncher.remote.RemoteBridgeService
import dev.dylanakp.abysstvlauncher.remote.TvCommandController
import dev.dylanakp.abysstvlauncher.ui.components.AbyssIconType
import org.koin.compose.koinInject

@Composable
fun RemoteSectionContent() {
    val context = LocalContext.current
    val preferences: LauncherPreferences = koinInject()
    val controller: TvCommandController = koinInject()
    val remoteState by controller.tvStateFlow.collectAsState()

    var serverEnabled by remember { mutableStateOf(preferences.remoteServerEnabled) }
    var currentPin by remember { mutableStateOf(controller.getPairingPin()) }
    var pairedCount by remember { mutableIntStateOf(controller.getPairedDeviceCount()) }

    val localIp = remember(serverEnabled) { controller.getLocalIpAddress() ?: "abyss-tv.local" }
    val pairUrl = remember(localIp, currentPin) {
        "http://$localIp:${TvCommandController.DEFAULT_PORT}/?pair=$currentPin"
    }
    val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = pairUrl) {
        value = withContext(Dispatchers.Default) {
            generateQrBitmap(pairUrl, 360)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsGroup(title = stringResource(R.string.remote_server_group)) {
            GoogleTvSwitchRow(
                title = stringResource(R.string.remote_enable_title),
                subtitle = stringResource(R.string.remote_enable_sub),
                icon = AbyssIconType.REMOTE,
                isChecked = serverEnabled,
                onClick = {
                    val next = !serverEnabled
                    serverEnabled = next
                    preferences.remoteServerEnabled = next
                    if (next) RemoteBridgeService.start(context)
                    else RemoteBridgeService.stop(context)
                }
            )
        }

        if (serverEnabled) {
            SettingsGroup(title = stringResource(R.string.remote_pair_group)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val qrBmp = qrBitmap
                    if (qrBmp != null) {
                        Box(
                            modifier = Modifier
                                .size(164.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBmp.asImageBitmap(),
                                contentDescription = "QR Pair",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.remote_scan_qr_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBDC1C6)
                        )
                        Text(
                            text = "http://$localIp:${TvCommandController.DEFAULT_PORT}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF8AB4F8)
                        )
                        Text(
                            text = stringResource(R.string.remote_pin_label, currentPin),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = stringResource(
                                R.string.remote_paired_count,
                                pairedCount,
                                remoteState.connectedClients
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF9AA0A6)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsPrimaryActionButton(
                            label = stringResource(R.string.remote_regen_pin),
                            onClick = { currentPin = controller.regeneratePin() }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsPrimaryActionButton(
                            label = stringResource(R.string.remote_revoke_all),
                            onClick = {
                                controller.revokeAllDevices()
                                currentPin = controller.getPairingPin()
                                pairedCount = controller.getPairedDeviceCount()
                            }
                        )
                    }
                }
            }

            SettingsGroup(title = stringResource(R.string.remote_permissions_group)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoogleTvSwitchRow(
                        title = stringResource(R.string.remote_notif_access_title),
                        subtitle = if (remoteState.notificationAccessEnabled) {
                            stringResource(R.string.remote_notif_access_on)
                        } else {
                            stringResource(R.string.remote_notif_access_off)
                        },
                        icon = AbyssIconType.AUDIO_SPEAKER,
                        isChecked = remoteState.notificationAccessEnabled,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )

                    GoogleTvSwitchRow(
                        title = stringResource(R.string.remote_acc_access_title),
                        subtitle = if (remoteState.accessibilityEnabled) {
                            stringResource(R.string.remote_acc_access_on)
                        } else {
                            stringResource(R.string.remote_acc_access_off)
                        },
                        icon = AbyssIconType.SETTINGS,
                        isChecked = remoteState.accessibilityEnabled,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
    return runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }.getOrNull()
}
