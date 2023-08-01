package com.example.mvpchatapplication.ui.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.data.models.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.Dispatchers
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
    private val goTrue: GoTrue,
) : ViewModel() {

    private var _chatsUiState = MutableStateFlow(ChatsUIState())
    val chatsUiState = _chatsUiState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

    private fun connectRealtime() = viewModelScope.launch(Dispatchers.IO) {
        kotlin.runCatching {
            val result = repository.connectRealtime()
            result
                .catch {
                    _chatsUiState.update { it.copy(isLoading = false, error = it.error) }
                }
                .onEach {
                    val rooms = it.decodeRecord<Chat>()
                    //_chatRoomsUiState.update { it.copy(isLoading = false, chatRooms = rooms) }
                }.launchIn(viewModelScope)
        }
    }

    private fun getAllChatRooms() = viewModelScope.launch {
        when (val result = repository.getAllChatRooms()) {
            is Response.Success -> {
                _chatsUiState.update { it.copy(isLoading = false, chats = result.data) }
            }

            is Response.Error -> {
                _chatsUiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.error.message ?: "Something went wrong!"
                    )
                }
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
                            isLoading = false,
                            profile = Profile(id = "", name = "")
                        )
                    }
                }
                _searchState.update { it.copy(isLoading = false, profile = null, error = "") }
            }
        }
    }

    fun insertData(message: Message) = viewModelScope.launch {
        _chatsUiState.update { it.copy(isLoading = true) }
        repository.insert(message)
    }

    /*fun connectToRealtime() {
        viewModelScope.launch {
            kotlin.runCatching {
                val realtimeChannel = supabaseClient.realtime.createChannel("#random")
                supabaseClient.realtime.connect()

                realtimeChannel.postgresChangeFlow<PostgresAction>("public") {
                    table = "messages"
                }.onEach {
                    when (it) {
                        is PostgresAction.Delete -> {

                        }

                        is PostgresAction.Insert -> {
                            Log.d("TAG", "connectToRealtime: ${it.decodeRecord<Message>()}")
                        }

                        is PostgresAction.Select -> error("Select should not be possible")
                        is PostgresAction.Update -> error("Update should not be possible")
                    }
                }.launchIn(viewModelScope)

                realtimeChannel.join()

            }.onFailure {
                it.printStackTrace()
            }
        }
    }*/


    fun chatErrorMessageShown() {
        _chatsUiState.update { it.copy(error = null) }
    }

    fun messageErrorMessageShown() {
        _chatsUiState.update { it.copy(error = null) }
    }

    /*fun isLoggedIn() = viewModelScope.launch {
        val isLoggedIn = repository.isLoggedIn()
        Log.d("TAG", "isLoggedIn: $isLoggedIn, ${supabaseClient.gotrue.currentUserOrNull()?.id}")
        if (isLoggedIn) {
            connectRealtime()
            getAllChatRooms()
        }
    }*/

    fun getMyUid() = goTrue.currentUserOrNull()?.id ?: ""

    fun setMessageSeenTrue(messageId: Int) = viewModelScope.launch {
        repository.setMessageSeenTrue(messageId)
    }

    init {
        connectRealtime()
        getAllChatRooms()
    }
}

data class ChatsUIState(
    val isLoading: Boolean = false,
    val chats: List<Chat>? = null,
    val error: String? = null,
)

data class SearchState(
    val isLoading: Boolean = false,
    val profile: Profile? = null,
    val error: String? = null,
)
