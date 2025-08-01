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

package com.tunjid.heron.data.repository

import app.bsky.actor.GetPreferencesResponse
import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.PreferencesUnion
import app.bsky.actor.SavedFeed
import app.bsky.actor.Type
import app.bsky.feed.GetFeedGeneratorQueryParams
import app.bsky.graph.GetListQueryParams
import com.atproto.server.CreateSessionRequest
import com.tunjid.heron.data.core.models.Preferences
import com.tunjid.heron.data.core.models.Profile
import com.tunjid.heron.data.core.models.TimelinePreference
import com.tunjid.heron.data.core.types.Id
import com.tunjid.heron.data.core.types.ProfileId
import com.tunjid.heron.data.database.daos.ProfileDao
import com.tunjid.heron.data.database.entities.ProfileEntity
import com.tunjid.heron.data.database.entities.asExternalModel
import com.tunjid.heron.data.local.models.SessionRequest
import com.tunjid.heron.data.network.NetworkService
import com.tunjid.heron.data.network.models.profileEntity
import com.tunjid.heron.data.utilities.multipleEntitysaver.MultipleEntitySaverProvider
import com.tunjid.heron.data.utilities.multipleEntitysaver.add
import com.tunjid.heron.data.utilities.withRefresh
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did

interface AuthRepository {
    val isSignedIn: Flow<Boolean>

    val signedInUser: Flow<Profile?>

    fun isSignedInProfile(id: Id): Flow<Boolean>

    suspend fun createSession(request: SessionRequest): Result<Unit>

    suspend fun updateSignedInUser(): Boolean
}

@Inject
internal class AuthTokenRepository(
    private val profileDao: ProfileDao,
    private val multipleEntitySaverProvider: MultipleEntitySaverProvider,
    private val networkService: NetworkService,
    private val savedStateRepository: SavedStateRepository,
) : AuthRepository {

    override val isSignedIn: Flow<Boolean> =
        savedStateRepository.savedState.map { it.auth != null }

    override val signedInUser: Flow<Profile?> =
        savedStateRepository.savedState
            .distinctUntilChangedBy { it.auth?.authProfileId }
            .flatMapLatest { savedState ->
                val signeInUserFlow = savedState.auth
                    ?.authProfileId
                    ?.let(::listOf)
                    ?.let(profileDao::profiles)
                    ?.filter(List<ProfileEntity>::isNotEmpty)
                    ?.map { it.first().asExternalModel() }
                    ?: flowOf(null)
                signeInUserFlow.withRefresh {
                    updateSignedInUser()
                }
            }

    override fun isSignedInProfile(id: Id): Flow<Boolean> =
        savedStateRepository.savedState
            .distinctUntilChangedBy { it.auth?.authProfileId }
            .map { id == it.auth?.authProfileId }

    override suspend fun createSession(
        request: SessionRequest,
    ): Result<Unit> = networkService.runCatchingWithMonitoredNetworkRetry(times = 2) {
        createSession(
            CreateSessionRequest(
                identifier = request.username,
                password = request.password,
            )
        )
    }
        .mapCatching { result ->
            savedStateRepository.updateState {
                copy(
                    auth = SavedState.AuthTokens(
                        authProfileId = ProfileId(result.did.did),
                        auth = result.accessJwt,
                        refresh = result.refreshJwt,
                        didDoc = SavedState.AuthTokens.DidDoc.fromJsonContentOrEmpty(
                            jsonContent = result.didDoc,
                        ),
                    )
                )
            }
            // Suspend till auth token has been saved and is readable
            savedStateRepository.savedState.first { it.auth != null }
            updateSignedInUser(result.did)
        }


    override suspend fun updateSignedInUser(): Boolean {
        return networkService.runCatchingWithMonitoredNetworkRetry {
            getSession()
        }
            .getOrNull()
            ?.did
            ?.let { updateSignedInUser(it) } == true
    }

    private suspend fun updateSignedInUser(did: Did) = supervisorScope {
        listOf(
            async {
                networkService.runCatchingWithMonitoredNetworkRetry {
                    getProfile(GetProfileQueryParams(actor = did))
                }
                    .getOrNull()
                    ?.profileEntity()
                    ?.let { profileDao.upsertProfiles(listOf(it)) } != null
            },
            async {
                networkService.runCatchingWithMonitoredNetworkRetry {
                    getPreferences()
                }
                    .getOrNull()
                    ?.let { savePreferences(it) } != null
            },
        ).awaitAll().all(true::equals)
    }

    private suspend fun savePreferences(
        preferencesResponse: GetPreferencesResponse,
    ) = preferencesResponse.preferences.map { preferencesUnion ->
        when (preferencesUnion) {
            is PreferencesUnion.AdultContentPref -> Unit
            is PreferencesUnion.BskyAppStatePref -> Unit
            is PreferencesUnion.ContentLabelPref -> Unit
            is PreferencesUnion.FeedViewPref -> Unit
            is PreferencesUnion.HiddenPostsPref -> Unit
            is PreferencesUnion.InterestsPref -> Unit
            is PreferencesUnion.LabelersPref -> Unit
            is PreferencesUnion.MutedWordsPref -> Unit
            is PreferencesUnion.PersonalDetailsPref -> Unit
            is PreferencesUnion.SavedFeedsPref -> Unit
            is PreferencesUnion.SavedFeedsPrefV2 ->
                saveFeedPreferences(preferencesUnion)

            is PreferencesUnion.ThreadViewPref -> Unit
            is PreferencesUnion.Unknown -> Unit
            is PreferencesUnion.PostInteractionSettingsPref -> Unit
            is PreferencesUnion.VerificationPrefs -> Unit
        }
    }


    private suspend fun saveFeedPreferences(
        preferencesUnion: PreferencesUnion.SavedFeedsPrefV2,
    ) = supervisorScope {
        val saveTimelinePreferences = async {
            savedStateRepository.updateState {
                val timelinePreferences = preferencesUnion.value.items.map {
                    TimelinePreference(
                        id = it.id,
                        type = it.type.value,
                        value = it.value,
                        pinned = it.pinned,
                    )
                }
                copy(
                    preferences = preferences?.copy(
                        timelinePreferences = timelinePreferences
                    ) ?: Preferences(
                        timelinePreferences = timelinePreferences
                    )
                )
            }
        }
        val types = preferencesUnion.value.items.groupBy(
            SavedFeed::type
        )
        val feeds = types[Type.Feed]?.map {
            async {
                networkService.runCatchingWithMonitoredNetworkRetry(times = 2) {
                    getFeedGenerator(
                        GetFeedGeneratorQueryParams(
                            feed = AtUri(it.value)
                        )
                    )
                }
            }
        } ?: emptyList()
        val lists = types[Type.List]?.map {
            async {
                networkService.runCatchingWithMonitoredNetworkRetry(times = 2) {
                    getList(
                        GetListQueryParams(
                            cursor = null,
                            limit = 1,
                            list = AtUri(it.value)
                        )
                    )
                }
            }
        } ?: emptyList()

        saveTimelinePreferences.await()
        multipleEntitySaverProvider.saveInTransaction {
            feeds.mapNotNull { it.await().getOrNull() }.forEach { add(it.view) }
            lists.mapNotNull { it.await().getOrNull() }.forEach { add(it.list) }
        }
    }

}