package dev.dylanakp.abysstvlauncher.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dylanakp.abysstvlauncher.model.TvAppInfo
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.ui.components.AppCard

@Composable
fun AppHorizontalRow(
    title: String,
    apps: List<TvAppInfo>,
    onAppClick: (TvAppInfo) -> Unit,
    onAppLongClick: (TvAppInfo) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Boolean)? = null,
    rowState: LazyListState = rememberLazyListState()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = AbyssTextPrimary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
        )
        LazyRow(
            state = rowState,
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = apps,
                key = { _, it -> it.packageName },
                contentType = { _, _ -> "app_card" }
            ) { index, app ->
                AppCard(
                    app = app,
                    focusRequester = if (index == 0) firstItemFocusRequester else null,
                    onNavigateUp = onNavigateUp,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }
    }
}

@Composable
fun AppsGridView(
    allApps: List<TvAppInfo>,
    hiddenApps: List<TvAppInfo>,
    showHiddenApps: Boolean,
    gridState: LazyGridState,
    onAppClick: (TvAppInfo) -> Unit,
    onAppLongClick: (TvAppInfo) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onTopRowFocused: (() -> Unit)? = null
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalAvailableWidth = maxWidth - 64.dp
        val minCardWidth = 170.dp
        val spacing = 14.dp
        val columnsCount = maxOf(1, ((totalAvailableWidth + spacing) / (minCardWidth + spacing)).toInt())

        val list = remember(allApps, hiddenApps, showHiddenApps) {
            if (showHiddenApps) (allApps + hiddenApps) else allApps
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = minCardWidth),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 72.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = list,
                key = { _, it -> it.packageName },
                contentType = { _, _ -> "app_card" }
            ) { index, app ->
                val isTopRow = index < columnsCount
                AppCard(
                    app = app,
                    focusRequester = if (index == 0) firstItemFocusRequester else null,
                    onNavigateUp = if (isTopRow) onNavigateUp else null,
                    onFocus = if (isTopRow) onTopRowFocused else null,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }
    }
}
