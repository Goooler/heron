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

package com.tunjid.heron.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.JoinFull
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunjid.heron.data.core.models.FeedGenerator
import com.tunjid.heron.data.core.models.LinkTarget
import com.tunjid.heron.data.core.models.ListMember
import com.tunjid.heron.data.core.models.Post
import com.tunjid.heron.data.core.models.Profile
import com.tunjid.heron.data.core.models.ProfileWithViewerState
import com.tunjid.heron.data.core.models.Trend
import com.tunjid.heron.data.core.models.path
import com.tunjid.heron.data.utilities.path
import com.tunjid.heron.scaffold.navigation.NavigationAction
import com.tunjid.heron.scaffold.navigation.composePostDestination
import com.tunjid.heron.scaffold.navigation.pathDestination
import com.tunjid.heron.scaffold.navigation.postDestination
import com.tunjid.heron.scaffold.navigation.profileDestination
import com.tunjid.heron.scaffold.scaffold.PaneScaffoldState
import com.tunjid.heron.search.ui.FeedGeneratorSearchResult
import com.tunjid.heron.search.ui.PostSearchResult
import com.tunjid.heron.search.ui.ProfileSearchResult
import com.tunjid.heron.search.ui.SuggestedStarterPack
import com.tunjid.heron.search.ui.Trend
import com.tunjid.heron.search.ui.avatarSharedElementKey
import com.tunjid.heron.tiling.TilingState
import com.tunjid.heron.tiling.tiledItems
import com.tunjid.heron.timeline.ui.avatarSharedElementKey
import com.tunjid.heron.timeline.ui.post.PostInteractionsBottomSheet
import com.tunjid.heron.timeline.ui.post.PostInteractionsSheetState.Companion.rememberPostInteractionState
import com.tunjid.heron.timeline.ui.profile.ProfileWithViewerState
import com.tunjid.heron.ui.Tab
import com.tunjid.heron.ui.Tabs
import com.tunjid.heron.ui.TabsState.Companion.rememberTabsState
import com.tunjid.heron.ui.UiTokens
import com.tunjid.heron.ui.UiTokens.bottomNavAndInsetPaddingValues
import com.tunjid.heron.ui.tabIndex
import com.tunjid.tiler.compose.PivotedTilingEffect
import com.tunjid.treenav.compose.MovableElementSharedTransitionScope
import heron.feature_search.generated.resources.Res
import heron.feature_search.generated.resources.discover_feeds
import heron.feature_search.generated.resources.feeds
import heron.feature_search.generated.resources.latest
import heron.feature_search.generated.resources.people
import heron.feature_search.generated.resources.starter_packs
import heron.feature_search.generated.resources.suggested_accounts
import heron.feature_search.generated.resources.top
import heron.feature_search.generated.resources.trending_title
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SearchScreen(
    paneScaffoldState: PaneScaffoldState,
    state: State,
    actions: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val postInteractionState = rememberPostInteractionState()

    Column(
        modifier = modifier
    ) {
        Spacer(Modifier.height(UiTokens.toolbarHeight + UiTokens.statusBarHeight))
        val pagerState = rememberPagerState {
            4
        }
        val onProfileClicked: (ProfileWithViewerState) -> Unit = remember {
            { profileWithViewerState ->
                actions(
                    Action.Navigate.To(
                        profileDestination(
                            profile = profileWithViewerState.profile,
                            avatarSharedElementKey = profileWithViewerState
                                .profile
                                .searchProfileAvatarSharedElementKey(),
                            referringRouteOption = NavigationAction.ReferringRouteOption.ParentOrCurrent
                        )
                    )
                )
            }
        }
        val onViewerStateClicked: (ProfileWithViewerState) -> Unit =
            remember(state.signedInProfile?.did) {
                { profileWithViewerState ->
                    state.signedInProfile?.did?.let {
                        actions(
                            Action.ToggleViewerState(
                                signedInProfileId = it,
                                viewedProfileId = profileWithViewerState.profile.did,
                                following = profileWithViewerState.viewerState?.following,
                                followedBy = profileWithViewerState.viewerState?.followedBy,
                            )
                        )
                    }
                }
            }
        val onProfileSearchResultClicked: (SearchResult.OfProfile) -> Unit = remember {
            { profileSearchResult ->
                actions(
                    Action.Navigate.To(
                        profileDestination(
                            profile = profileSearchResult.profileWithViewerState.profile,
                            avatarSharedElementKey = profileSearchResult.avatarSharedElementKey(),
                            referringRouteOption = NavigationAction.ReferringRouteOption.ParentOrCurrent
                        )
                    )
                )
            }
        }
        val onLinkTargetClicked = remember {
            { result: SearchResult.OfPost, linkTarget: LinkTarget ->
                if (linkTarget is LinkTarget.OfProfile) actions(
                    Action.Navigate.To(
                        pathDestination(
                            path = linkTarget.path,
                            referringRouteOption = NavigationAction.ReferringRouteOption.ParentOrCurrent,
                        )
                    )
                )
            }
        }
        val onPostSearchResultProfileClicked = remember {
            { result: SearchResult.OfPost ->
                actions(
                    Action.Navigate.To(
                        profileDestination(
                            referringRouteOption = NavigationAction.ReferringRouteOption.ParentOrCurrent,
                            profile = result.post.author,
                            avatarSharedElementKey = result.post.avatarSharedElementKey(
                                result.sharedElementPrefix
                            )
                        )
                    )
                )
            }
        }
        val onListMemberClicked = remember {
            { listMember: ListMember ->
                actions(
                    Action.Navigate.To(
                        profileDestination(
                            referringRouteOption = NavigationAction.ReferringRouteOption.ParentOrCurrent,
                            profile = listMember.subject,
                            avatarSharedElementKey = listMember.avatarSharedElementKey()
                        )
                    )
                )
            }
        }
        val onTrendClicked = remember {
            { trend: Trend ->
                actions(
                    Action.Navigate.To(
                        pathDestination(
                            path = trend.link,
                            referringRouteOption = NavigationAction.ReferringRouteOption.ParentOrCurrent,
                        )
                    )
                )
            }
        }
        val onFeedGeneratorClicked = remember {
            { feedGenerator: FeedGenerator ->
                actions(
                    Action.Navigate.To(
                        pathDestination(
                            path = feedGenerator.uri.path,
                            models = listOf(feedGenerator),
                            sharedElementPrefix = SearchFeedGeneratorSharedElementPrefix,
                            referringRouteOption = NavigationAction.ReferringRouteOption.ParentOrCurrent,
                        )
                    )
                )
            }
        }
        val onPostSearchResultClicked = remember {
            { result: SearchResult.OfPost ->
                actions(
                    Action.Navigate.To(
                        postDestination(
                            referringRouteOption = NavigationAction.ReferringRouteOption.ParentOrCurrent,
                            sharedElementPrefix = result.sharedElementPrefix,
                            post = result.post,
                        )
                    )
                )
            }
        }
        val onPostInteraction = postInteractionState::onInteraction

        AnimatedContent(
            targetState = state.layout
        ) { targetLayout ->
            when (targetLayout) {
                ScreenLayout.Suggested -> SuggestedContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp),
                    movableElementSharedTransitionScope = paneScaffoldState,
                    trends = state.trends,
                    suggestedProfiles = state.categoriesToSuggestedProfiles[state.suggestedProfileCategory]
                        ?: emptyList(),
                    starterPacksWithMembers = state.starterPacksWithMembers,
                    feedGenerators = state.feedGenerators,
                    onProfileClicked = onProfileClicked,
                    onViewerStateClicked = onViewerStateClicked,
                    onListMemberClicked = onListMemberClicked,
                    onTrendClicked = onTrendClicked,
                    onFeedGeneratorClicked = onFeedGeneratorClicked,
                )

                ScreenLayout.AutoCompleteProfiles -> AutoCompleteProfileSearchResults(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp),
                    paneMovableElementSharedTransitionScope = paneScaffoldState,
                    results = state.autoCompletedProfiles,
                    onProfileClicked = onProfileSearchResultClicked,
                    onViewerStateClicked = onViewerStateClicked,
                )

                ScreenLayout.GeneralSearchResults -> TabbedSearchResults(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    pagerState = pagerState,
                    state = state,
                    paneMovableElementSharedTransitionScope = paneScaffoldState,
                    onProfileClicked = onProfileSearchResultClicked,
                    onViewerStateClicked = onViewerStateClicked,
                    onLinkTargetClicked = onLinkTargetClicked,
                    onPostSearchResultProfileClicked = onPostSearchResultProfileClicked,
                    onPostSearchResultClicked = onPostSearchResultClicked,
                    onPostInteraction = onPostInteraction,
                    onFeedGeneratorClicked = onFeedGeneratorClicked,
                )
            }
        }
    }

    PostInteractionsBottomSheet(
        state = postInteractionState,
        onInteractionConfirmed = {
            actions(
                Action.SendPostInteraction(it)
            )
        },
        onQuotePostClicked = { repost ->
            actions(
                Action.Navigate.To(
                    composePostDestination(
                        type = Post.Create.Quote(repost),
                        sharedElementPrefix = null,
                    )
                )
            )
        }
    )

    LifecycleStartEffect(Unit) {
        actions(
            Action.FetchSuggestedProfiles(
                category = state.suggestedProfileCategory
            )
        )
        onStopOrDispose { }
    }
}

@Composable
private fun SuggestedContent(
    modifier: Modifier = Modifier,
    movableElementSharedTransitionScope: MovableElementSharedTransitionScope,
    trends: List<Trend>,
    suggestedProfiles: List<ProfileWithViewerState>,
    starterPacksWithMembers: List<SuggestedStarterPack>,
    feedGenerators: List<FeedGenerator>,
    onTrendClicked: (Trend) -> Unit,
    onProfileClicked: (ProfileWithViewerState) -> Unit,
    onViewerStateClicked: (ProfileWithViewerState) -> Unit,
    onListMemberClicked: (ListMember) -> Unit,
    onFeedGeneratorClicked: (FeedGenerator) -> Unit,
) {
    val now = remember { Clock.System.now() }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = bottomNavAndInsetPaddingValues(),
    ) {
        item {
            TrendTitle(
                modifier = Modifier
                    .padding(horizontal = 8.dp),
                icon = Icons.AutoMirrored.Rounded.ShowChart,
                title = stringResource(Res.string.trending_title),
            )
        }
        itemsIndexed(
            items = trends.take(5),
            key = { _, trend -> trend.link },
            itemContent = { index, trend ->
                Trend(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .clickable { onTrendClicked(trend) }
                        .padding(horizontal = 16.dp),
                    index = index,
                    now = now,
                    trend = trend,
                    onTrendClicked = onTrendClicked,
                )
            }
        )
        item {
            TrendTitle(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                icon = Icons.Rounded.AccountCircle,
                title = stringResource(Res.string.suggested_accounts),
            )
        }
        items(
            items = suggestedProfiles.take(5),
            key = { suggestedProfile -> suggestedProfile.profile.did.id },
            itemContent = { suggestedProfile ->
                ProfileWithViewerState(
                    modifier = Modifier
                        .clickable { onProfileClicked(suggestedProfile) }
                        .padding(horizontal = 16.dp),
                    movableElementSharedTransitionScope = movableElementSharedTransitionScope,
                    signedInProfileId = null,
                    profile = suggestedProfile.profile,
                    viewerState = suggestedProfile.viewerState,
                    profileSharedElementKey = Profile::searchProfileAvatarSharedElementKey,
                    onProfileClicked = { onProfileClicked(suggestedProfile) },
                    onViewerStateClicked = { onViewerStateClicked(suggestedProfile) },
                )
            }
        )
        item {
            TrendTitle(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                icon = Icons.Rounded.JoinFull,
                title = stringResource(Res.string.starter_packs),
            )
        }
        items(
            items = starterPacksWithMembers.take(5),
            key = { starterPackWithMember -> starterPackWithMember.starterPack.cid.id },
            itemContent = { starterPackWithMember ->
                SuggestedStarterPack(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(horizontal = 16.dp),
                    movableElementSharedTransitionScope = movableElementSharedTransitionScope,
                    starterPackWithMembers = starterPackWithMember,
                    onListMemberClicked = onListMemberClicked,
                )
            }
        )
        item {
            TrendTitle(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                icon = Icons.Rounded.RssFeed,
                title = stringResource(Res.string.discover_feeds),
            )
        }
        items(
            items = feedGenerators.take(5),
            key = { feedGenerator -> feedGenerator.cid.id },
            itemContent = { feedGenerator ->
                FeedGeneratorSearchResult(
                    modifier = Modifier
                        .fillParentMaxWidth(),
                    movableElementSharedTransitionScope = movableElementSharedTransitionScope,
                    sharedElementPrefix = SearchFeedGeneratorSharedElementPrefix,
                    feedGenerator = feedGenerator,
                    onFeedGeneratorClicked = onFeedGeneratorClicked,
                )
            }
        )
        item {
            Spacer(
                Modifier
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .height(UiTokens.bottomNavHeight)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrendTitle(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
) {
    Column(
        modifier = modifier.padding(
            vertical = 8.dp,
        )
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Icon(
                modifier = Modifier
                    .size(24.dp),
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}


@Composable
private fun AutoCompleteProfileSearchResults(
    paneMovableElementSharedTransitionScope: PaneScaffoldState,
    modifier: Modifier = Modifier,
    results: List<SearchResult.OfProfile>,
    onProfileClicked: (SearchResult.OfProfile) -> Unit,
    onViewerStateClicked: (ProfileWithViewerState) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = bottomNavAndInsetPaddingValues(
            horizontal = 16.dp,
        ),
    ) {
        items(
            items = results,
            key = { it.profileWithViewerState.profile.did.id },
            itemContent = { result ->
                ProfileSearchResult(
                    paneMovableElementSharedTransitionScope = paneMovableElementSharedTransitionScope,
                    result = result,
                    onProfileClicked = onProfileClicked,
                    onViewerStateClicked = { onViewerStateClicked(it.profileWithViewerState) }
                )
            }
        )
    }
}

@Composable
private fun TabbedSearchResults(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    state: State,
    paneMovableElementSharedTransitionScope: PaneScaffoldState,
    onProfileClicked: (SearchResult.OfProfile) -> Unit,
    onViewerStateClicked: (ProfileWithViewerState) -> Unit,
    onLinkTargetClicked: (SearchResult.OfPost, LinkTarget) -> Unit,
    onPostSearchResultProfileClicked: (SearchResult.OfPost) -> Unit,
    onPostSearchResultClicked: (SearchResult.OfPost) -> Unit,
    onPostInteraction: (Post.Interaction) -> Unit,
    onFeedGeneratorClicked: (FeedGenerator) -> Unit,
) {
    Column(
        modifier = modifier
    ) {
        val scope = rememberCoroutineScope()
        Tabs(
            modifier = Modifier.fillMaxWidth(),
            tabsState = rememberTabsState(
                tabs = listOf(
                    Tab(
                        title = stringResource(resource = Res.string.top),
                        hasUpdate = false
                    ),
                    Tab(
                        title = stringResource(resource = Res.string.latest),
                        hasUpdate = false
                    ),
                    Tab(
                        title = stringResource(resource = Res.string.people),
                        hasUpdate = false
                    ),
                    Tab(
                        title = stringResource(resource = Res.string.feeds),
                        hasUpdate = false
                    ),
                ),
                selectedTabIndex = pagerState::tabIndex,
                onTabSelected = {
                    scope.launch {
                        pagerState.animateScrollToPage(it)
                    }
                },
                onTabReselected = { },
            )
        )
        HorizontalPager(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                    )
                ),
            state = pagerState,
            key = { page -> page },
            pageContent = { page ->
                val searchResultStateHolder = remember { state.searchStateHolders[page] }
                SearchResults(
                    paneScaffoldState = paneMovableElementSharedTransitionScope,
                    searchResultStateHolder = searchResultStateHolder,
                    onProfileClicked = onProfileClicked,
                    onPostSearchResultProfileClicked = onPostSearchResultProfileClicked,
                    onPostSearchResultClicked = onPostSearchResultClicked,
                    onLinkTargetClicked = onLinkTargetClicked,
                    onPostInteraction = onPostInteraction,
                    onViewerStateClicked = { onViewerStateClicked(it.profileWithViewerState) },
                    onFeedGeneratorClicked = onFeedGeneratorClicked,
                )
            }
        )
    }
}

@Composable
private fun SearchResults(
    modifier: Modifier = Modifier,
    paneScaffoldState: PaneScaffoldState,
    searchResultStateHolder: SearchResultStateHolder,
    onProfileClicked: (SearchResult.OfProfile) -> Unit,
    onViewerStateClicked: (SearchResult.OfProfile) -> Unit,
    onLinkTargetClicked: (SearchResult.OfPost, LinkTarget) -> Unit,
    onPostSearchResultProfileClicked: (SearchResult.OfPost) -> Unit,
    onPostSearchResultClicked: (SearchResult.OfPost) -> Unit,
    onPostInteraction: (Post.Interaction) -> Unit,
    onFeedGeneratorClicked: (FeedGenerator) -> Unit,
) {
    val searchState = searchResultStateHolder.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    when (val state = searchState.value) {
        is SearchState.OfPosts -> {
            val now = remember { Clock.System.now() }
            val results by rememberUpdatedState(state.tiledItems)
            LazyColumn(
                modifier = modifier,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = bottomNavAndInsetPaddingValues(),
            ) {
                items(
                    items = results,
                    key = { it.post.cid.id },
                    itemContent = { result ->
                        PostSearchResult(
                            paneMovableElementSharedTransitionScope = paneScaffoldState,
                            now = now,
                            result = result,
                            onLinkTargetClicked = onLinkTargetClicked,
                            onProfileClicked = onPostSearchResultProfileClicked,
                            onPostClicked = onPostSearchResultClicked,
                            onPostInteraction = onPostInteraction,
                        )
                    }
                )
            }
            listState.PivotedTilingEffect(
                items = results,
                onQueryChanged = { query ->
                    searchResultStateHolder.accept(
                        SearchState.Tile(
                            tilingAction = TilingState.Action.LoadAround(
                                query ?: state.tilingData.currentQuery,
                            )
                        )
                    )
                }
            )
        }

        is SearchState.OfProfiles -> {
            val results by rememberUpdatedState(state.tiledItems)
            LazyColumn(
                modifier = modifier,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = bottomNavAndInsetPaddingValues(),
            ) {
                items(
                    items = results,
                    key = { it.profileWithViewerState.profile.did.id },
                    itemContent = { result ->
                        ProfileSearchResult(
                            paneMovableElementSharedTransitionScope = paneScaffoldState,
                            result = result,
                            onProfileClicked = onProfileClicked,
                            onViewerStateClicked = onViewerStateClicked,
                        )
                    }
                )
            }
            listState.PivotedTilingEffect(
                items = results,
                onQueryChanged = { query ->
                    searchResultStateHolder.accept(
                        SearchState.Tile(
                            tilingAction = TilingState.Action.LoadAround(
                                query ?: state.tilingData.currentQuery,
                            )
                        )
                    )
                }
            )
        }

        is SearchState.OfFeedGenerators -> {
            val results by rememberUpdatedState(state.tiledItems)
            LazyColumn(
                modifier = modifier,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = bottomNavAndInsetPaddingValues(),
            ) {
                items(
                    items = results,
                    key = { it.feedGenerator.cid.id },
                    itemContent = { result ->
                        FeedGeneratorSearchResult(
                            movableElementSharedTransitionScope = paneScaffoldState,
                            sharedElementPrefix = SearchFeedGeneratorSharedElementPrefix,
                            feedGenerator = result.feedGenerator,
                            onFeedGeneratorClicked = onFeedGeneratorClicked,
                        )
                    }
                )
            }
            listState.PivotedTilingEffect(
                items = results,
                onQueryChanged = { query ->
                    searchResultStateHolder.accept(
                        SearchState.Tile(
                            tilingAction = TilingState.Action.LoadAround(
                                query ?: state.tilingData.currentQuery,
                            )
                        )
                    )
                }
            )
        }
    }
}

private fun Profile.searchProfileAvatarSharedElementKey(): String =
    "suggested-profile-${did.id}"

internal const val SearchFeedGeneratorSharedElementPrefix = "search-feedGenerator"