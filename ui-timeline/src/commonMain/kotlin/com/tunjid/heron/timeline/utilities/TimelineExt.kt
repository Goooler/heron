/*
 *    Copyright 2024 Adetunji Dahunsi
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tunjid.heron.timeline.utilities

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.tunjid.heron.data.core.models.Timeline
import com.tunjid.heron.data.core.models.TimelineItem
import com.tunjid.heron.data.core.types.PostId
import com.tunjid.heron.timeline.ui.withQuotingPostIdPrefix
import heron.ui_timeline.generated.resources.Res
import heron.ui_timeline.generated.resources.likes
import heron.ui_timeline.generated.resources.media
import heron.ui_timeline.generated.resources.posts
import heron.ui_timeline.generated.resources.replies
import heron.ui_timeline.generated.resources.videos
import org.jetbrains.compose.resources.stringResource

@Composable
fun Timeline.displayName() = when (this) {
    is Timeline.Home.Feed -> name
    is Timeline.Home.Following -> name
    is Timeline.Home.List -> name
    is Timeline.Profile -> when (type) {
        Timeline.Profile.Type.Media -> stringResource(Res.string.media)
        Timeline.Profile.Type.Posts -> stringResource(Res.string.posts)
        Timeline.Profile.Type.Likes -> stringResource(Res.string.likes)
        Timeline.Profile.Type.Replies -> stringResource(Res.string.replies)
        Timeline.Profile.Type.Videos -> stringResource(Res.string.videos)
    }.capitalize(Locale.current)
}

val Timeline.Presentation.cardSize
    get() = when (this) {
        Timeline.Presentation.Text.WithEmbed -> 340.dp
        Timeline.Presentation.Media.Condensed -> 160.dp
        Timeline.Presentation.Media.Expanded -> 340.dp
    }

val Timeline.Presentation.timelineHorizontalPadding
    get() = when (this) {
        Timeline.Presentation.Text.WithEmbed -> 8.dp
        Timeline.Presentation.Media.Condensed -> 8.dp
        Timeline.Presentation.Media.Expanded -> 0.dp
    }

val Timeline.Presentation.actionIconSize
    get() = when (this) {
        Timeline.Presentation.Text.WithEmbed -> 16.dp
        Timeline.Presentation.Media.Condensed -> 0.dp
        Timeline.Presentation.Media.Expanded -> 24.dp
    }

internal val Timeline.Presentation.icon
    get() = when (this) {
        Timeline.Presentation.Text.WithEmbed -> Icons.AutoMirrored.Rounded.Article
        Timeline.Presentation.Media.Condensed -> Icons.Rounded.Dashboard
        Timeline.Presentation.Media.Expanded -> Icons.Rounded.Splitscreen
    }

val Timeline.sharedElementPrefix get() = sourceId

fun Timeline.sharedElementPrefix(
    quotingPostId: PostId?
) = sourceId.withQuotingPostIdPrefix(
    quotingPostId = quotingPostId
)

fun LazyStaggeredGridState.pendingOffsetFor(
    item: TimelineItem
) = layoutInfo
    .visibleItemsInfo
    .first { it.key == item.id }
    .offset
    .y
    .toFloat()