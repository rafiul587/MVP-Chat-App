package com.example.mvpchatapplication.ui.message

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Media
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.utils.MessageViewType
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val client: SupabaseClient,
) : ViewModel() {

    private var _chatUiState = MutableStateFlow(ChatUIState())
    val chatUiState = _chatUiState.asStateFlow()

    private var _messageState = MutableStateFlow(MessageState())
    val messages = _messageState.asStateFlow()

    var page = 1

    private var recipeListScrollPosition = 0

    private var receiverIdForChat = ""
    private var chatId = 0

    fun connectRealtime(chatId: Int) = viewModelScope.launch(Dispatchers.IO) {
        kotlin.runCatching {
            Log.d("TAG", "connectRealtime222: ")
            val result = repository.connectRealtime()
            result
                .catch {
                    Log.d("TAG", "connectRealtime: ${it.message}")
                    _messageState.update {
                        it.copy(
                            isLoading = false,
                            error = "Message send failed!"
                        )
                    }
                }
                .onEach {
                    val message = it.decodeRecord<Message>()
                    Log.d("TAG", "connectRealtime: $message")
                    if (message.authorId != getMyUid()) {
                        setMessageSeenTrue(message.id)
                    }

                    kotlin.runCatching {
                        repository.invalidate()
                    }
                }.launchIn(viewModelScope)
        }.onFailure {
            Log.d("TAG", "connectRealtime failed: ${it.message}")
        }
    }

    fun getAllChat(chatId: Int) = viewModelScope.launch {
        _chatUiState.update { it.copy(isLoading = true) }
        repository.getAllMessages(chatId).catch {
            _chatUiState.update { it.copy(isLoading = false, error = "Something went wrong!") }
        }
            .collectLatest { data ->
                data.map {
                    Log.d("TAG", "getAllChat: $it")
                }
                _chatUiState.update { it.copy(isLoading = false, messages = data) }
            }
    }


    private fun incrementPage() {
        page++
    }

    fun onChangeRecipeScrollPosition(position: Int) {
        recipeListScrollPosition = position
    }

    /**
     * Called when a new search is executed.
     */
    private fun resetSearchState() {
        page = 0
        onChangeRecipeScrollPosition(0)
    }

    fun insertData(message: Message) = viewModelScope.launch {
        _messageState.update { it.copy(isLoading = true) }
        if (receiverIdForChat.isNotEmpty()) {
            when (val response = repository.createChat(receiverIdForChat)) {
                is Response.Error ->
                    _messageState.update {
                        it.copy(
                            isLoading = false,
                            error = response.error.message
                        )
                    }

                is Response.Success -> {
                    receiverIdForChat = ""
                    when (val result = repository.insert(message.copy(chatId = response.data.id))) {
                        is Response.Error -> {
                            _messageState.update {
                                it.copy(
                                    isLoading = false,
                                    error = result.error.message
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }
        } else {
            when (val result =
                repository.insert(if (message.chatId == -1) message.copy(chatId = chatId) else message)) {
                is Response.Error -> {
                    _messageState.update {
                        it.copy(
                            isLoading = false,
                            error = result.error.message
                        )
                    }
                }

                else -> {}
            }
        }
    }


    fun chatErrorShown() {
        _chatUiState.update { it.copy(error = null) }
    }

    fun messageErrorShown() {
        _messageState.update { it.copy(error = null) }
    }

    fun deleteChat(chatId: Int) = viewModelScope.launch {
        _chatUiState.update { it.copy(isLoading = true) }
        when (val response = repository.deleteChat(chatId)) {
            is Response.Error -> {
                _chatUiState.update { it.copy(isLoading = false, error = "Deletion Failed!") }
            }

            is Response.Success -> {
                _chatUiState.update { it.copy(isLoading = false, deleted = true) }
            }
        }
    }

    fun getChat(receiverId: String) = viewModelScope.launch {
        when (val response = repository.getChat(receiverId)) {
            is Response.Success -> {
                Log.d("TAG", "getChat: ${response.data}")
                chatId = response.data.id
                getAllChat(response.data.id)
                connectRealtime(response.data.id)
            }

            is Response.Error -> {
                if (response.error is NoSuchElementException) {
                    receiverIdForChat = receiverId;
                }
            }
        }
    }

    fun createChat(receiverId: String) = viewModelScope.launch {
        repository.createChat(receiverId)
    }

    fun getMyUid() = client.gotrue.currentUserOrNull()?.id ?: ""

    private fun setMessageSeenTrue(chatId: Int) = viewModelScope.launch {
        repository.setMessageSeenTrue(chatId)
    }

    fun leaveChannel() {

    }

    companion object {
        const val PAGE_SIZE = 5
    }
}

data class ChatUIState(
    val isLoading: Boolean = false,
    val messages: PagingData<MessageViewType>? = null,
    val error: String? = null,
    val deleted: Boolean = false
)

data class MessageState(
    val isLoading: Boolean = false,
    val message: Message? = null,
    val error: String? = null
)
