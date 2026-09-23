package dev.mewdeko.mobile.feature.guilddetail.home

import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.model.MusicStatus
import dev.mewdeko.mobile.core.model.PlayerState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * The currently playing track, with artwork, a live progress bar, an
 * equalizer while playing, and the queue length. Opens the music player.
 */
@Composable
fun NowPlayingCard(music: MusicStatus, reduced: Boolean, onOpen: () -> Unit) {
    val queued = music.currentTrack ?: return
    val track = queued.track
    val artwork = track.artworkUri?.takeIf { it.isNotBlank() }
    val channel = music.channelName?.takeIf { it.isNotBlank() }
    val title = track.title.ifBlank { "Unknown track" }
    val author = track.author?.takeIf { it.isNotBlank() }
    val playing = music.state.isPlaying
    val paused = music.state == PlayerState.PAUSED
    val label = buildString {
        append("Now playing, ").append(title)
        if (author != null) append(" by ").append(author)
        if (channel != null) append(", in ").append(channel)
    }
    val clamp = fontScaleClamp()

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Box {
            if (artwork != null) {
                val blurred = Build.VERSION.SDK_INT >= 31
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (blurred) 0.35f else 0.2f,
                    modifier = Modifier
                        .matchParentSize()
                        .then(if (blurred) Modifier.blur(32.dp) else Modifier),
                )
            }
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp * clamp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artwork != null) {
                        AsyncImage(
                            model = artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val content = LocalContentColor.current
                    Text(
                        text = when {
                            playing -> "Now playing"
                            paused -> "Paused"
                            else -> "Idle"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = content.copy(alpha = 0.8f),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (author != null) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (channel != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = channel,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val total = HomeSeries.parseClock(track.duration) ?: 0L
                    if (total > 0 && track.isLiveStream != true) {
                        val elapsed = rememberElapsedSeconds(
                            position = HomeSeries.parseClock(music.position?.displayValue) ?: 0L,
                            syncedAt = music.position?.syncedAt,
                            playing = playing,
                            total = total,
                        )
                        Column(modifier = Modifier.clearAndSetSemantics {}) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { elapsed.toFloat() / total },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = content,
                                trackColor = content.copy(alpha = 0.15f),
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = HomeSeries.clock(elapsed),
                                    style = MaterialTheme.typography.labelSmall.tabular(),
                                    color = content.copy(alpha = 0.8f),
                                )
                                Text(
                                    text = HomeSeries.clock(total),
                                    style = MaterialTheme.typography.labelSmall.tabular(),
                                    color = content.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val content = LocalContentColor.current
                    if (playing) {
                        EqualizerBars(color = content, reduced = reduced)
                    } else if (paused) {
                        Icon(
                            Icons.Default.PauseCircle,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    val queueSize = music.queue?.size ?: 0
                    if (queueSize > 0) {
                        Surface(
                            shape = CircleShape,
                            color = content.copy(alpha = 0.12f),
                            contentColor = content,
                        ) {
                            Text(
                                text = "+$queueSize queued",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Three bouncing bars that signal live playback.
 *
 * Under reduced motion they hold still at fixed heights and no infinite
 * transition is started.
 */
@Composable
fun EqualizerBars(color: Color, reduced: Boolean, modifier: Modifier = Modifier) {
    val heights: List<State<Float>> = if (reduced) {
        listOf(
            remember { mutableFloatStateOf(0.4f) },
            remember { mutableFloatStateOf(0.8f) },
            remember { mutableFloatStateOf(0.6f) },
        )
    } else {
        val transition = rememberInfiniteTransition(label = "equalizer")
        listOf(450, 600, 750).map { duration ->
            transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
                label = "equalizerBar$duration",
            )
        }
    }
    Canvas(modifier = modifier.size(24.dp, 20.dp)) {
        val barWidth = 4.dp.toPx()
        val bottom = size.height - barWidth / 2f
        val usable = size.height - barWidth
        heights.forEachIndexed { index, state ->
            val x = barWidth / 2f + index * (size.width - barWidth) / 2f
            drawLine(
                color = color,
                start = Offset(x, bottom),
                end = Offset(x, bottom - usable * state.value),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Playback position in seconds, advancing once a second while [playing].
 *
 * The server reports a position and when it was sampled; elapsed time since
 * then is added on top, clamped to the track length.
 */
@Composable
fun rememberElapsedSeconds(position: Long, syncedAt: Instant?, playing: Boolean, total: Long): Long {
    val base = remember(position, syncedAt) { syncedAt ?: Instant.now() }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(playing, position, syncedAt) {
        now = Instant.now()
        while (playing) {
            delay(1_000)
            now = Instant.now()
        }
    }
    val drift = if (playing) Duration.between(base, now).seconds.coerceAtLeast(0) else 0L
    return (position + drift).coerceIn(0L, total)
}
