package co.omnimusic.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.omnimusic.app.AppModel
import co.omnimusic.core.model.Load

/** Landing screen: trending tracks, albums and browsable genres. */
@Composable
fun HomeScreen(model: AppModel) {
    when (val result = model.home) {
        null -> LoadingIndicator()
        is Load.Failure -> Column {
            Message("Could not load the home feed: ${result.error}")
            Button(onClick = model::loadHome, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Retry")
            }
        }

        is Load.Success -> {
            val feed = result.value
            LazyColumn(Modifier.fillMaxSize()) {
                item { SectionHeader("Trending on ${model.playbackSourceName()}") }
                items(feed.trendingTracks, key = { it.id }) { track ->
                    TrackRow(
                        index = feed.trendingTracks.indexOf(track) + 1,
                        track = track,
                        isCurrent = model.playState.track?.id == track.id,
                        onClick = { model.play(feed.trendingTracks, feed.trendingTracks.indexOf(track)) },
                        onLongClick = { model.openArtist(track.artist.id, track.artistName) },
                    )
                }
                if (feed.newAlbums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                            items(feed.newAlbums, key = { it.id }) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { model.openAlbum(album.id, album.title) },
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            }
                        }
                    }
                }
                if (feed.genres.isNotEmpty()) {
                    item { SectionHeader("Genres") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                            items(feed.genres, key = { it.id }) { genre ->
                                GenreChip(genre = genre, onClick = { model.runSearch(genre.name) })
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
fun SearchScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = model.searchQuery,
            onValueChange = model::runSearch,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = { Text("Search tracks, albums, artists") },
            singleLine = true,
        )
        when (val result = model.searchResults) {
            null -> Message("Type to search ${model.playbackSourceName()}.")
            is Load.Failure -> Message("Search failed: ${result.error}")
            is Load.Success -> {
                val search = result.value
                if (search.isEmpty) {
                    Message("No results for “${search.query}”.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (search.tracks.items.isNotEmpty()) {
                            item { SectionHeader("Tracks") }
                            items(search.tracks.items, key = { "t${it.id}" }) { track ->
                                TrackRow(
                                    index = search.tracks.items.indexOf(track) + 1,
                                    track = track,
                                    isCurrent = model.playState.track?.id == track.id,
                                    onClick = { model.play(search.tracks.items, search.tracks.items.indexOf(track)) },
                                    onLongClick = { model.startRadio(track.id) },
                                )
                            }
                        }
                        if (search.albums.items.isNotEmpty()) {
                            item { SectionHeader("Albums") }
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                                    items(search.albums.items, key = { "a${it.id}" }) { album ->
                                        AlbumCard(
                                            album = album,
                                            onClick = { model.openAlbum(album.id, album.title) },
                                            modifier = Modifier.padding(end = 12.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (search.artists.items.isNotEmpty()) {
                            item { SectionHeader("Artists") }
                            items(search.artists.items, key = { "r${it.id}" }) { artist ->
                                Row(
                                    Modifier.fillMaxWidth().clickable(onClick = { model.openArtist(artist.id, artist.name) })
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AvatarBadge(artist.name, Modifier.size(40.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(artist.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(model: AppModel, albumId: String) {
    when (val result = model.album) {
        null -> LoadingIndicator()
        is Load.Failure -> Message("Could not load the album: ${result.error}")
        is Load.Success -> {
            val (album, tracks) = result.value
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(seed = album.id, initials = album.title, Modifier.size(120.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(album.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = album.artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = listOfNotNull(
                                    album.releaseDate,
                                    "${tracks.size} tracks",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Button(onClick = { model.play(tracks) }) { Text("Play") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { model.saveAsPlaylist(album.title, tracks) }) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        index = tracks.indexOf(track) + 1,
                        track = track,
                        isCurrent = model.playState.track?.id == track.id,
                        onClick = { model.play(tracks, tracks.indexOf(track)) },
                        onLongClick = { model.openArtist(track.artist.id, track.artistName) },
                    )
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
fun ArtistScreen(model: AppModel) {
    when (val result = model.artist) {
        null -> LoadingIndicator()
        is Load.Failure -> Message("Could not load the artist: ${result.error}")
        is Load.Success -> {
            val (artist, top) = result.value
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarBadge(artist.name, Modifier.size(72.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(artist.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("${top.size} top tracks", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { model.play(top) }) { Text("Play all") }
                        }
                    }
                }
                items(top, key = { it.id }) { track ->
                    TrackRow(
                        index = top.indexOf(track) + 1,
                        track = track,
                        isCurrent = model.playState.track?.id == track.id,
                        onClick = { model.play(top, top.indexOf(track)) },
                        onLongClick = { model.openAlbum(track.album?.id.orEmpty(), track.album?.title.orEmpty()) },
                    )
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
fun LibraryScreen(model: AppModel) {
    val playlists = model.filteredPlaylists()
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = model.libraryFilter,
            onValueChange = { model.libraryFilter = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Filter playlists") },
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = model.newPlaylistName,
                onValueChange = { model.newPlaylistName = it },
                modifier = Modifier.weight(1f),
                label = { Text("New playlist") },
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = model::submitNewPlaylist,
                enabled = model.newPlaylistName.isNotBlank(),
            ) { Text("Create") }
        }
        LazyColumn(Modifier.weight(1f)) {
            item { SectionHeader("Playlists") }
            if (playlists.isEmpty()) {
                item { Message("No playlists match.") }
            }
            items(playlists, key = { it.id }) { playlist ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${playlist.tracks.size} tracks · ${playlist.durationLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { model.playPlaylist(playlist) }) { Text("Play") }
                        TextButton(onClick = { model.deletePlaylist(playlist.id) }) { Text("Delete") }
                    }
                    playlist.tracks.take(3).forEachIndexed { index, track ->
                        TrackRow(
                            index = index + 1,
                            track = track,
                            isCurrent = model.playState.track?.id == track.id,
                            onClick = { model.play(playlist.tracks, index) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}
