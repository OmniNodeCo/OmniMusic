package co.omnimusic.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import co.omnimusic.app.theme.OmniMusicTheme
import co.omnimusic.app.ui.AlbumScreen
import co.omnimusic.app.ui.Artwork
import co.omnimusic.app.ui.ArtistScreen
import co.omnimusic.app.ui.HomeScreen
import co.omnimusic.app.ui.LibraryScreen
import co.omnimusic.app.ui.SearchScreen
import co.omnimusic.app.ui.TrackRow
import co.omnimusic.core.AppEnvironment

/**
 * The root composable, shared verbatim by the Android and desktop targets.
 *
 * It also owns the clock: the playback engine is tick-free by design and expects its host to call
 * [AppModel.tick] on every frame, which is what the [LaunchedEffect] below does.
 */
@Composable
fun App(environment: AppEnvironment, model: AppModel = remember { AppModel(environment) }) {
    OmniMusicTheme {
        LaunchedEffect(Unit) {
            var previous = -1L
            while (true) {
                withFrameNanos { now ->
                    if (previous >= 0L) model.tick((now - previous) / 1_000_000L)
                    previous = now
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = { TopBar(model) },
                bottomBar = { MiniPlayer(model) },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    // Read once: `screen` is a delegated property, so the compiler will not smart
                    // cast `model.screen` across the branches below.
                    val screen = model.screen
                    when (screen) {
                        Screen.Home -> HomeScreen(model)
                        Screen.Search -> SearchScreen(model)
                        Screen.Library -> LibraryScreen(model)
                        is Screen.Album -> AlbumScreen(model, screen.id)
                        is Screen.Artist -> ArtistScreen(model)
                    }
                }
            }
        }

        if (model.nowPlayingExpanded) {
            NowPlayingPanel(model)
        }

        model.notice?.let { text ->
            // `align` is a BoxScope modifier, so the overlay needs a Box to hang from; the
            // theme/surface above is not one.
            Box(Modifier.fillMaxSize()) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
                    action = { TextButton(onClick = model::dismissNotice) { Text("Dismiss") } },
                ) { Text(text) }
            }
        }
    }
}

@Composable
private fun TopBar(model: AppModel) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "OmniMusic",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = model.playbackSourceName(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            NavTab("Home", model.screen is Screen.Home) { model.goHome() }
            NavTab("Search", model.screen is Screen.Search) { model.goSearch() }
            NavTab("Library", model.screen is Screen.Library) { model.goLibrary() }
        }
    }
}

@Composable
private fun NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** The persistent bar above the bottom edge. Tapping it opens the full now-playing view. */
@Composable
private fun MiniPlayer(model: AppModel) {
    val state = model.playState
    if (state.isEmpty) return
    val track = state.track ?: return

    Surface(tonalElevation = 6.dp) {
        Column {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(
                Modifier.fillMaxWidth().clickable { model.nowPlayingExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(seed = track.album?.id ?: track.id, initials = track.title, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${track.artistName} · ${state.positionLabel}/${state.durationLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = model::previous) { Text("⏮", fontSize = 18.sp) }
                IconButton(onClick = model::togglePlayPause) {
                    Text(if (state.isPlaying) "⏸" else "▶", fontSize = 20.sp)
                }
                IconButton(onClick = model::next) { Text("⏭", fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun NowPlayingPanel(model: AppModel) {
    val state = model.playState
    val track = state.track

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 12.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Now playing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.nowPlayingExpanded = false }) { Text("Close") }
            }

            if (track == null) {
                Message2("Nothing is playing yet.")
                return@Column
            }

            Artwork(
                seed = track.album?.id ?: track.id,
                initials = track.album?.title ?: track.title,
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(track.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { model.openArtist(track.artist.id, track.artistName) },
            )

            Slider(
                value = state.progress,
                onValueChange = { model.seekTo((it * state.durationMillis).toLong()) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(state.positionLabel, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(state.durationLabel, style = MaterialTheme.typography.labelSmall)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = model::toggleShuffle) {
                    Text(if (state.shuffleEnabled) "Shuffle: on" else "Shuffle: off")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = model::previous) { Text("⏮", fontSize = 20.sp) }
                FilledIconButton(onClick = model::togglePlayPause, modifier = Modifier.size(56.dp)) {
                    Text(if (state.isPlaying) "⏸" else "▶", fontSize = 22.sp)
                }
                IconButton(onClick = model::next) { Text("⏭", fontSize = 20.sp) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { model.cycleRepeat() }) { Text(state.repeatMode.label) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.volume,
                    onValueChange = model::setVolume,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }

            val lyricLines = model.lyrics?.lines.orEmpty()
            if (lyricLines.isNotEmpty()) {
                val activeLine = model.currentLyricIndex()
                Text("Lyrics", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(lyricLines.size) { index ->
                        val line = lyricLines[index]
                        Text(
                            text = line.text.ifBlank { " " },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index == activeLine) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (index == activeLine) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            } else if (model.lyrics?.plainLines.orEmpty().isNotEmpty()) {
                Text("Lyrics", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(model.lyrics?.plainLines.orEmpty().size) { index ->
                        Text(
                            text = model.lyrics?.plainLines.orEmpty()[index],
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }

            Text(
                text = "Up next (${state.queueIndex + 1}/${state.queueSize})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            val queue = model.queue()
            LazyColumn(Modifier.weight(1f)) {
                items(queue.size) { index ->
                    TrackRow(
                        index = index + 1,
                        track = queue[index],
                        isCurrent = index == state.queueIndex,
                        onClick = { model.play(queue, index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Message2(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
