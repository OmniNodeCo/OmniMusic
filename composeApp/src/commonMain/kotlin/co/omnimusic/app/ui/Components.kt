package co.omnimusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.omnimusic.app.theme.accentFor
import coil3.compose.AsyncImage
import co.omnimusic.core.model.Album
import co.omnimusic.core.model.Genre
import co.omnimusic.core.model.Track

/**
 * Cover art, with a deterministic gradient behind it.
 *
 * The gradient and initials are not a "no image loader yet" placeholder: they stay underneath the
 * real artwork, so they are what shows while a request is in flight, when a release genuinely has no
 * cover, and when the machine is offline. Painting them first is also what stops a grid of cards
 * from shifting height as images arrive.
 *
 * [imageUrl] is optional because the catalog only sometimes has one — an artist inside a track has
 * no picture, and a hand-made playlist entry may have neither album nor art.
 */
@Composable
fun Artwork(
    seed: String,
    initials: String,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val accent = accentFor(seed)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun TrackRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // `clickable` has no long-press callback; that is `combinedClickable`.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isCurrent) "▸" else index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Artwork(
            seed = track.album?.id ?: track.id,
            initials = track.album?.title ?: track.title,
            imageUrl = track.album?.imageUrl ?: track.artist.imageUrl,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(track.title)
                    if (track.explicit) append("  E")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = listOfNotNull(track.artistName, track.album?.title).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = track.durationLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        Artwork(
            seed = album.id,
            initials = album.title,
            imageUrl = album.imageUrl,
            modifier = Modifier.size(140.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artistName.ifBlank { album.releaseDate ?: "" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GenreChip(genre: Genre, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accentFor(genre.id).copy(alpha = 0.18f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = genre.name,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A round artist avatar.
 *
 * Same arrangement as [Artwork]: the tinted initial stays underneath, so it is the placeholder, the
 * no-picture case and the offline case. Artists nested inside a track carry no picture at all, so
 * [imageUrl] is routinely null rather than exceptional.
 *
 * There is deliberately no inner padding. The badge used to inset its content, which only made
 * sense while it sized itself to its initial; both call sites now pass an explicit size, and an
 * inset would leave `matchParentSize()` matching the padded box rather than the circle. Callers
 * that want a badge without a fixed size should pass one.
 */
@Composable
fun AvatarBadge(name: String, imageUrl: String? = null, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(accentFor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
