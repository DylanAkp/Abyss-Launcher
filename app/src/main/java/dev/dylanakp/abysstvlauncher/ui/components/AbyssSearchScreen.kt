package dev.dylanakp.abysstvlauncher.ui.components

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.model.MediaCategory
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.model.TvAppInfo
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderFocus
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssPillGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssTextMuted
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import kotlinx.coroutines.delay

@Composable
fun AbyssSearchScreen(
    query: String,
    movies: List<MediaItem>,
    series: List<MediaItem>,
    tmdbRecommendations: List<MediaItem>,
    apps: List<TvAppInfo>,
    searchResults: List<MediaItem> = emptyList(),
    isSearching: Boolean = false,
    searchHistory: List<String> = emptyList(),
    searchInputFocusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit = {},
    onSelectSearchHistory: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onRemoveSearchHistoryItem: (String) -> Unit = {},
    onNavigateUpToTopBar: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onAppClick: (TvAppInfo) -> Unit,
    onAppLongClick: (TvAppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val firstResultFocusRequester = remember { FocusRequester() }
    val clearButtonFocusRequester = remember { FocusRequester() }
    var isInputFocused by remember { mutableStateOf(false) }

    // Focus the search field and summon the integrated TV keyboard on entry
    LaunchedEffect(Unit) {
        delay(80L)
        try {
            searchInputFocusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {}
    }

    val trimmedQuery = query.trim()

    val matchedMovies = remember(searchResults, trimmedQuery) {
        if (trimmedQuery.isBlank()) emptyList()
        else searchResults.filter { it.category == MediaCategory.MOVIE }
    }

    val matchedSeries = remember(searchResults, trimmedQuery) {
        if (trimmedQuery.isBlank()) emptyList()
        else searchResults.filter { it.category == MediaCategory.SERIES }
    }

    val matchedSeer = remember(searchResults, trimmedQuery) {
        if (trimmedQuery.isBlank()) emptyList()
        else searchResults.filter { it.category == MediaCategory.SEER_REQUEST || it.id.startsWith("seer_") }
    }

    val matchedApps = remember(apps, trimmedQuery) {
        if (trimmedQuery.isBlank()) emptyList()
        else {
            val q = trimmedQuery.lowercase()
            apps.filter { it.label.lowercase().contains(q) }
        }
    }

    val totalResultsCount = matchedMovies.size + matchedSeries.size + matchedSeer.size + matchedApps.size

    val topResultRowKey = remember(
        trimmedQuery.isBlank(),
        searchHistory.isNotEmpty(),
        matchedMovies.isNotEmpty(),
        matchedSeries.isNotEmpty(),
        matchedSeer.isNotEmpty(),
        matchedApps.isNotEmpty(),
        tmdbRecommendations.isNotEmpty()
    ) {
        if (trimmedQuery.isBlank()) {
            if (searchHistory.isNotEmpty()) "searchHistoryRow"
            else if (tmdbRecommendations.isNotEmpty()) "searchSuggestedRow"
            else "searchPopularMoviesRow"
        } else {
            when {
                matchedMovies.isNotEmpty() -> "searchMoviesRow"
                matchedSeries.isNotEmpty() -> "searchSeriesRow"
                matchedSeer.isNotEmpty() -> "searchSeerRow"
                matchedApps.isNotEmpty() -> "searchAppsRow"
                else -> ""
            }
        }
    }

    val borderColor by animateColorAsState(
        targetValue = if (isInputFocused) Color.White else AbyssBorderHairline,
        animationSpec = tween(120),
        label = "searchInputBorder"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AbyssVoid)
            .padding(top = 70.dp)
    ) {
        // Top Search Input Bar (integrated TV keyboard)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AbyssSurfaceGlass)
                    .border(1.dp, borderColor, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AbyssIcon(
                        type = AbyssIconType.SEARCH,
                        color = if (isInputFocused || trimmedQuery.isNotBlank()) Color.White else AbyssTextSecondary,
                        size = 15.dp
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AbyssTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                if (query.isNotBlank()) onSubmitQuery(query)
                                try { firstResultFocusRequester.requestFocus() } catch (_: Exception) {}
                            },
                            onDone = {
                                keyboardController?.hide()
                                if (query.isNotBlank()) onSubmitQuery(query)
                                try { firstResultFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchInputFocusRequester)
                            .onFocusChanged {
                                isInputFocused = it.isFocused
                                if (it.isFocused) {
                                    keyboardController?.show()
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            keyboardController?.hide()
                                            onNavigateUpToTopBar()
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            keyboardController?.hide()
                                            try {
                                                firstResultFocusRequester.requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            if (query.isNotEmpty()) {
                                                try {
                                                    clearButtonFocusRequester.requestFocus()
                                                    true
                                                } catch (_: Exception) {
                                                    false
                                                }
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_placeholder),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = AbyssTextSecondary
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }

            if (query.isNotEmpty()) {
                var clearFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .focusRequester(clearButtonFocusRequester)
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (clearFocused) Color.White else AbyssPillGlass)
                        .border(
                            1.dp,
                            if (clearFocused) AbyssBorderFocus else AbyssBorderHairline,
                            RoundedCornerShape(50)
                        )
                        .onFocusChanged { clearFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        onNavigateUpToTopBar()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        try {
                                            searchInputFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        try {
                                            firstResultFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .clickable {
                            onQueryChange("")
                            try {
                                searchInputFocusRequester.requestFocus()
                                keyboardController?.show()
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AbyssIcon(
                            type = AbyssIconType.CLOSE,
                            color = if (clearFocused) Color.Black else AbyssTextPrimary,
                            size = 12.dp
                        )
                        Text(
                            text = stringResource(R.string.search_clear_history),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = if (clearFocused) Color.Black else AbyssTextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live Results Area
        Box(modifier = Modifier.fillMaxSize()) {
            val navigateBackToSearchInput: () -> Unit = {
                try {
                    searchInputFocusRequester.requestFocus()
                } catch (_: Exception) {
                    onNavigateUpToTopBar()
                }
            }

            if (trimmedQuery.isBlank()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (searchHistory.isNotEmpty()) {
                        item(key = "searchHistoryRow") {
                            val isTop = topResultRowKey == "searchHistoryRow"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_recent_title),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        color = AbyssTextPrimary
                                    )
                                    var isClearFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isClearFocused) Color(0x33FFFFFF) else Color.Transparent)
                                            .border(
                                                1.dp,
                                                if (isClearFocused) Color.White else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .onFocusChanged { isClearFocused = it.isFocused }
                                            .onPreviewKeyEvent { event ->
                                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                    when (event.nativeKeyEvent.keyCode) {
                                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                                            navigateBackToSearchInput()
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                            try {
                                                                firstResultFocusRequester.requestFocus()
                                                                true
                                                            } catch (_: Exception) {
                                                                false
                                                            }
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                            .clickable { onClearSearchHistory() }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.search_clear_history),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            color = if (isClearFocused) Color.White else AbyssTextMuted
                                        )
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 32.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(searchHistory, key = { _, h -> "hist_$h" }) { idx, h ->
                                        RecentSearchPill(
                                            queryText = h,
                                            focusRequester = if (isTop && idx == 0) firstResultFocusRequester else null,
                                            onNavigateUp = if (isTop) navigateBackToSearchInput else null,
                                            onClick = { onSelectSearchHistory(h) },
                                            onRemove = { onRemoveSearchHistoryItem(h) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (tmdbRecommendations.isNotEmpty()) {
                        item(key = "searchSuggestedRow") {
                            val isTop = topResultRowKey == "searchSuggestedRow"
                            MediaRow(
                                title = stringResource(R.string.search_suggested),
                                items = tmdbRecommendations.take(20),
                                firstItemFocusRequester = if (isTop) firstResultFocusRequester else null,
                                onNavigateUp = if (isTop) navigateBackToSearchInput else null,
                                onItemClick = onMediaClick,
                                onItemFocused = {}
                            )
                        }
                    }
                    if (movies.isNotEmpty()) {
                        item(key = "searchPopularMoviesRow") {
                            val isTop = topResultRowKey == "searchPopularMoviesRow"
                            MediaRow(
                                title = stringResource(R.string.search_popular_movies),
                                items = movies.take(25),
                                firstItemFocusRequester = if (isTop) firstResultFocusRequester else null,
                                onNavigateUp = if (isTop) navigateBackToSearchInput else null,
                                onItemClick = onMediaClick,
                                onItemFocused = {}
                            )
                        }
                    }
                    if (series.isNotEmpty()) {
                        item(key = "searchPopularSeriesRow") {
                            MediaRow(
                                title = stringResource(R.string.search_popular_series),
                                items = series.take(25),
                                onItemClick = onMediaClick,
                                onItemFocused = {}
                            )
                        }
                    }
                }
            } else if (totalResultsCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AbyssIcon(
                            type = AbyssIconType.SEARCH,
                            color = AbyssTextMuted,
                            size = 32.dp
                        )
                        Text(
                            text = stringResource(R.string.search_no_results, trimmedQuery),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = AbyssTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (matchedMovies.isNotEmpty()) {
                        item(key = "searchMoviesRow") {
                            val isTop = topResultRowKey == "searchMoviesRow"
                            MediaRow(
                                title = stringResource(R.string.search_results_movies, matchedMovies.size),
                                items = matchedMovies,
                                firstItemFocusRequester = if (isTop) firstResultFocusRequester else null,
                                onNavigateUp = if (isTop) navigateBackToSearchInput else null,
                                onItemClick = onMediaClick,
                                onItemFocused = {}
                            )
                        }
                    }

                    if (matchedSeries.isNotEmpty()) {
                        item(key = "searchSeriesRow") {
                            val isTop = topResultRowKey == "searchSeriesRow"
                            MediaRow(
                                title = stringResource(R.string.search_results_series, matchedSeries.size),
                                items = matchedSeries,
                                firstItemFocusRequester = if (isTop) firstResultFocusRequester else null,
                                onNavigateUp = if (isTop) navigateBackToSearchInput else null,
                                onItemClick = onMediaClick,
                                onItemFocused = {}
                            )
                        }
                    }

                    if (matchedSeer.isNotEmpty()) {
                        item(key = "searchSeerRow") {
                            val isTop = topResultRowKey == "searchSeerRow"
                            MediaRow(
                                title = stringResource(R.string.search_results_seer, matchedSeer.size),
                                items = matchedSeer,
                                firstItemFocusRequester = if (isTop) firstResultFocusRequester else null,
                                onNavigateUp = if (isTop) navigateBackToSearchInput else null,
                                onItemClick = onMediaClick,
                                onItemFocused = {}
                            )
                        }
                    }

                    if (matchedApps.isNotEmpty()) {
                        item(key = "searchAppsRow") {
                            val isTop = topResultRowKey == "searchAppsRow"
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.search_results_apps, matchedApps.size),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    ),
                                    color = AbyssTextPrimary,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(matchedApps.size, key = { matchedApps[it].packageName }) { idx ->
                                        val app = matchedApps[idx]
                                        AppCard(
                                            app = app,
                                            focusRequester = if (isTop && idx == 0) firstResultFocusRequester else null,
                                            onNavigateUp = if (isTop) navigateBackToSearchInput else null,
                                            onClick = { onAppClick(app) },
                                            onLongClick = { onAppLongClick(app) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentSearchPill(
    queryText: String,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isFocused) Color.White else AbyssPillGlass)
            .border(
                1.dp,
                if (isFocused) AbyssBorderFocus else AbyssBorderHairline,
                RoundedCornerShape(50)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onNavigateUp != null) {
                                onNavigateUp.invoke()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onRemove
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AbyssIcon(
                type = AbyssIconType.SEARCH,
                color = if (isFocused) Color.Black else AbyssTextSecondary,
                size = 12.dp
            )
            Text(
                text = queryText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = if (isFocused) Color.Black else AbyssTextPrimary
            )
        }
    }
}
