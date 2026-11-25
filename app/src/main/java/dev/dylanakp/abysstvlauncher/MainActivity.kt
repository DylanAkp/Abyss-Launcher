package dev.dylanakp.abysstvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import dev.dylanakp.abysstvlauncher.model.AppLanguage
import dev.dylanakp.abysstvlauncher.remote.RemoteBridgeService
import dev.dylanakp.abysstvlauncher.remote.TvCommandController
import dev.dylanakp.abysstvlauncher.theme.AbyssTVLauncherTheme
import dev.dylanakp.abysstvlauncher.ui.LauncherViewModel
import dev.dylanakp.abysstvlauncher.ui.components.JellyfinMessageOverlay
import dev.dylanakp.abysstvlauncher.ui.focus.LocalFocusSlots
import dev.dylanakp.abysstvlauncher.ui.focus.rememberFocusSlotHolder
import dev.dylanakp.abysstvlauncher.ui.main.MainScreen
import dev.dylanakp.abysstvlauncher.ui.main.PlaybackPreparingOverlay
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModel()
    private val remoteController: TvCommandController by inject()
    private val preferences: LauncherPreferences by inject()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refreshInstalledApps(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        )
        if (preferences.remoteServerEnabled) {
            RemoteBridgeService.start(this)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    remoteController.playMediaRequests.collect { item ->
                        viewModel.onMediaItemClicked(this@MainActivity, item)
                    }
                }
                launch {
                    remoteController.nextEpisodeRequests.collect { nextEp ->
                        viewModel.playNextEpisodeInPlayer(nextEp)
                    }
                }
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val configuration = LocalConfiguration.current
            val context = LocalContext.current

            androidx.compose.runtime.LaunchedEffect(uiState.language) {
                val locales = when (uiState.language) {
                    AppLanguage.FR -> LocaleListCompat.forLanguageTags("fr")
                    AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
                    AppLanguage.AUTO -> LocaleListCompat.getEmptyLocaleList()
                }
                if (AppCompatDelegate.getApplicationLocales() != locales) {
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }

            val localizedContext = remember(uiState.language, configuration) {
                val targetLocale = when (uiState.language) {
                    AppLanguage.FR -> java.util.Locale.FRENCH
                    AppLanguage.EN -> java.util.Locale.ENGLISH
                    AppLanguage.AUTO -> null
                }
                if (targetLocale != null) {
                    val conf = Configuration(configuration)
                    conf.setLocale(targetLocale)
                    context.createConfigurationContext(conf)
                } else {
                    context
                }
            }

            val focusSlots = rememberFocusSlotHolder()
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalFocusSlots provides focusSlots
            ) {
                AbyssTVLauncherTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MainScreen(viewModel = viewModel)
                            if (uiState.isPreparingPlayback) {
                                PlaybackPreparingOverlay(modifier = Modifier.matchParentSize())
                            }
                            JellyfinMessageOverlay(
                                message = uiState.jellyfinDisplayMessage,
                                onDismiss = { viewModel.dismissJellyfinDisplayMessage() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 28.dp, end = 36.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
            if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        remoteController.attachActivity(this)
        viewModel.onActivityResumed()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.action == Intent.ACTION_MAIN) {
            viewModel.handleHomePressed()
        }
    }

    override fun onStop() {
        super.onStop()
        remoteController.detachActivity(this)
        viewModel.onActivityStopped()
    }
}
