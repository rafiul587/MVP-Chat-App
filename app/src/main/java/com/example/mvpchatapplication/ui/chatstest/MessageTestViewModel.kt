package com.example.mvpchatapplication.ui.chatstest

import android.util.Log
import android.util.Pair
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Media
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.di.MessageChannel
import com.example.mvpchatapplication.ui.message.MessageRepository
import com.example.mvpchatapplication.utils.MessageContent
import com.example.mvpchatapplication.utils.MessageDate
import com.example.mvpchatapplication.utils.MessageViewType
import com.example.mvpchatapplication.utils.toDayOrDateString
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.createChannel
import io.github.jan.supabase.realtime.decodeJoinsAs
import io.github.jan.supabase.realtime.decodeLeavesAs
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@HiltViewModel
class MessageTestViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val client: SupabaseClient,
    @MessageChannel
    private val channel: RealtimeChannel
) : ViewModel() {

    private var _chatUiState = MutableStateFlow(ChatUIState())
    val chatUiState = _chatUiState.asStateFlow()

    private var _messageState: MutableStateFlow<MessageState<Message>?> = MutableStateFlow(null)
    val messages = _messageState.asStateFlow()

    var page = 1

    private var recipeListScrollPosition = 0

    private var receiverIdForChat = ""
    private var chatId = 0

    val messageList = mutableListOf<MessageViewType>()
    val separators = mutableListOf<String>()
    val connectedUsers = mutableSetOf<PresenceState>()

    private fun connectRealtime() = viewModelScope.launch(Dispatchers.IO) {
        kotlin.runCatching {
            Log.d("TAG", "connectRealtime222: ")

            val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
            }
            val presenceChangeFlow = channel.presenceChangeFlow()

//in a new coroutine (or use Flow.onEach().launchIn(scope)):
            presenceChangeFlow.onEach {
                connectedUsers += it.decodeJoinsAs()
                connectedUsers -= it.decodeLeavesAs()
                Log.d("TAG", "connectRealtime: $connectedUsers")
            }.launchIn(viewModelScope)
            //in a new coroutine (or use Flow.onEach().launchIn(scope)):
            changeFlow.onEach { data ->
                val message = data.decodeRecord<Message>()

                if (message.authorId != getMyUid()) {
                    setMessageSeenTrue(message.id)
                }
                _messageState.value = MessageState.Success(message)
            }.launchIn(viewModelScope)
            channel.join()
            channel.track(PresenceState(uid = getMyUid()))
        }.onFailure {
            Log.d("TAG", "connectRealtime failed: ${it.message}")
        }
    }

    fun getAllChat(chatId: Int) = viewModelScope.launch {
        _chatUiState.update { it.copy(isLoading = true) }
        when (val response = repository.getAllChat(chatId, page - 1, PAGE_SIZE)) {

            is Response.Error -> {
                _chatUiState.update { it.copy(isLoading = false, error = "Something went wrong!") }
            }

            is Response.Success -> {
                processList(response.data)
                _chatUiState.update { it.copy(isLoading = false, messages = response.data) }
            }
        }
    }

    fun nextPage(chatId: Int) {
        viewModelScope.launch {
            // prevent duplicate event due to recompose happening to quickly
            if ((recipeListScrollPosition) >= (page * PAGE_SIZE - 1)) {
                _chatUiState.update { it.copy(isLoading = true) }
                incrementPage()
                Log.d("TAG", "nextPage: triggered: ${page}")

                if (page > 1) {

                    Log.d("TAG", "search: appending")
                    when (val response = repository.getAllChat(chatId, page - 1, PAGE_SIZE)) {
                        is Response.Success -> {
                            processList(response.data)
                            _chatUiState.update {
                                it.copy(
                                    isLoading = false,
                                    messages = response.data
                                )
                            }

                        }

                        is Response.Error -> {
                            _chatUiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Something went wrong!"
                                )
                            }
                        }
                    }

                }
            }
        }
    }


    private fun processList(messages: List<Message>) {
        val items: MutableList<MessageViewType> = ArrayList()
        var date: String = ""

        messages.forEachIndexed { index, event ->
            val formattedTime = event.createdAt!!.toDayOrDateString()
            Log.d("TAG", "processList: $formattedTime")
            if (formattedTime != date) {
                date = formattedTime
                //when next page's data loaded checking if the data is from the last dates of the previous page
                //Suppose last data was from today and next pages first data is also from today so I removed the first today
                if (separators.contains(date)) {
                    messageList.remove(MessageDate(date))
                } else {
                    //This check is basically to skip the first date so that I can add it after all the message of that date is loaded
                    //This is required because I want to make {Hi, Hello, Today, Nice, Yesterday} instead of {Today, Hi, Hello, Yesterday, Nice} because this list will be reversed in recyclerView
                    if (index != 0) {
                        items.add(MessageDate(separators.last()))
                    }
                    //add the date to the separator everytime if not already existed
                    separators.add(date)
                }
            }
            items.add(MessageContent(event))
        }
        //Here I add the last message from the separator when loop finished.
        items.add(MessageDate(separators.last()))
        Log.d("TAG", "setUpStateListeners processList: ${messageList.lastIndex}")
        messageList.addAll(items)
        val newList = messageList.distinct()
        messageList.clear()
        messageList.addAll(newList)
    }

    fun addMessage(message: Message) {
        val formattedTime = message.createdAt!!.toDayOrDateString()
        if (separators.last() == formattedTime) {
            messageList.add(0, MessageContent(message))
        } else {
            messageList.add(0, MessageContent(message))
            messageList.add(1, MessageDate(formattedTime))
            separators.add(formattedTime)
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
        _messageState.value = MessageState.Loading
        if (receiverIdForChat.isNotEmpty()) {
            when (val response = repository.createChat(receiverIdForChat)) {
                is Response.Error ->
                    _messageState.value = MessageState.Error(response.error)

                is Response.Success -> {
                    receiverIdForChat = ""
                    when (val result = repository.insert(message.copy(chatId = response.data.id))) {
                        is Response.Error -> {
                            _messageState.value = MessageState.Error(result.error)
                        }

                        else -> {}
                    }
                }
            }
        } else {
            when (val result =
                repository.insert(if (message.chatId == -1) message.copy(chatId = chatId) else message)) {
                is Response.Error -> {
                    _messageState.value = MessageState.Error(result.error)
                }

                else -> {}
            }
        }
    }


    fun chatErrorShown() {
        _chatUiState.update { it.copy(error = null) }
    }

    fun messageErrorShown() {
        _messageState.value = null
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
                connectRealtime()
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
        //viewModelScope.launch { repository.leaveChannel() }
    }

    init {
        connectRealtime()
    }

    companion object {
        const val PAGE_SIZE = 15
    }
}

data class ChatUIState(
    val isLoading: Boolean = false,
    val messages: List<Message>? = null,
    val error: String? = null,
    val deleted: Boolean = false
)

sealed class MessageState<out R> {
    object Loading : MessageState<Nothing>()
    data class Success<out T>(val data: T) : MessageState<T>()
    class Error(val error: Throwable) : MessageState<Nothing>()
}
@Serializable
data class PresenceState(val uid: String)
