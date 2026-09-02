package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.StreamFormatting
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.MediaSourceInfo

@Composable
fun VersionRow(
    sources: List<MediaSourceInfo>?,
    selectedSourceId: String?,
    onSelectVersion: (MediaSourceInfo) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    preferences: UserPreferences? = null,
    fontSize: TextUnit? = null,
    title: String = stringResource(R.string.versions),
    showTitle: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    cardWidth: Dp = 300.dp,
    cardHeight: Dp = 175.dp,
) {
    val nonNullSources = sources.orEmpty()
    val isVisible = isLoading || nonNullSources.isNotEmpty()

    val aioSources = remember(nonNullSources) {
        nonNullSources.filterNot { StreamFormatting.isLocalSource(it) }
    }
    val localSources = remember(nonNullSources) {
        nonNullSources.filter { StreamFormatting.isLocalSource(it) }
    }
    val hasLocal = localSources.isNotEmpty()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val displayedSources = if (hasLocal && selectedTab == 1) {
        localSources
    } else if (aioSources.isNotEmpty()) {
        aioSources
    } else {
        localSources
    }

    val isCurrentTabLocal = hasLocal && selectedTab == 1
    val effectiveFontSize = fontSize ?: 11.5.sp

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
    ) {
        val state = rememberLazyListState()
        val firstCardFocus = remember { FocusRequester() }
        val focusRequester = remember { FocusRequester() }
        val tabFocusRequester = remember { FocusRequester() }
        var position by rememberInt()

        val currentOnSelectVersion by rememberUpdatedState(onSelectVersion)

        // Focus the first card when loading finishes
        LaunchedEffect(isLoading, displayedSources.size) {
            if (!isLoading && displayedSources.isNotEmpty()) {
                firstCardFocus.tryRequestFocus()
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties {
                    onEnter = {
                        focusRequester.tryRequestFocus()
                    }
                },
        ) {
            if (showTitle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemRowTitle(title)
                    if (isLoading) {
                        Text(
                            text = stringResource(R.string.loading_versions),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (displayedSources.isNotEmpty()) {
                        Text(
                            text = "(${displayedSources.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (hasLocal) {
                val tabs = listOf(
                    TabDetails(StringStringProvider("${stringResource(R.string.tab_aiostreams)} (${aioSources.size})")),
                    TabDetails(StringStringProvider("${stringResource(R.string.tab_local)} (${localSources.size})")),
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    tabs = tabs,
                    onClick = {
                        selectedTab = it
                        position = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .focusRequester(tabFocusRequester),
                )
            }

            if (isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .padding(horizontal = horizontalPadding)
                        .focusProperties {
                            up = if (hasLocal) tabFocusRequester else FocusRequester.Default
                        },
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.border,
                        modifier = Modifier.size(48.dp),
                    )
                }
            } else {
                LazyRow(
                    state = state,
                    horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .focusRestorer(firstCardFocus)
                        .focusRequester(focusRequester),
                ) {
                    itemsIndexed(
                        items = displayedSources,
                        key = { _, item -> item.id ?: item.name ?: item.hashCode() },
                    ) { index, source ->
                        val shouldAnimate = remember { index < 4 }
                        var visible by remember { mutableStateOf(!shouldAnimate) }
                        if (shouldAnimate) {
                            LaunchedEffect(Unit) {
                                delay((index * 35L).coerceAtMost(200L))
                                visible = true
                            }
                        }
                        val scale by animateFloatAsState(
                            targetValue = if (visible) 1f else 0.85f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "cardScale",
                        )
                        val alpha by animateFloatAsState(
                            targetValue = if (visible) 1f else 0f,
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                            label = "cardAlpha",
                        )

                        val cardModifier = remember(index, position) {
                            if (index == 0) {
                                Modifier.focusRequester(firstCardFocus)
                            } else {
                                Modifier
                            }
                        }

                        val isSelected = remember(source.id, selectedSourceId) {
                            source.id != null && source.id == selectedSourceId
                        }

                        val onClick = remember(source) {
                            {
                                position = index
                                currentOnSelectVersion(source)
                            }
                        }

                        VersionCard(
                            source = source,
                            isSelected = isSelected,
                            isLocal = isCurrentTabLocal,
                            fontSize = effectiveFontSize,
                            onClick = onClick,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            modifier = cardModifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            },
                        )
                    }
                }
            }
        }
    }
}
