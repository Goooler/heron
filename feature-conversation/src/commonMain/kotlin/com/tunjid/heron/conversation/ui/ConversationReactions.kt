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

package com.tunjid.heron.conversation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunjid.heron.conversation.hasEmojiReaction
import com.tunjid.heron.data.core.models.Message
import com.tunjid.heron.ui.Tab
import com.tunjid.heron.ui.Tabs
import com.tunjid.heron.ui.TabsState.Companion.rememberTabsState
import com.tunjid.heron.ui.tabIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Stable
class EmojiPickerSheetState(
    internal val sheetState: SheetState,
    internal val scope: CoroutineScope,
) {
    var messageToReactTo by mutableStateOf<Message?>(null)
        internal set

    fun showSheet(message: Message) {
        messageToReactTo = message
    }

    fun hideSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                messageToReactTo = null
            }
        }
    }

    companion object Companion {
        @Composable
        fun rememberEmojiPickerState(): EmojiPickerSheetState {
            val sheetState = rememberModalBottomSheetState()
            val scope = rememberCoroutineScope()

            return remember(sheetState, scope) {
                EmojiPickerSheetState(
                    sheetState = sheetState,
                    scope = scope,
                )
            }
        }
    }
}

/**
 * The main composable for the emoji picker.
 *
 * @param onEmojiSelected A callback function that is invoked with the selected emoji string.
 */
@Composable
fun EmojiPickerBottomSheet(
    state: EmojiPickerSheetState,
    onEmojiSelected: (message: Message, emoji: String) -> Unit,
) {
    // Extract category titles and remember them
    val categories = remember { emojiCategories.keys.toList() }

    // Create a PagerState to control the HorizontalPager
    val pagerState = rememberPagerState(pageCount = { categories.size })

    // Coroutine scope for launching animations
    val scope = rememberCoroutineScope()

    val messageToReactTo = state.messageToReactTo
    if (messageToReactTo != null) ModalBottomSheet(
        onDismissRequest = {
            state.messageToReactTo = null
        },
        sheetState = state.sheetState,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // TabRow to display the categories
                Tabs(
                    modifier = Modifier
                        .fillMaxWidth(),
                    tabsState = rememberTabsState(
                        tabs = categories.map {
                            Tab(
                                title = emojiCategories[it]?.firstOrNull() ?: "X",
                                hasUpdate = false,
                            )
                        },
                        selectedTabIndex = pagerState::tabIndex,
                        onTabSelected = {
                            scope.launch {
                                pagerState.animateScrollToPage(it)
                            }
                        },
                        onTabReselected = { },
                    )
                )


                // HorizontalPager to swipe between emoji grids
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { pageIndex ->
                    val category = categories[pageIndex]
                    val emojis = emojiCategories[category] ?: emptyList()

                    // LazyVerticalGrid to display emojis efficiently
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 48.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        items(emojis) { emoji ->
                            IconButton(
                                modifier = Modifier
                                    .padding(1.dp)
                                    .aspectRatio(1f), // Make the emoji cell square
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = when {
                                        messageToReactTo.hasEmojiReaction(emoji) -> MaterialTheme.colorScheme.primaryContainer
                                        else -> Color.Unspecified
                                    },
                                ),
                                onClick = {
                                    onEmojiSelected(messageToReactTo, emoji)
                                    state.hideSheet()
                                },
                                content = {
                                    Text(
                                        text = emoji,
                                        textAlign = TextAlign.Center,
                                        fontSize = 24.sp,
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

private val emojiCategories: Map<String, List<String>> = mapOf(
    "😃Smileys & Emotion" to listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥹", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍",
        "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩",
        "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭",
        "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🫣",
        "🤭", "🤫", "🤥", "😶", "😶‍🌫️", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴",
        "🤤", "😪", "😵", "😵‍💫", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿",
        "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼",
        "😽", "🙀", "😿", "😾"
    ),

    "🧑People & Body" to listOf(
        "👋",
        "🤚",
        "🖐️",
        "✋",
        "🖖",
        "👌",
        "🤌",
        "🤏",
        "✌️",
        "🤞",
        "🤟",
        "🤘",
        "🤙",
        "👈",
        "👉",
        "👆",
        "🖕",
        "👇",
        "☝️",
        "👍",
        "👎",
        "✊",
        "👊",
        "🤛",
        "🤜",
        "👏",
        "🙌",
        "👐",
        "🤲",
        "🤝",
        "🙏",
        "✍️",
        "💅",
        "🤳",
        "💪",
        "🦾",
        "🦵",
        "🦿",
        "🦶",
        "👣",
        "👂",
        "🦻",
        "👃",
        "🧠",
        "🫀",
        "🫁",
        "🦷",
        "🦴",
        "👀",
        "👁️",
        "👅",
        "👄",
        "💋",
        "🩸",
        "👶",
        "👧",
        "🧒",
        "👦",
        "👩",
        "🧑",
        "👨",
        "👩‍🦱",
        "🧑‍🦱",
        "👨‍🦱",
        "👩‍🦰",
        "🧑‍🦰",
        "👨‍🦰",
        "👱‍♀️",
        "👱",
        "👱‍♂️",
        "👩‍🦳",
        "🧑‍🦳",
        "👨‍🦳",
        "👩‍🦲",
        "🧑‍🦲",
        "👨‍🦲",
        "🧔‍♀️",
        "🧔",
        "🧔‍♂️",
        "👵",
        "🧓",
        "👴",
        "👲",
        "👳‍♀️",
        "👳",
        "👳‍♂️",
        "🧕",
        "👮‍♀️",
        "👮",
        "👮‍♂️",
        "👷‍♀️",
        "👷",
        "👷‍♂️",
        "💂‍♀️",
        "💂",
        "💂‍♂️",
        "🕵️‍♀️",
        "🕵️",
        "🕵️‍♂️",
        "👩‍⚕️",
        "🧑‍⚕️",
        "👨‍⚕️",
        "👩‍🌾",
        "🧑‍🌾",
        "👨‍🌾",
        "👩‍🍳",
        "🧑‍🍳",
        "👨‍🍳",
        "👩‍🎓",
        "🧑‍🎓",
        "👨‍🎓",
        "👩‍🎤",
        "🧑‍🎤",
        "👨‍🎤",
        "👩‍🏫",
        "🧑‍🏫",
        "👨‍🏫",
        "👩‍🏭",
        "🧑‍🏭",
        "👨‍🏭",
        "👩‍💻",
        "🧑‍💻",
        "👨‍💻",
        "👩‍💼",
        "🧑‍💼",
        "👨‍💼",
        "👩‍🔧",
        "🧑‍🔧",
        "👨‍🔧",
        "👩‍🔬",
        "🧑‍🔬",
        "👨‍🔬",
        "👩‍🎨",
        "🧑‍🎨",
        "👨‍🎨",
        "👩‍🚒",
        "🧑‍🚒",
        "👨‍🚒",
        "👩‍✈️",
        "🧑‍✈️",
        "👨‍✈️",
        "👩‍🚀",
        "🧑‍🚀",
        "👨‍🚀",
        "👩‍⚖️",
        "🧑‍⚖️",
        "👨‍⚖️",
        "🫅",
        "🤴",
        "👸",
        "🦸‍♀️",
        "🦸",
        "🦸‍♂️",
        "🦹‍♀️",
        "🦹",
        "🦹‍♂️",
        "🤶",
        "🧑‍🎄",
        "🎅",
        "🧙‍♀️",
        "🧙",
        "🧙‍♂️",
        "🧝‍♀️",
        "🧝",
        "🧝‍♂️",
        "🧛‍♀️",
        "🧛",
        "🧛‍♂️",
        "🧟‍♀️",
        "🧟",
        "🧟‍♂️",
        "🧞‍♀️",
        "🧞",
        "🧞‍♂️",
        "🧜‍♀️",
        "🧜",
        "🧜‍♂️",
        "🧚‍♀️",
        "🧚",
        "🧚‍♂️",
        "👼",
        "🤰",
        "🫄",
        "🫃",
        "🤱",
        "👩‍🍼",
        "🧑‍🍼",
        "👨‍🍼",
        "🙇‍♀️",
        "🙇",
        "🙇‍♂️",
        "💁‍♀️",
        "💁",
        "💁‍♂️",
        "🙅‍♀️",
        "🙅",
        "🙅‍♂️",
        "🙆‍♀️",
        "🙆",
        "🙆‍♂️",
        "🙋‍♀️",
        "🙋",
        "🙋‍♂️",
        "🧏‍♀️",
        "🧏",
        "🧏‍♂️",
        "🤦‍♀️",
        "🤦",
        "🤦‍♂️",
        "🤷‍♀️",
        "🤷",
        "🤷‍♂️",
        "🙎‍♀️",
        "🙎",
        "🙎‍♂️",
        "🙍‍♀️",
        "🙍",
        "🙍‍♂️",
        "💇‍♀️",
        "💇",
        "💇‍♂️",
        "💆‍♀️",
        "💆",
        "💆‍♂️",
        "🧖‍♀️",
        "🧖",
        "🧖‍♂️",
        "💃",
        "🕺",
        "🕴️",
        "👩‍🦽",
        "🧑‍🦽",
        "👨‍🦽",
        "👩‍🦼",
        "🧑‍🦼",
        "👨‍🦼",
        "🚶‍♀️",
        "🚶",
        "🚶‍♂️",
        "👩‍🦯",
        "🧑‍🦯",
        "👨‍🦯",
        "🧎‍♀️",
        "🧎",
        "🧎‍♂️",
        "🏃‍♀️",
        "🏃",
        "🏃‍♂️",
        "🧍‍♀️",
        "🧍",
        "🧍‍♂️",
        "👭",
        "🧑‍🤝‍🧑",
        "👬",
        "👫",
        "👩‍❤️‍👩",
        "💑",
        "👨‍❤️‍👨",
        "👩‍❤️‍👨",
        "👩‍❤️‍💋‍👩",
        "💏",
        "👨‍❤️‍💋‍👨",
        "👩‍❤️‍💋‍👨",
        "👨‍👩‍👦",
        "👨‍👩‍👧",
        "👨‍👩‍👧‍👦",
        "👨‍👩‍👦‍👦",
        "👨‍👩‍👧‍👧",
        "👩‍👩‍👦",
        "👩‍👩‍👧",
        "👩‍👩‍👧‍👦",
        "👩‍👩‍👦‍👦",
        "👩‍👩‍👧‍👧",
        "👨‍👨‍👦",
        "👨‍👨‍👧",
        "👨‍👨‍👧‍👦",
        "👨‍👨‍👦‍👦",
        "👨‍👨‍👧‍👧",
        "👩‍👦",
        "👩‍👧",
        "👩‍👧‍👦",
        "👩‍👦‍👦",
        "👩‍👧‍👧",
        "👨‍👦",
        "👨‍👧",
        "👨‍👧‍👦",
        "👨‍👦‍👦",
        "👨‍👧‍👧",
        "🗣️",
        "👤",
        "👥",
        "🫂"
    ),

    "🐻Animals & Nature" to listOf(
        "🙈", "🙉", "🙊", "🐵", "🐒", "🦍", "🦧", "🐶", "🐕", "🦮", "🐕‍🦺", "🐩", "🐺", "🦊", "🦝", "🐱",
        "🐈", "🐈‍⬛", "🦁", "🐯", "🐅", "🐆", "🐴", "🐎", "🦄", "🦓", "🦌", "🦬", "🐮", "🐂", "🐃", "🐄",
        "🐷", "🐖", "🐗", "🐽", "🐏", "🐑", "🐐", "🐪", "🐫", "🦙", "🦒", "🐘", "🦣", "🦏", "🦛", "🐭",
        "🐁", "🐀", "🐹", "🐰", "🐇", "🐿️", "🦫", "🦔", "🦇", "🐻", "🐻‍❄️", "🐨", "🐼", "🦥", "🦦", "🦨",
        "🦘", "🦡", "🐾", "🦃", "🐔", "🐓", "🐣", "🐤", "🐥", "🐦", "🐧", "🕊️", "🦅", "🦆", "🦢", "🦉",
        "🦤", "🪶", "🦩", "🦚", "🦜", "🐸", "🐊", "🐢", "🦎", "🐍", "🐲", "🐉", "🦕", "🦖", "🐳", "🐋",
        "🐬", "🦭", "🐟", "🐠", "🐡", "🦈", "🐙", "🐚", "🪸", "🐌", "🦋", "🐛", "🐜", "🐝", "🪲", "🐞",
        "🦗", "🪳", "🕷️", "🕸️", "🦂", "🦟", "🪰", "🪱", "🦠", "💐", "🌸", "💮", "🏵️", "🌹", "🥀", "🌺",
        "🌻", "🌼", "🌷", "🌱", "🪴", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "☘️", "🍀", "🍁", "🍂", "🍃",
        "🍄", "🪨", "🌰", "🌍", "🌎", "🌏", "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔", "🌚", "🌝",
        "🌞", "🪐", "⭐", "🌟", "🌠", "🌌", "☁️", "⛅", "⛈️", "🌤️", "🌥️", "🌦️", "🌧️", "🌨️", "🌩️", "🌪️",
        "🌫️", "🌬️", "🌀", "🌈", "🌂", "☂️", "☔", "⛱️", "⚡", "❄️", "☃️", "⛄", "☄️", "🔥", "💧", "🌊"
    ),

    "🍔Food & Drink" to listOf(
        "🍇", "🍈", "🍉", "🍊", "🍋", "🍌", "🍍", "🥭", "🍎", "🍏", "🍐", "🍑", "🍒", "🍓", "🫐", "🥝",
        "🍅", "🫒", "🥥", "🥑", "🍆", "🥔", "🥕", "🌽", "🌶️", "🫑", "🥒", "🥬", "🥦", "🧄", "🧅", "🍄",
        "🥜", "🫘", "🌰", "🍞", "🥐", "🥖", "🫓", "🥨", "🥯", "🥞", "🧇", "🧀", "🍖", "🍗", "🥩", "🥓",
        "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🫔", "🥙", "🧆", "🥚", "🍳", "🥘", "🍲", "🫕", "🥣",
        "🥗", "🍿", "🧈", "🧂", "🥫", "🍱", "🍘", "🍙", "🍚", "🍛", "🍜", "🍝", "🍠", "🍢", "🍣", "🍤",
        "🍥", "🥮", "🍡", "🥟", "🥠", "🥡", "🦀", "🦞", "🦐", "🦑", "🦪", "🍦", "🍧", "🍨", "🍩", "🍪",
        "🎂", "🍰", "🧁", "🥧", "🍫", "🍬", "🍭", "🍮", "🍯", "🍼", "🥛", "☕", "🫖", "🍵", "🍶", "🍾",
        "🍷", "🍸", "🍹", "🍺", "🍻", "🥂", "🥃", "🫗", "🥤", "🧋", "🧃", "🧉", "🧊", "🥢", "🍽️", "🍴",
        "🥄", "🏺"
    ),

    "⚽Activity" to listOf(
        "⚽",
        "🏀",
        "🏈",
        "⚾",
        "🥎",
        "🎾",
        "🏐",
        "🏉",
        "🥏",
        "🎱",
        "🪀",
        "🏓",
        "🏸",
        "🏒",
        "🏑",
        "🥍",
        "🏏",
        "🪃",
        "🥅",
        "⛳",
        "🪁",
        "🏹",
        "🎣",
        "🤿",
        "🥊",
        "🥋",
        "🎽",
        "🛹",
        "🛼",
        "🛷",
        "⛸️",
        "🥌",
        "🎿",
        "⛷️",
        "🏂",
        "🪂",
        "🏋️‍♀️",
        "🏋️",
        "🏋️‍♂️",
        "🤼‍♀️",
        "🤼",
        "🤼‍♂️",
        "🤸‍♀️",
        "🤸",
        "🤸‍♂️",
        "⛹️‍♀️",
        "⛹️",
        "⛹️‍♂️",
        "🤺",
        "🤾‍♀️",
        "🤾",
        "🤾‍♂️",
        "🏌️‍♀️",
        "🏌️",
        "🏌️‍♂️",
        "🏇",
        "🧘‍♀️",
        "🧘",
        "🧘‍♂️",
        "🏄‍♀️",
        "🏄",
        "🏄‍♂️",
        "🏊‍♀️",
        "🏊",
        "🏊‍♂️",
        "🤽‍♀️",
        "🤽",
        "🤽‍♂️",
        "🚣‍♀️",
        "🚣",
        "🚣‍♂️",
        "🧗‍♀️",
        "🧗",
        "🧗‍♂️",
        "🚵‍♀️",
        "🚵",
        "🚵‍♂️",
        "🚴‍♀️",
        "🚴",
        "🚴‍♂️",
        "🏆",
        "🥇",
        "🥈",
        "🥉",
        "🏅",
        "🎖️",
        "🏵️",
        "🎗️",
        "🎫",
        "🎟️",
        "🎪",
        "🤹‍♀️",
        "🤹",
        "🤹‍♂️",
        "🎭",
        "🎨",
        "🎬",
        "🎤",
        "🎧",
        "🎼",
        "🎹",
        "🥁",
        "🪘",
        "🎷",
        "🎺",
        "🪗",
        "🎸",
        "🪕",
        "🎻",
        "🎲",
        "♟️",
        "🎯",
        "🎳",
        "🎮",
        "🎰",
        "🧩"
    ),

    "🚀Travel & Places" to listOf(
        "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🦯", "🦽",
        "🦼", "🛴", "🚲", "🛵", "🏍️", "🛺", "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋",
        "🚞", "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇", "🚊", "🚉", "✈️", "🛫", "🛬", "🛩️", "💺", "🛰️",
        "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "🪝", "⛽", "🚧", "🚦", "🚥",
        "🛑", "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️", "🎡", "🎢", "🎠", "⛲", "⛱️", "🏖️", "🏝️", "🏜️",
        "🌋", "⛰️", "🏔️", "🗻", "🏕️", "⛺", "🛖", "🏠", "🏡", "🏘️", "🏚️", "🏢", "🏬", "🏣", "🏤", "🏥",
        "🏦", "🏨", "🏩", "🏪", "🏫", "🏭", "⛩️", "🕋", "🕌", "🕍", "🛕", "⛪", "🌉", "🌃", "🏙️", "🌆", "🌇", "🌉"
    ),

    "💡Objects" to listOf(
        "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️", "🗜️", "💾", "💿", "📀", "📼", "📷",
        "📸", "📹", "🎥", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️", "🎛️", "🧭", "⏱️", "⏲️",
        "⏰", "🕰️", "⏳", "⌛", "📡", "🔋", "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸", "💵", "💴",
        "💶", "💷", "🪙", "💰", "💳", "💎", "⚖️", "🦯", "🧰", "🔧", "🔨", "⚒️", "🛠️", "⛏️", "🔩", "⚙️",
        "🧱", "⛓️", "🪝", "🧲", "🔫", "💣", "🧨", "🪓", "🔪", "🗡️", "⚔️", "🛡️", "🚬", "⚰️", "🪦", "⚱️",
        "🏺", "🔮", "🪄", "📿", "🧿", "💈", "⚗️", "🔭", "🔬", "🕳️", "🩹", "🩺", "💊", "💉", "🩸", "🧬",
        "🦠", "🧫", "🧪", "🌡️", "🧹", "🧺", "🧻", "🚽", "🚰", "🚿", "🛁", "🛀", "🧼", "🪥", "🪒", "🧽",
        "🪣", "🧴", "🛎️", "🔑", "🗝️", "🚪", "🪑", "🛋️", "🛏️", "🛌", "🧸", "🪆", "🖼️", "🪞", "🪟", "🛍️",
        "🛒", "🎁", "🎈", "🎏", "🎀", "🎊", "🎉", "🎎", "🏮", "🎐", "🧧", "✉️", "📩", "📨", "📧", "💌",
        "📮", "📪", "📫", "📬", "📭", "📦", "🏷️", "🧾", "📊", "📈", "📉", "📜", "📃", "📄", "📑", "📂", "📁",
        "🗂️", "📅", "📆", "🗒️", "🗓️", "📇", "🗃️", "🗳️", "🗄️", "📋", "📌", "📍", "📎", "🖇️", "📏", "📐",
        "✂️", "✒️", "🖋️", "🖌️", "🖍️", "📝", "✏️", "🔎", "🔍", "🔏", "🔐", "🔒", "🔓", "🧥", "🥼", "🦺",
        "👚", "👕", "👖", "🩲", "🩳", "👔", "👗", "👘", "🥻", "🩱", "👙", "🩴", "🥿", "👠", "👡", "👢",
        "👞", "👟", "🥾", "🧦", "🧤", "🧣", "🎩", "🧢", "👒", "🎓", "⛑️", "🪖", "👑", "💍", "👜", "👝",
        "👛", "💼", "🎒", "🧳", "👓", "🕶️", "🥽", "🌂", "☂️"
    ),

    "💕Symbols" to listOf(
        "❤️",
        "🧡",
        "💛",
        "💚",
        "💙",
        "💜",
        "🖤",
        "🤍",
        "🤎",
        "💔",
        "❤️‍🔥",
        "❤️‍🩹",
        "❣️",
        "💕",
        "💞",
        "💓",
        "💗",
        "💖",
        "💘",
        "💝",
        "💟",
        "☮️",
        "✝️",
        "☪️",
        "🕉️",
        "☸️",
        "✡️",
        "🔯",
        "🕎",
        "☯️",
        "☦️",
        "🛐",
        "⛎",
        "♈",
        "♉",
        "♊",
        "♋",
        "♌",
        "♍",
        "♎",
        "♏",
        "♐",
        "♑",
        "♒",
        "♓",
        "🆔",
        "⚛️",
        "🉑",
        "☢️",
        "☣️",
        "📴",
        "📳",
        "🈶",
        "🈚",
        "🈸",
        "🈺",
        "🈷️",
        "✴️",
        "🆚",
        "💮",
        "🉐",
        "㊙️",
        "㊗️",
        "🈴",
        "🈵",
        "🈹",
        "🈲",
        "🅰️",
        "🅱️",
        "🆎",
        "🆑",
        "🅾️",
        "🆘",
        "❌",
        "⭕",
        "🛑",
        "⛔",
        "📛",
        "🚫",
        "💯",
        "💢",
        "♨️",
        "🚷",
        "🚯",
        "🚳",
        "🚱",
        "🔞",
        "📵",
        "🚭",
        "❗",
        "❕",
        "❓",
        "❔",
        "‼️",
        "⁉️",
        "🔅",
        "🔆",
        "〽️",
        "⚠️",
        "🚸",
        "🔱",
        "⚜️",
        "🔰",
        "♻️",
        "✅",
        "🈯",
        "💹",
        "❇️",
        "✳️",
        "❎",
        "🌐",
        "💠",
        "Ⓜ️",
        "🌀",
        "💤",
        "🏧",
        "🚾",
        "♿",
        "🅿️",
        "🛗",
        "🈳",
        "🈂️",
        "🛂",
        "🛃",
        "🛄",
        "🛅",
        "🚹",
        "🚺",
        "🚼",
        "🚻",
        "🚮",
        "🎦",
        "📶",
        "🈁",
        "🔣",
        "ℹ️",
        "🔤",
        "🔡",
        "🔠",
        "🆖",
        "🆗",
        "🆙",
        "🆒",
        "🆕",
        "🆓",
        "0️⃣",
        "1️⃣",
        "2️⃣",
        "3️⃣",
        "4️⃣",
        "5️⃣",
        "6️⃣",
        "7️⃣",
        "8️⃣",
        "9️⃣",
        "🔟",
        "🔢",
        "#️⃣",
        "*️⃣",
        "⏏️",
        "▶️",
        "⏸️",
        "⏯️",
        "⏹️",
        "⏺️",
        "⏭️",
        "⏮️",
        "⏩",
        "⏪",
        "⏫",
        "⏬",
        "◀️",
        "🔼",
        "🔽",
        "➡️",
        "⬅️",
        "⬆️",
        "⬇️",
        "↗️",
        "↘️",
        "↙️",
        "↖️",
        "↕️",
        "↔️",
        "↪️",
        "↩️",
        "⤴️",
        "⤵️",
        "🔀",
        "🔁",
        "🔂",
        "🔄",
        "🔃",
        "🎵",
        "🎶",
        "➕",
        "➖",
        "➗",
        "✖️",
        "♾️",
        "💲",
        "💱",
        "™️",
        "©️",
        "®️",
        "〰️",
        "➰",
        "➿",
        "🔚",
        "🔙",
        "🔛",
        "🔝",
        "🔜",
        "✔️",
        "☑️",
        "🔘",
        "🔴",
        "🟠",
        "🟡",
        "🟢",
        "🔵",
        "🟣",
        "⚫",
        "⚪",
        "🟤",
        "🔺",
        "🔻",
        "🟥",
        "🟧",
        "🟨",
        "🟩",
        "🟦",
        "🟪",
        "⬛",
        "⬜",
        "🟫",
        "🔶",
        "🔷",
        "🔸",
        "🔹",
        "🔳",
        "🔲",
        "▪️",
        "▫️",
        "◾",
        "◽",
        "◼️",
        "◻️",
        "🗨️",
        "🗯️",
        "💭",
        "💬",
        "👁️‍🗨️",
        "♠️",
        "♥️",
        "♦️",
        "♣️",
        "🃏",
        "🎴",
        "🀄"
    ),

    // Note: This includes all major country flags and some subdivisions/special flags.
    // A completely exhaustive list would contain over 250 entries.
    "🎌Flags" to listOf(
        "🏁",
        "🚩",
        "🎌",
        "🏴",
        "🏳️",
        "🏳️‍🌈",
        "🏳️‍⚧️",
        "🏴‍☠️",
        "🇦🇨",
        "🇦🇩",
        "🇦🇪",
        "🇦🇫",
        "🇦🇬",
        "🇦🇮",
        "🇦🇱",
        "🇦🇲",
        "🇦🇴",
        "🇦🇶",
        "🇦🇷",
        "🇦🇸",
        "🇦🇹",
        "🇦🇺",
        "🇦🇼",
        "🇦🇽",
        "🇦🇿",
        "🇧🇦",
        "🇧🇧",
        "🇧🇩",
        "🇧🇪",
        "🇧🇫",
        "🇧🇬",
        "🇧🇭",
        "🇧🇮",
        "🇧🇯",
        "🇧🇱",
        "🇧🇲",
        "🇧🇳",
        "🇧🇴",
        "🇧🇶",
        "🇧🇷",
        "🇧🇸",
        "🇧🇹",
        "🇧🇻",
        "🇧🇼",
        "🇧🇾",
        "🇧🇿",
        "🇨🇦",
        "🇨🇨",
        "🇨🇩",
        "🇨🇫",
        "🇨🇬",
        "🇨🇭",
        "🇨🇮",
        "🇨🇰",
        "🇨🇱",
        "🇨🇲",
        "🇨🇳",
        "🇨🇴",
        "🇨🇵",
        "🇨🇷",
        "🇨🇺",
        "🇨🇻",
        "🇨🇼",
        "🇨🇽",
        "🇨🇾",
        "🇨🇿",
        "🇩🇪",
        "🇩🇬",
        "🇩🇯",
        "🇩🇰",
        "🇩🇲",
        "🇩🇴",
        "🇩🇿",
        "🇪🇦",
        "🇪🇨",
        "🇪🇪",
        "🇪🇬",
        "🇪🇭",
        "🇪🇷",
        "🇪🇸",
        "🇪🇹",
        "🇪🇺",
        "🇫🇮",
        "🇫🇯",
        "🇫🇰",
        "🇫🇲",
        "🇫🇴",
        "🇫🇷",
        "🇬🇦",
        "🇬🇧",
        "🇬🇩",
        "🇬🇪",
        "🇬🇫",
        "🇬🇬",
        "🇬🇭",
        "🇬🇮",
        "🇬🇱",
        "🇬🇲",
        "🇬🇳",
        "🇬🇵",
        "🇬🇶",
        "🇬🇷",
        "🇬🇸",
        "🇬🇹",
        "🇬🇺",
        "🇬🇼",
        "🇬🇾",
        "🇭🇰",
        "🇭🇲",
        "🇭🇳",
        "🇭🇷",
        "🇭🇹",
        "🇭🇺",
        "🇮🇨",
        "🇮🇩",
        "🇮🇪",
        "🇮🇱",
        "🇮🇲",
        "🇮🇳",
        "🇮🇴",
        "🇮🇶",
        "🇮🇷",
        "🇮🇸",
        "🇮🇹",
        "🇯🇪",
        "🇯🇲",
        "🇯🇴",
        "🇯🇵",
        "🇰🇪",
        "🇰🇬",
        "🇰🇭",
        "🇰🇮",
        "🇰🇲",
        "🇰🇳",
        "🇰🇵",
        "🇰🇷",
        "🇰🇼",
        "🇰🇾",
        "🇰🇿",
        "🇱🇦",
        "🇱🇧",
        "🇱🇨",
        "🇱🇮",
        "🇱🇰",
        "🇱🇷",
        "🇱🇸",
        "🇱🇹",
        "🇱🇺",
        "🇱🇻",
        "🇱🇾",
        "🇲🇦",
        "🇲🇨",
        "🇲🇩",
        "🇲🇪",
        "🇲🇫",
        "🇲🇬",
        "🇲🇭",
        "🇲🇰",
        "🇲🇱",
        "🇲🇲",
        "🇲🇳",
        "🇲🇴",
        "🇲🇵",
        "🇲🇶",
        "🇲🇷",
        "🇲🇸",
        "🇲🇹",
        "🇲🇺",
        "🇲🇻",
        "🇲🇼",
        "🇲🇽",
        "🇲🇾",
        "🇲🇿",
        "🇳🇦",
        "🇳🇨",
        "🇳🇪",
        "🇳🇫",
        "🇳🇬",
        "🇳🇮",
        "🇳🇱",
        "🇳🇴",
        "🇳🇵",
        "🇳🇷",
        "🇳🇺",
        "🇳🇿",
        "🇴🇲",
        "🇵🇦",
        "🇵🇪",
        "🇵🇫",
        "🇵🇬",
        "🇵🇭",
        "🇵🇰",
        "🇵🇱",
        "🇵🇲",
        "🇵🇳",
        "🇵🇷",
        "🇵🇸",
        "🇵🇹",
        "🇵🇼",
        "🇵🇾",
        "🇶🇦",
        "🇷🇪",
        "🇷🇴",
        "🇷🇸",
        "🇷🇺",
        "🇷🇼",
        "🇸🇦",
        "🇸🇧",
        "🇸🇨",
        "🇸🇩",
        "🇸🇪",
        "🇸🇬",
        "🇸🇭",
        "🇸🇮",
        "🇸🇯",
        "🇸🇰",
        "🇸🇱",
        "🇸🇲",
        "🇸🇳",
        "🇸🇴",
        "🇸🇷",
        "🇸🇸",
        "🇸🇹",
        "🇸🇻",
        "🇸🇽",
        "🇸🇾",
        "🇸🇿",
        "🇹🇦",
        "🇹🇨",
        "🇹🇩",
        "🇹🇫",
        "🇹🇬",
        "🇹🇭",
        "🇹🇯",
        "🇹🇰",
        "🇹🇱",
        "🇹🇲",
        "🇹🇳",
        "🇹🇴",
        "🇹🇷",
        "🇹🇹",
        "🇹🇻",
        "🇹🇼",
        "🇹🇿",
        "🇺🇦",
        "🇺🇬",
        "🇺🇲",
        "🇺🇳",
        "🇺🇸",
        "🇺🇾",
        "🇺🇿",
        "🇻🇦",
        "🇻🇨",
        "🇻🇪",
        "🇻🇬",
        "🇻🇮",
        "🇻🇳",
        "🇻🇺",
        "🇼🇫",
        "🇼🇸",
        "🇽🇰",
        "🇾🇪",
        "🇾🇹",
        "🇿🇦",
        "🇿🇲",
        "🇿🇼",
        "🏴󠁧󠁢󠁥󠁮󠁧󠁿",
        "🏴󠁧󠁢󠁳󠁣󠁴󠁿",
        "🏴󠁧󠁢󠁷󠁬󠁳󠁿"
    )
)