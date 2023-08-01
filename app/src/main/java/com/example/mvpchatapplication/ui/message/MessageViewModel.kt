package com.example.mvpchatapplication.ui.message

import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.map
import com.example.mvpchatapplication.BuildConfig
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Media
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.utils.MessageType
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val client: SupabaseClient,
) : ViewModel() {

    private var _chatUiState = MutableStateFlow(ChatUIState())
    val chatUiState = _chatUiState.asStateFlow()

    private var _messageState = MutableStateFlow(MessageState())
    val messages = _messageState.asStateFlow()

    var receiverIdForChat = ""

    fun connectRealtime(roomId: Int) = viewModelScope.launch(Dispatchers.IO) {
        kotlin.runCatching {
            val result = repository.connectRealtime(roomId)
            /*result
                .catch { error ->
                    _chatUiState.update { it.copy(messages = it.messages?.plus(MessageState(isLoading = false, error = error.localizedMessage))) }
                }
                .onEach {
                    val message = it.decodeRecord<Message>()
                    _chatUiState.update { it.copy(isLoading = false, messages = it.messages?.plus(
                        MessageState(isLoading = false, message = message)
                    )) }

                    if(message.authorId != getMyUid()) {
                        setMessageSeenTrue(message.id)
                    }
                }.launchIn(viewModelScope)*/
        }
    }

    fun getAllChat(chatId: Int) = viewModelScope.launch {
        _chatUiState.update { it.copy(isLoading = true) }
        repository.getAllMessages(chatId)
            .catch {  }
            .collectLatest { pagingData ->
            _chatUiState.update { it.copy(isLoading = false, messages = pagingData.map { MessageState(message = it) }) }
        }
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
                    when (val result = repository.insert(message)) {
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
            when (val result = repository.insert(message)) {
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

    fun disconnectFromRealtime() {
        viewModelScope.launch {
            kotlin.runCatching {
                client.realtime.disconnect()
            }
        }
    }

    fun chatErrorShown() {
        _chatUiState.update { it.copy(error = null) }
    }

    fun messageErrorShown() {
        _messageState.update { it.copy(error = null) }
    }

    fun deleteChat(roomId: Int) = viewModelScope.launch {
        repository.deleteChat(roomId)
    }

    fun getChat(receiverId: String) = viewModelScope.launch {
        when (val response = repository.getChat(receiverId)) {
            is Response.Success -> {
                Log.d("TAG", "getChat: ${response.data}")
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

/*    @OptIn(SupabaseExperimental::class)
    fun uploadImage(media: Media, roomId: Int) = viewModelScope.launch {
        val bucket = client.storage["images"]
        bucket.uploadAsFlow(media.name, Uri.parse(media.uri).toFile(), upsert = true)
            .collect { status ->
                when (status) {
                    is UploadStatus.Progress -> {
                        _uploadState.update {
                            it.copy(
                                isLoading = false,
                                progress = (status.totalBytesSend.toFloat() / status.contentLength * 100)
                            )
                        }
                    }

                    is UploadStatus.Success -> {
                        val url = client.storage["images"].publicUrl(media.name)
                        insertData(
                            Message(
                                content = url,
                                type = MessageType.IMAGE,
                                chatId = roomId
                            )
                        )
                    }
                }
            }
    }*/

    @OptIn(SupabaseExperimental::class)
    fun uploadVideo(media: Media, roomId: Int) = viewModelScope.launch {
        val bucket = client.storage["videos"]
        bucket.uploadAsFlow(media.name, Uri.parse(media.uri).toFile(), upsert = true).collect {
            when (it) {
                is UploadStatus.Progress -> println("Progress: ${it.totalBytesSend.toFloat() / it.contentLength * 100}%")
                is UploadStatus.Success -> {
                    val url = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/${media.name}"
                    insertData(
                        Message(
                            content = url,
                            type = MessageType.VIDEO,
                            chatId = roomId
                        )
                    )
                }
            }
        }
    }

    private fun uploadMediaMessage(message: Message) = viewModelScope.launch {
        _messageState.update { it.copy(isLoading = true) }
        repository.insert(message)
    }

    fun getMyUid() = client.gotrue.currentUserOrNull()?.id ?: ""

    fun setMessageSeenTrue(chatId: Int) = viewModelScope.launch {
        repository.setMessageSeenTrue(chatId)
    }
}

data class UploadState(
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val isSuccess: Boolean = false,
    val error: String? = null
)

data class ChatUIState(
    val isLoading: Boolean = false,
    val messages: PagingData<MessageState>? = null,
    val error: String? = null
)

data class MessageState(
    val isLoading: Boolean = false,
    val message: Message? = null,
    val error: String? = null
)
