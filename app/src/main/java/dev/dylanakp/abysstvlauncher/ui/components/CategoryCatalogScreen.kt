package dev.dylanakp.abysstvlauncher.ui.components

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import dev.dylanakp.abysstvlauncher.model.MediaItem
import dev.dylanakp.abysstvlauncher.theme.AbyssBorderHairline
import dev.dylanakp.abysstvlauncher.theme.AbyssPillGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssSurfaceGlass
import dev.dylanakp.abysstvlauncher.theme.AbyssTextMuted
import dev.dylanakp.abysstvlauncher.theme.AbyssTextPrimary
import dev.dylanakp.abysstvlauncher.theme.AbyssTextSecondary
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import kotlinx.coroutines.delay

data class CatalogSelection(
    val title: String,
    val items: List<MediaItem>,
    val isLive: Boolean = false
)

@Composable
fun CategoryCatalogScreen(
    selection: CatalogSelection,
    onBack: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    var filterQuery by remember { mutableStateOf("") }
    val firstItemFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredItems = remember(selection.items, filterQuery) {
        if (filterQuery.isBlank()) {
            selection.items
        } else {
            val q = filterQuery.lowercase().trim()
            selection.items.filter {
                it.localizedTitle.lowercase().contains(q) ||
                    it.title.lowercase().contains(q) ||
                    it.localizedGroupTitle.lowercase().contains(q) ||
                    it.year.contains(q)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100L)
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AbyssVoid)
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Back Button
                CatalogBackButton(
                    onClick = onBack,
                    focusRequester = backButtonFocusRequester
                )

                Column {
                    Text(
                        text = selection.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = AbyssTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.catalog_total_items, filteredItems.size),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = AbyssTextSecondary
                    )
                }
            }

            // Quick In-Catalog Filter Field
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AbyssPillGlass)
                    .border(1.dp, AbyssBorderHairline, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AbyssIcon(
                        type = AbyssIconType.SEARCH,
                        color = AbyssTextSecondary,
                        size = 14.dp
                    )
                    BasicTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AbyssTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { keyboardController?.hide() },
                            onDone = { keyboardController?.hide() }
                        ),
                        decorationBox = { innerTextField ->
                            if (filterQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.catalog_filter_placeholder),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = AbyssTextMuted
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Grid View of Full Category Catalog
        if (selection.isLive) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = filteredItems,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> "live_channel" }
                ) { index, item ->
                    LiveChannelCard(
                        item = item,
                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                        onClick = { onItemClick(item) },
                        onFocus = {}
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = filteredItems,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> "media_poster" }
                ) { index, item ->
                    MediaPosterCard(
                        item = item,
                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                        onClick = { onItemClick(item) },
                        onFocus = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogBackButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(50))
            .background(if (isFocused) Color.White else AbyssPillGlass)
            .border(1.dp, if (isFocused) Color.White else AbyssBorderHairline, RoundedCornerShape(50))
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                ) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AbyssIcon(
                type = AbyssIconType.ARROW_LEFT,
                color = if (isFocused) Color.Black else Color.White,
                size = 14.dp
            )
            Text(
                text = "Retour",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = if (isFocused) Color.Black else Color.White
            )
        }
    }
}
