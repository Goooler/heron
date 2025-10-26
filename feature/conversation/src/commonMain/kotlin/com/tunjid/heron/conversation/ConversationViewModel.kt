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

package com.tunjid.heron.conversation

import androidx.lifecycle.ViewModel
import com.tunjid.heron.conversation.di.sharedUri
import com.tunjid.heron.data.core.models.Message
import com.tunjid.heron.data.core.models.stubProfile
import com.tunjid.heron.data.core.types.ProfileHandle
import com.tunjid.heron.data.core.types.ProfileId
import com.tunjid.heron.data.core.types.Uri
import com.tunjid.heron.data.repository.AuthRepository
import com.tunjid.heron.data.repository.MessageQuery
import com.tunjid.heron.data.repository.MessageRepository
import com.tunjid.heron.data.repository.RecordRepository
import com.tunjid.heron.data.repository.TimelineRepository
import com.tunjid.heron.data.utilities.asRecordUri
import com.tunjid.heron.data.utilities.writequeue.Writable
import com.tunjid.heron.data.utilities.writequeue.WriteQueue
import com.tunjid.heron.feature.AssistedViewModelFactory
import com.tunjid.heron.feature.FeatureWhileSubscribed
import com.tunjid.heron.scaffold.navigation.NavigationMutation
import com.tunjid.heron.scaffold.navigation.consumeNavigationActions
import com.tunjid.heron.scaffold.navigation.removeQueryParamsFromCurrentRoute
import com.tunjid.heron.scaffold.scaffold.duplicateWriteMessage
import com.tunjid.heron.scaffold.scaffold.failedWriteMessage
import com.tunjid.heron.tiling.TilingState
import com.tunjid.heron.tiling.mapCursorList
import com.tunjid.heron.tiling.reset
import com.tunjid.heron.tiling.tilingMutations
import com.tunjid.mutator.ActionStateMutator
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.coroutines.actionStateFlowMutator
import com.tunjid.mutator.coroutines.mapLatestToManyMutations
import com.tunjid.mutator.coroutines.mapToManyMutations
import com.tunjid.mutator.coroutines.mapToMutation
import com.tunjid.mutator.coroutines.toMutationStream
import com.tunjid.tiler.buildTiledList
import com.tunjid.tiler.distinctBy
import com.tunjid.tiler.plus
import com.tunjid.tiler.tiledListOf
import com.tunjid.treenav.strings.Route
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
import kotlinx.datetime.Clock

internal typealias ConversationStateHolder = ActionStateMutator<Action, StateFlow<State>>

@AssistedFactory
fun interface RouteViewModelInitializer : AssistedViewModelFactory {
    override fun invoke(
        scope: CoroutineScope,
        route: Route,
    ): ActualConversationViewModel
}

@Inject
class ActualConversationViewModel(
    authRepository: AuthRepository,
    recordRepository: RecordRepository,
    messagesRepository: MessageRepository,
    timelineRepository: TimelineRepository,
    writeQueue: WriteQueue,
    navActions: (NavigationMutation) -> Unit,
    @Assisted
    scope: CoroutineScope,
    @Assisted
    route: Route,
) : ViewModel(viewModelScope = scope),
    ConversationStateHolder by scope.actionStateFlowMutator(
        initialState = State(route),
        started = SharingStarted.WhileSubscribed(FeatureWhileSubscribed),
        inputs = listOf(
            loadProfileMutations(authRepository),
            pendingMessageFlushMutations(writeQueue),
            flow { messagesRepository.monitorConversationLogs() },
            labelPreferencesMutations(
                timelineRepository = timelineRepository,
            ),
            labelerMutations(
                timelineRepository = timelineRepository,
            ),
        ),
        actionTransform = transform@{ actions ->
            merge(
                actions.toMutationStream(
                    keySelector = Action::key,
                ) {
                    when (val action = type()) {
                        is Action.Navigate -> action.flow.consumeNavigationActions(
                            navigationMutationConsumer = navActions,
                        )

                        is Action.SendPostInteraction -> action.flow.postInteractionMutations(
                            writeQueue = writeQueue,
                        )

                        is Action.SharedRecord -> action.flow.recordSharingMutations(
                            recordRepository = recordRepository,
                            navActions = navActions,
                            state = state,
                        )

                        is Action.SendMessage -> action.flow.sendMessageMutations(
                            writeQueue = writeQueue,
                            navActions = navActions,
                        )
                        is Action.SnackbarDismissed -> action.flow.snackbarDismissalMutations()

                        is Action.UpdateMessageReaction -> action.flow.updateMessageReactionMutations(
                            writeQueue = writeQueue,
                        )

                        is Action.Tile -> action.flow.messagingTilingMutations(
                            currentState = { state() },
                            messagesRepository = messagesRepository,
                        )
                    }
                },
                sharedRecordMutations(
                    sharedUri = route.sharedUri,
                    overrideExisting = false,
                    recordRepository = recordRepository,
                    state = state,
                ),
            )
        },
    )

private fun loadProfileMutations(
    authRepository: AuthRepository,
): Flow<Mutation<State>> =
    authRepository.signedInUser.mapToMutation {
        copy(signedInProfile = it)
    }

private fun pendingMessageFlushMutations(
    writeQueue: WriteQueue,
): Flow<Mutation<State>> =
    writeQueue.queueChanges.mapToMutation {
        val queuedIds = it.mapTo(mutableSetOf(), Writable::queueId)
        val updatedPendingMessages = pendingItems.filter { item ->
            queuedIds.contains(Writable.Send(item.message).queueId)
        }
        copy(
            pendingItems = updatedPendingMessages,
            tilingData = tilingData.updatePendingMessages(updatedPendingMessages),
        )
    }

private fun labelPreferencesMutations(
    timelineRepository: TimelineRepository,
): Flow<Mutation<State>> =
    timelineRepository.preferences()
        .mapToMutation { copy(labelPreferences = it.contentLabelPreferences) }

private fun labelerMutations(
    timelineRepository: TimelineRepository,
): Flow<Mutation<State>> =
    timelineRepository.labelers()
        .mapToMutation { copy(labelers = it) }

private fun sharedRecordMutations(
    sharedUri: Uri?,
    overrideExisting: Boolean,
    recordRepository: RecordRepository,
    state: suspend () -> State,
): Flow<Mutation<State>> =
    if (sharedUri == null) emptyFlow()
    else flow {
        val recordUri = sharedUri.asRecordUri() ?: return@flow
        val shouldFetch = when (state().sharedRecord) {
            SharedRecord.Consumed,
            is SharedRecord.Pending,
            -> overrideExisting
            SharedRecord.None -> true
        }
        if (!shouldFetch) return@flow

        emitAll(
            recordRepository.record(recordUri)
                // Take only one emission so user changes do not override it
                .take(1)
                .mapToMutation {
                    copy(sharedRecord = SharedRecord.Pending(it))
                },
        )
    }

private fun Flow<Action.SendPostInteraction>.postInteractionMutations(
    writeQueue: WriteQueue,
): Flow<Mutation<State>> =
    mapToManyMutations { action ->
        when (writeQueue.enqueue(Writable.Interaction(action.interaction))) {
            WriteQueue.Status.Dropped -> emit {
                copy(messages = messages + action.interaction.failedWriteMessage())
            }
            WriteQueue.Status.Duplicate -> emit {
                copy(messages = messages + action.interaction.duplicateWriteMessage())
            }
            WriteQueue.Status.Enqueued -> Unit
        }
    }

private fun Flow<Action.SnackbarDismissed>.snackbarDismissalMutations(): Flow<Mutation<State>> =
    mapToMutation { action ->
        copy(messages = messages - action.message)
    }

private fun Flow<Action.UpdateMessageReaction>.updateMessageReactionMutations(
    writeQueue: WriteQueue,
): Flow<Mutation<State>> =
    mapToManyMutations { action ->
        writeQueue.enqueue(Writable.Reaction(action.reaction))
    }

private fun Flow<Action.SharedRecord>.recordSharingMutations(
    recordRepository: RecordRepository,
    navActions: (NavigationMutation) -> Unit,
    state: suspend () -> State,
): Flow<Mutation<State>> =
    mapLatestToManyMutations { action ->
        when (action) {
            is Action.SharedRecord.Add -> sharedRecordMutations(
                sharedUri = action.uri,
                overrideExisting = true,
                recordRepository = recordRepository,
                state = state,
            )
            Action.SharedRecord.Remove -> {
                emit { copy(sharedRecord = SharedRecord.Consumed) }
                navActions(ConsumeSharedUriQueryParam)
            }
        }
    }

private fun Flow<Action.SendMessage>.sendMessageMutations(
    writeQueue: WriteQueue,
    navActions: (NavigationMutation) -> Unit,
): Flow<Mutation<State>> =
    mapToManyMutations { action ->
        // Add the pending item to the chat
        emit {
            val currentItems = tilingData.items
            val tileCount = currentItems.tileCount
            val lastQuery = when {
                tileCount > 0 -> currentItems.queryAtTile(tileCount - 1)
                else -> tilingData.currentQuery
            }
            val pendingItem = MessageItem.Pending(
                sender = signedInProfile ?: stubProfile(
                    did = ProfileId(""),
                    handle = ProfileHandle(id = ""),
                ),
                message = action.message,
                sentAt = Clock.System.now(),
            )

            copy(
                sharedRecord = when (action.message.recordReference) {
                    null -> sharedRecord
                    else -> SharedRecord.Consumed
                },
                pendingItems = pendingItems + pendingItem,
                tilingData = tilingData.copy(
                    items = currentItems + tiledListOf(
                        lastQuery to pendingItem,
                    ),
                ),
            )
        }
        navActions(ConsumeSharedUriQueryParam)


        // Write the message
        writeQueue.enqueue(Writable.Send(action.message))
    }

private suspend fun Flow<Action.Tile>.messagingTilingMutations(
    currentState: suspend () -> State,
    messagesRepository: MessageRepository,
): Flow<Mutation<State>> =
    map { it.tilingAction }
        .tilingMutations(
            currentState = currentState,
            updateQueryData = { copy(data = it) },
            refreshQuery = { copy(data = data.reset()) },
            cursorListLoader = messagesRepository::messages.mapCursorList<MessageQuery, Message, MessageItem>(
                MessageItem::Sent,
            ),
            onNewItems = { items ->
                items.distinctBy(MessageItem::id)
            },
            onTilingDataUpdated = tilingDataUpdated@{ updatedTilingData ->
                copy(
                    tilingData = when {
                        pendingItems.isEmpty() -> updatedTilingData
                        // Database refreshes can happen at any time. Add pending items.
                        else -> updatedTilingData.updatePendingMessages(pendingItems)
                    },
                )
            },
        )

private fun TilingState.Data<MessageQuery, MessageItem>.updatePendingMessages(
    pendingItems: List<MessageItem.Pending>,
): TilingState.Data<MessageQuery, MessageItem> =
    copy(
        items = when {
            items.isEmpty() -> buildTiledList {
                addAll(
                    query = currentQuery,
                    items = pendingItems,
                )
            }

            else -> buildTiledList {
                (0..<items.tileCount).forEach { tileIndex ->
                    val tile = items.tileAt(tileIndex)
                    val lastTileIndex = items.tileCount - 1
                    val tileSublist = items.subList(tile.start, tile.end)
                    if (tileIndex == lastTileIndex) addAll(
                        query = items.queryAtTile(tileIndex),
                        // Add pending items to the last chunk and sort
                        items = (tileSublist + pendingItems).sortedBy(MessageItem::sentAt),
                    )
                    else addAll(
                        query = items.queryAtTile(tileIndex),
                        items = tileSublist,
                    )
                }
            }
        },
    )

private val ConsumeSharedUriQueryParam = removeQueryParamsFromCurrentRoute(setOf("sharedUri"))
