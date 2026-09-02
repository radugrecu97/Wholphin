package com.github.damontecres.wholphin.ui.playback.overlay

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.findActivity
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * State holding touch gesture interactions during video playback (volume, brightness, seek scrub, double-tap seek).
 */
class PlaybackTouchState {
    var isScrubbing by mutableStateOf(false)
    var scrubTargetMs by mutableLongStateOf(0L)
    var scrubDeltaMs by mutableLongStateOf(0L)

    var isVolumeChanging by mutableStateOf(false)
    var currentVolumePercent by mutableFloatStateOf(0f)

    var isBrightnessChanging by mutableStateOf(false)
    var currentBrightnessPercent by mutableFloatStateOf(0f)

    var doubleTapSeekDuration by mutableLongStateOf(0L)
    var doubleTapSeekForward by mutableStateOf(true)
}

@Composable
fun rememberPlaybackTouchState(): PlaybackTouchState = remember { PlaybackTouchState() }

enum class DragMode {
    NONE,
    HORIZONTAL_SEEK,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME,
}

/**
 * Modifier detecting single-tap (toggle OSD), double-tap seek (+/- 10s), and swipe gestures (brightness, volume, scrub).
 */
fun Modifier.playbackTouchGestures(
    player: Player,
    touchState: PlaybackTouchState,
    seekBackDuration: Duration,
    seekForwardDuration: Duration,
    onSingleTap: () -> Unit,
    onInteraction: () -> Unit,
    context: Context,
): Modifier = pointerInput(player, seekBackDuration, seekForwardDuration) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val activity = context.findActivity()

    val touchSlop = viewConfiguration.touchSlop
    val doubleTapTimeout = 300L

    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startTime = System.currentTimeMillis()
        val startPosition = down.position
        val containerWidth = size.width.toFloat()
        val containerHeight = size.height.toFloat()

        var dragMode = DragMode.NONE
        var totalDragDistance = Offset.Zero
        var hasMovedPastSlop = false

        val initialPlayerPosition = player.currentPosition
        val playerDuration = player.duration.coerceAtLeast(1L)

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        var initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        var initialBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        if (initialBrightness < 0f) {
            val sysBrightness =
                try {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                } catch (ex: Exception) {
                    128
                }
            initialBrightness = (sysBrightness / 255f).coerceIn(0.01f, 1f)
        }

        var pointerUp = false

        while (!pointerUp) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }

            if (change == null || change.isConsumed || !change.pressed) {
                pointerUp = true
                break
            }

            val currentPosition = change.position
            val delta = currentPosition - startPosition
            totalDragDistance = delta

            if (!hasMovedPastSlop) {
                if (abs(delta.x) > touchSlop || abs(delta.y) > touchSlop) {
                    hasMovedPastSlop = true
                    dragMode = if (abs(delta.x) > abs(delta.y)) {
                        DragMode.HORIZONTAL_SEEK
                    } else if (startPosition.x < containerWidth * 0.5f) {
                        DragMode.VERTICAL_BRIGHTNESS
                    } else {
                        DragMode.VERTICAL_VOLUME
                    }
                }
            }

            if (hasMovedPastSlop) {
                change.consume()
                onInteraction()

                when (dragMode) {
                    DragMode.HORIZONTAL_SEEK -> {
                        touchState.isScrubbing = true
                        val scrubRangeMs = (playerDuration.coerceAtMost(90 * 60 * 1000L)).toFloat()
                        val deltaPercent = (delta.x / containerWidth).coerceIn(-1f, 1f)
                        val deltaMs = (deltaPercent * scrubRangeMs).toLong()
                        val targetMs = (initialPlayerPosition + deltaMs).coerceIn(0L, playerDuration)

                        touchState.scrubTargetMs = targetMs
                        touchState.scrubDeltaMs = deltaMs
                    }

                    DragMode.VERTICAL_BRIGHTNESS -> {
                        touchState.isBrightnessChanging = true
                        val deltaPercent = (-delta.y / (containerHeight * 0.75f))
                        val newBrightness = (initialBrightness + deltaPercent).coerceIn(0.01f, 1.0f)
                        touchState.currentBrightnessPercent = newBrightness

                        activity?.let { act ->
                            val lp = act.window.attributes
                            lp.screenBrightness = newBrightness
                            act.window.attributes = lp
                        }
                    }

                    DragMode.VERTICAL_VOLUME -> {
                        touchState.isVolumeChanging = true
                        val deltaPercent = (-delta.y / (containerHeight * 0.75f))
                        val volumeStep = (deltaPercent * maxVolume).roundToInt()
                        val newVolume = (initialVolume + volumeStep).coerceIn(0, maxVolume)
                        touchState.currentVolumePercent = newVolume.toFloat() / maxVolume.toFloat()

                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                    }

                    DragMode.NONE -> {}
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime

        if (hasMovedPastSlop) {
            when (dragMode) {
                DragMode.HORIZONTAL_SEEK -> {
                    if (touchState.isScrubbing) {
                        player.seekTo(touchState.scrubTargetMs)
                    }
                }
                else -> {}
            }
            touchState.isScrubbing = false
            touchState.isVolumeChanging = false
            touchState.isBrightnessChanging = false
        } else if (elapsed < 500) {
            // Tap detected
            val currentTime = System.currentTimeMillis()
            val timeSinceLastTap = currentTime - lastTapTime
            val distanceSinceLastTap = (startPosition - lastTapPosition).getDistance()

            if (timeSinceLastTap < doubleTapTimeout && distanceSinceLastTap < touchSlop * 4) {
                // Double tap
                lastTapTime = 0L
                onInteraction()
                if (startPosition.x < containerWidth * 0.4f) {
                    player.seekBack(seekBackDuration)
                    touchState.doubleTapSeekForward = false
                    touchState.doubleTapSeekDuration -= seekBackDuration.inWholeMilliseconds
                } else if (startPosition.x > containerWidth * 0.6f) {
                    player.seekForward(seekForwardDuration)
                    touchState.doubleTapSeekForward = true
                    touchState.doubleTapSeekDuration += seekForwardDuration.inWholeMilliseconds
                } else {
                    onSingleTap()
                }
            } else {
                // First tap: record and schedule single tap action
                lastTapTime = currentTime
                lastTapPosition = startPosition
                onSingleTap()
            }
        }
    }
}

/**
 * Overlay HUDs for brightness, volume, seek gesture scrubbing, and double-tap seek indicators.
 */
@Composable
fun PlaybackGestureHudOverlay(
    touchState: PlaybackTouchState,
    player: Player,
    modifier: Modifier = Modifier,
) {
    // Reset double-tap seek badge after timeout
    LaunchedEffect(touchState.doubleTapSeekDuration) {
        if (touchState.doubleTapSeekDuration != 0L) {
            delay(1.seconds)
            touchState.doubleTapSeekDuration = 0L
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Brightness HUD
        AnimatedVisibility(
            visible = touchState.isBrightnessChanging,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp),
        ) {
            GestureVerticalBarHud(
                icon = R.string.fa_sun,
                percent = touchState.currentBrightnessPercent,
                label = "${(touchState.currentBrightnessPercent * 100).roundToInt()}%",
            )
        }

        // Volume HUD
        AnimatedVisibility(
            visible = touchState.isVolumeChanging,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
        ) {
            val volPercent = touchState.currentVolumePercent
            val icon = when {
                volPercent <= 0f -> R.string.fa_volume_xmark
                volPercent < 0.5f -> R.string.fa_volume_low
                else -> R.string.fa_volume_high
            }
            GestureVerticalBarHud(
                icon = icon,
                percent = volPercent,
                label = "${(volPercent * 100).roundToInt()}%",
            )
        }

        // Scrubbing HUD
        AnimatedVisibility(
            visible = touchState.isScrubbing,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val targetSec = (touchState.scrubTargetMs / 1000).seconds
            val deltaSec = (touchState.scrubDeltaMs / 1000)
            val deltaSign = if (deltaSec >= 0) "+${deltaSec}s" else "${deltaSec}s"

            Box(
                modifier =
                    Modifier
                        .background(AppColors.TransparentBlack75, shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = LocalContext.current.resources.formatDuration(targetSec),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "[$deltaSign]",
                        fontSize = 14.sp,
                        color = if (deltaSec >= 0) MaterialTheme.colorScheme.border else Color.Red,
                    )
                }
            }
        }

        // Double-tap Seek HUD
        if (touchState.doubleTapSeekDuration != 0L) {
            val isForward = touchState.doubleTapSeekForward
            val durationSec = abs(touchState.doubleTapSeekDuration / 1000)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.4f)
                        .align(if (isForward) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .background(AppColors.TransparentBlack50, shape = CircleShape)
                            .padding(20.dp),
                ) {
                    Text(
                        text = if (isForward) stringResource(R.string.fa_forward) else stringResource(R.string.fa_backward),
                        fontFamily = FontAwesome,
                        fontSize = 24.sp,
                        color = Color.White,
                    )
                    Text(
                        text = "${durationSec}s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureVerticalBarHud(
    icon: Int,
    percent: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .background(AppColors.TransparentBlack75, shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(icon),
            fontFamily = FontAwesome,
            fontSize = 18.sp,
            color = Color.White,
        )
        // Vertical Progress Bar
        Box(
            modifier =
                Modifier
                    .width(6.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(percent.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.border),
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}
