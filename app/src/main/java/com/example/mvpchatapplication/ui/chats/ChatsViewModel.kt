package com.example.mvpchatapplication.ui.chats

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.di.ChatChannel
import com.example.mvpchatapplication.utils.isJoinedOrJoining
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ChatsRepository,
    private val client: SupabaseClient,
    @ChatChannel
    private val channel: RealtimeChannel
) : ViewModel() {

    private var _chatsUiState = MutableStateFlow(ChatsUIState())
    val chatsUiState = _chatsUiState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

    private var lastLoadedItemId =  0
    var isLastPage = false

    val chatList = mutableListOf<Chat>()

    private var chatActionJob: Job? = null

    private fun connectRealtime() = viewModelScope.launch(Dispatchers.IO) {
        kotlin.runCatching {
            Log.d("TAG", "connectRealtime chat: ")
            chatActionJob?.cancel()
            chatActionJob = repository.connectRealtime().catch {
                _chatsUiState.update { it.copy(isLoading = false, error = it.error) }
            }.onEach {
                when (it) {
                    is PostgresAction.Delete -> {
                        Log.d("TAG", "connectRealtime hjgjghg: ${it.decodeOldRecord<Chat>()}")
                        val index =
                            chatList.indexOfFirst { chat -> it.decodeOldRecord<Chat>().id == chat.id }
                        if (index != -1) {
                            chatList.removeAt(index)
                            _chatsUiState.value =
                                ChatsUIState(chatAction = ChatAction.Delete(index))
                        }
                    }

                    is PostgresAction.Insert -> {
                        val chat = it.decodeRecord<Chat>()
                        Log.d("TAG", "connectRealtime inserted: $chat")
                        getProfileInfo(chat)
                    }

                    is PostgresAction.Select -> {

                    }

                    is PostgresAction.Update -> {
                        val chat = it.decodeRecord<Chat>()
                        getMessageForChat(chat)
                        Log.d("TAG", "connectRealtime updated: $chat")

                    }
                }
            }.launchIn(viewModelScope)
        }.onFailure {
            Log.d("TAG", "connectRealtime chat: ${it.message}")
            _chatsUiState.update { it.copy(isLoading = false, error = it.error) }
        }
    }

    fun getAllChats() = viewModelScope.launch {

        _chatsUiState.update { it.copy(isLoading = true) }
        when (val response = repository.getAllChats(lastChatId = lastLoadedItemId)) {
            is Response.Error -> {
                if (response.error is HttpRequestException) {
                    _chatsUiState.update {
                        it.copy(
                            isLoading = false,
                            error = R.string.network_error
                        )
                    }
                } else _chatsUiState.update {
                    it.copy(
                        isLoading = false,
                        error = R.string.other_errors
                    )
                }
            }

            is Response.Success -> {
                Log.d("TAG", "getAllChatRooms: ${response.data}")
                chatList.clear()
                chatList.addAll(response.data)
                isLastPage =
                    response.data.isEmpty() || response.data.size < ChatsRepository.PAGE_SIZE
                _chatsUiState.update {
                    it.copy(isLoading = false, chats = response.data)
                }
            }
        }
    }

    fun nextPage(lastChatId: Int) {
        Log.d("TAG", "nextPage: $lastChatId")

        if (chatsUiState.value.isLoading || lastLoadedItemId == lastChatId) return
        lastLoadedItemId = lastChatId

        viewModelScope.launch {
            _chatsUiState.update { it.copy(isLoading = true) }
            when (val response = repository.getAllChats(lastChatId)) {
                is Response.Success -> {
                    chatList.addAll(response.data)
                    isLastPage =
                        response.data.isEmpty() || response.data.size < ChatsRepository.PAGE_SIZE
                    _chatsUiState.update {
                        it.copy(isLoading = false, chats = response.data)
                    }
                }

                is Response.Error -> {
                    Log.d("TAG", "nextPage: ${response.error.message}")
                    if (response.error is HttpRequestException) {
                        _chatsUiState.update {
                            it.copy(
                                isLoading = false,
                                error = R.string.network_error
                            )
                        }
                    } else _chatsUiState.update {
                        it.copy(
                            isLoading = false,
                            error = R.string.other_errors
                        )
                    }

                }
            }
        }
    }

    fun resetChatAction() {
        _chatsUiState.update { it.copy(chatAction = null) }
    }

    private suspend fun getMessageForChat(chat: Chat) {
        when (val response = repository.getMessageById(chat.lastMessageId!!)) {
            is Response.Error -> {

            }

            is Response.Success -> {
                val message = response.data
                val newList = chatList.toList()
                chatList.clear()
                chatList.addAll(newList.map {
                    if (it.id == message.chatId) {
                        it.copy(
                            lastMessageId = message.id,
                            lastMessage = message.content,
                            lastMessageType = message.type,
                            lastMessageAuthorId = message.authorId,
                            lastMessageSeen = message.seen,
                            updatedAt = chat.updatedAt
                        )
                    } else it
                }.sortedByDescending { it.updatedAt }
                )
                Log.d("TAG", "connectRealtime: $chatList")
                _chatsUiState.value = ChatsUIState(chatAction = ChatAction.Update)
            }
        }
    }

    private suspend fun getProfileInfo(chat: Chat) {
        val otherUserId = if (chat.user1 == getMyUid()) chat.user2 else chat.user1
        when (val response = repository.getProfileById(otherUserId!!)) {
            is Response.Error -> {

            }

            is Response.Success -> {
                val profile = response.data
                val newChat = chat.copy(
                    otherUserName = profile.name,
                    otherUserProfileImage = profile.profileImageUrl
                )
                chatList.add(0, newChat)
                _chatsUiState.value = ChatsUIState(chatAction = ChatAction.Insert)
            }
        }
    }

    fun searchUserWithEmail(email: String) = viewModelScope.launch {
        when (val result = repository.searchUserWithEmail(email)) {
            is Response.Success -> {
                _searchState.update { it.copy(isLoading = false, profile = result.data) }
            }

            is Response.Error -> {
                if (result.error is NoSuchElementException) {
                    _searchState.update {
                        it.copy(
                            isLoading = false, profile = Profile(id = "", name = "")
                        )
                    }
                } else _searchState.update {
                    it.copy(
                        isLoading = false, profile = null, error = ""
                    )
                }
            }
        }
    }


    fun chatErrorMessageShown() {
        _chatsUiState.update { it.copy(error = null) }
    }

    fun resetSearchState() {
        _searchState.value = SearchState()
    }

    fun getMyUid() = savedStateHandle.get<String>("uid") ?: run {
        val uid = client.gotrue.currentUserOrNull()?.id ?: ""
        savedStateHandle["uid"] = uid
        uid
    }

    fun setMessageSeenTrue(messageId: Int) = viewModelScope.launch {
        repository.setMessageSeenTrue(messageId)
    }

    init {
        client.realtime.status.onEach {
            Log.d("Statuspp", "Realtime Message: $it")
            if (it == Realtime.Status.CONNECTED && !channel.isJoinedOrJoining()) {
                connectRealtime()
            }
        }.launchIn(viewModelScope)

        client.gotrue.sessionStatus.onEach {
            Log.d("Statuspp", "Auth Chat: $it")
            if (it is SessionStatus.Authenticated) {
                if(chatList.isEmpty()) {
                    getAllChats()
                }
            }
        }.launchIn(viewModelScope)
    }
}

data class ChatsUIState(
    val isLoading: Boolean = false,
    val chats: List<Chat>? = null,
    val chatAction: ChatAction? = null,
    val error: Int? = null,
)

sealed class ChatAction {
    data object Update : ChatAction()
    data object Insert : ChatAction()
    data class Delete(val index: Int) : ChatAction()
}

data class SearchState(
    val isLoading: Boolean = false,
    val profile: Profile? = null,
    val error: String? = null,
)


