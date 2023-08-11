package com.example.mvpchatapplication.ui.chats

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.data.models.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
) : ViewModel() {

    private var _chatsUiState = MutableStateFlow(ChatsUIState())
    val chatsUiState = _chatsUiState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

    private fun connectRealtime() = viewModelScope.launch(Dispatchers.IO) {
        kotlin.runCatching {
            Log.d("TAG", "connectRealtime chat: ")
            repository.connectRealtime()
                .catch {
                    _chatsUiState.update { it.copy(isLoading = false, error = it.error) }
                }
                .onEach {
                    when (it) {
                        is PostgresAction.Delete -> {
                            repository.invalidate()
                        }

                        is PostgresAction.Insert -> {
                            repository.invalidate()
                        }

                        is PostgresAction.Select -> {

                        }

                        is PostgresAction.Update -> {
                            val chat = it.decodeRecord<Chat>()
                            when (val response = repository.getMessageById(chat.lastMessageId!!)) {
                                is Response.Error -> {

                                }

                                is Response.Success -> {
                                    val message = response.data
                                    Log.d("TAG", "connectRealtime: $message")
                                    val pagindData = chatsUiState.value.chats?.map {
                                        if (it.id == chat.id) {
                                            val newChat = it.copy(
                                                lastMessage = message.content,
                                                lastMessageType = message.type,
                                                lastMessageSeen = message.seen,
                                                updatedAt = chat.updatedAt
                                            )
                                            //newList.add(newChat)
                                            newChat
                                        }
                                        else {
                                            //newList.add(it)
                                            it
                                        }
                                    }
                                    _chatsUiState.update {
                                        it.copy(
                                            isLoading = false,
                                            chats = pagindData)
                                    }

                                }
                            }
                        }
                    }
                    repository::invalidate
                }
                .launchIn(viewModelScope)
        }.onFailure {
            Log.d("TAG", "connectRealtime chat: ${it.message}")
        }
    }

    private fun getAllChatRooms() = viewModelScope.launch {
        _chatsUiState.update { it.copy(isLoading = true) }
        repository.getAllChatRooms()
            .cachedIn(viewModelScope)
            .distinctUntilChanged()
            .catch { error ->
                _chatsUiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Something went wrong!"
                    )
                }
            }
            .collect { data ->
                _chatsUiState.update {
                    it.copy(isLoading = false, chats = data)
                }
            }
    }

    fun getMessageForChat(messageId: Int) {

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
                            isLoading = false,
                            profile = Profile(id = "", name = "")
                        )
                    }
                }
                _searchState.update { it.copy(isLoading = false, profile = null, error = "") }
            }
        }
    }


    fun chatErrorMessageShown() {
        _chatsUiState.update { it.copy(error = null) }
    }

    fun resetSearchState() {
        _searchState.value = SearchState()
    }

    fun getMyUid() = client.gotrue.currentUserOrNull()?.id ?: ""

    fun setMessageSeenTrue(messageId: Int) = viewModelScope.launch {
        repository.setMessageSeenTrue(messageId)
    }

    init {
        getAllChatRooms()
        client.realtime.status.onEach {
            if (it == Realtime.Status.CONNECTED) {
                connectRealtime()
            }
        }.launchIn(viewModelScope)
    }
}

data class ChatsUIState(
    val isLoading: Boolean = false,
    val chats: PagingData<Chat>? = null,
    val error: String? = null,
)

data class SearchState(
    val isLoading: Boolean = false,
    val profile: Profile? = null,
    val error: String? = null,
)
