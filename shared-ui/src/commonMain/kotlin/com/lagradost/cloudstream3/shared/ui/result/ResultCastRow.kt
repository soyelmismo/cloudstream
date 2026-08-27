package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors

import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Horizontal scrollable row of Cast & Crew actor cards with circular avatars,
 * actor names, and role/character information.
 */
@Composable
fun ResultCastRow(
    actors: List<ActorData>,
    onSearchClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (actors.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = stringResource(Res.string.cast),
            style = MaterialTheme.typography.subtitle1.copy(
                fontWeight = FontWeight.Bold,
                color = CloudStreamColors.TextPrimary
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(actors) { actorData ->
                ActorCard(actorData = actorData, onSearchClick = onSearchClick)
            }
        }
    }
}

@Composable
fun ActorCard(
    actorData: ActorData,
    onSearchClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val actorName = actorData.actor.name
    val actorImage = actorData.actor.image
    val roleName = actorData.roleString ?: actorData.role?.name

    Card(
        shape = RoundedCornerShape(8.dp),
        backgroundColor = CloudStreamColors.SurfaceVariant.copy(alpha = 0.6f),
        elevation = 0.dp,
        modifier = modifier.width(96.dp).let {
            if (onSearchClick != null) {
                it.clickable { onSearchClick(actorName) }
            } else it
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            // Circular Avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(CloudStreamColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (!actorImage.isNullOrBlank()) {
                    AsyncImage(
                        url = actorImage,
                        contentDescription = actorName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = actorName.take(1).uppercase(),
                        style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Actor Name
            Text(
                text = actorName,
                style = MaterialTheme.typography.caption.copy(
                    fontWeight = FontWeight.Medium,
                    color = CloudStreamColors.TextPrimary,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // Character / Role Name
            if (!roleName.isNullOrBlank()) {
                Text(
                    text = roleName,
                    style = MaterialTheme.typography.caption.copy(
                        color = CloudStreamColors.TextMuted,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
