package com.example.mvpchatapplication.ui.message

import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Media
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.di.MessageChannel
import com.example.mvpchatapplication.utils.AdapterNotifyType
import com.example.mvpchatapplication.utils.MessageContent
import com.example.mvpchatapplication.utils.MessageDate
import com.example.mvpchatapplication.utils.MessageLoadStatus
import com.example.mvpchatapplication.utils.MessageViewType
import com.example.mvpchatapplication.utils.saveFileBitmapToFile
import com.example.mvpchatapplication.utils.toDayOrDateString
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.decodeJoinsAs
import io.github.jan.supabase.realtime.decodeLeavesAs
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceChangeFlow
import io.github.jan.supabase.realtime.track
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject


@HiltViewModel
class MessageViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val client: SupabaseClient,
    @MessageChannel
    private val channel: RealtimeChannel
) : ViewModel() {

    private var _messageUiState = MutableStateFlow(ChatUIState())
    val messageUiState = _messageUiState.asStateFlow()

    private var _insertState: MutableStateFlow<InsertState<Message>?> = MutableStateFlow(null)
    val insertState = _insertState.asStateFlow()

    private var _uploadState = MutableStateFlow<UploadState?>(null)
    val uploadState = _uploadState.asStateFlow()

    private var messageLoadStatus: MessageLoadStatus? = null

    val messageList = mutableListOf<MessageViewType>()
    val separators = mutableListOf<String>()
    val connectedUsers = mutableSetOf<PresenceState>()

    var lastLoadedItemId = 0
    var isLastPage = false
    private var chatId: Int = -1

    private fun connectRealtime() = viewModelScope.launch(Dispatchers.IO) {
        kotlin.runCatching {

            val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
            }
            val presenceChangeFlow = channel.presenceChangeFlow()

            presenceChangeFlow.onEach {
                connectedUsers += it.decodeJoinsAs()
                connectedUsers -= it.decodeLeavesAs()
                Log.d("TAG", "connectRealtime: $connectedUsers")
            }.launchIn(viewModelScope)
            //in a new coroutine (or use Flow.onEach().launchIn(scope)):
            changeFlow.onEach { data ->
                val message = data.decodeRecord<Message>()
                Log.d("TAG", "connectRealtime: $message")
                if (message.authorId != getMyUid()) {
                    setMessageSeenTrue(message.id)
                }
                getMessageById(message.id)
            }.launchIn(viewModelScope)
            channel.join()
            channel.track(PresenceState(uid = getMyUid()))

        }.onFailure {
            Log.d("TAG", "connectRealtime failed: ${it.message}")
        }
    }

    fun getAllMessages(chatId: Int, receiverId: String) = viewModelScope.launch {
        _messageUiState.update { it.copy(isLoading = true) }
        when (val response = repository.getAllMessages(chatId)) {

            is Response.Error -> {
                _messageUiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Something went wrong!"
                    )
                }
                messageLoadStatus = MessageLoadStatus.Failed(receiverId)
            }

            is Response.Success -> {
                processList(response.data)
                messageLoadStatus = MessageLoadStatus.Success(chatId)
                _messageUiState.update { it.copy(isLoading = false, messages = response.data) }
            }
        }
    }

    fun nextPage(chatId: Int, lastMessageId: Int) {
        Log.d("TAG", "nextPage: $lastMessageId")

        if (messageUiState.value.isLoading || messageList.size <= MessageRepository.PAGE_SIZE || lastLoadedItemId == lastMessageId) return
        lastLoadedItemId = lastMessageId

        viewModelScope.launch {
            _messageUiState.update { it.copy(isLoading = true) }
            when (val response = repository.getAllMessages(chatId, lastMessageId)) {
                is Response.Success -> {
                    processList(response.data)
                    _messageUiState.update {
                        it.copy(isLoading = false, messages = response.data)
                    }
                }

                is Response.Error -> {
                    Log.d("TAG", "nextPage: ${response.error.message}")
                    _messageUiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Something went wrong!"
                        )
                    }

                }
            }
        }
    }


    private fun processList(messages: List<Message>) {
        isLastPage = messages.isEmpty() || messages.size < MessageRepository.PAGE_SIZE
        if (messages.isEmpty()) return
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
    }

    fun addMessage(message: Message): AdapterNotifyType {
        val formattedTime = message.createdAt!!.toDayOrDateString()
        return if (separators.isNotEmpty() && separators.first() == formattedTime) {
            messageList.add(0, MessageContent(message))
            AdapterNotifyType.MessageOfExistingDate
        } else {
            messageList.add(0, MessageContent(message))
            messageList.add(1, MessageDate(formattedTime))
            separators.add(0, formattedTime)
            AdapterNotifyType.MessageWithNewDate
        }
    }

    fun insertData(message: Message) = viewModelScope.launch {
        _insertState.value = InsertState.Loading
        Log.d("TAG", "insertData: $messageLoadStatus")
        when (val status = messageLoadStatus) {
            is MessageLoadStatus.NotFound -> {
                when (val response = repository.createChat(status.receiverId)) {
                    is Response.Error -> {
                        _insertState.value = InsertState.Error("Failed to create chat!")
                    }

                    is Response.Success -> {
                        chatId = response.data.id
                        when (repository.insert(if (message.chatId == -1) message.copy(chatId = response.data.id) else message)) {
                            is Response.Error -> {
                                _insertState.value = InsertState.Error("Message send failed!")
                            }

                            else -> {}
                        }
                    }
                }
            }

            is MessageLoadStatus.Failed -> {
                getChat(status.receiverId)
            }

            is MessageLoadStatus.Success -> {
                when (repository.insert(if (message.chatId == -1) message.copy(chatId = status.chatId) else message)) {
                    is Response.Error -> {
                        _insertState.value = InsertState.Error("Message send failed!")
                    }

                    else -> {}
                }
            }

            else -> {}
        }
    }


    fun messageUiErrorShown() {
        _messageUiState.update { it.copy(error = null) }
    }

    fun insertionDone() {
        _insertState.value = null
    }

    fun deleteChat() = viewModelScope.launch {
        Log.d("TAG", "deleteChat: $chatId")
        if(chatId != -1) {
            _messageUiState.update { it.copy(isLoading = true) }
            when (repository.deleteChat(if (chatId == -1) this@MessageViewModel.chatId else chatId)) {
                is Response.Error -> {
                    _messageUiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Deletion Failed!"
                        )
                    }
                }

                is Response.Success -> {
                    _messageUiState.update { it.copy(isLoading = false, deleted = true) }
                }
            }
        }
    }

    private suspend fun getMessageById(messageId: Int) {

        when (val response = repository.getMessageById(messageId)) {
            is Response.Error -> {
                _insertState.value = InsertState.Error("Message send failed!");
            }

            is Response.Success -> {
                _insertState.value = InsertState.Success(response.data);
            }
        }
    }

    /**
     * This method will be called if you enter the message page by searching profile to check if there is already a chat with the user.
     * If there is no chat with the user then it will save the receiverId so that in first message it can create a chat with the id.
     * And if there is already a chat with the user then it will save the [chatId], will get all messages of the chat and connect realtime as well.
     */
    fun getChat(receiverId: String) = viewModelScope.launch {
        when (val response = repository.getChat(receiverId)) {
            is Response.Success -> {
                getAllMessages(response.data.id, receiverId)
                chatId = response.data.id
            }

            is Response.Error -> {
                messageLoadStatus = if (response.error is NoSuchElementException) {
                    MessageLoadStatus.NotFound(receiverId)
                } else {
                    _messageUiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Message Load Failed!"
                        )
                    }
                    MessageLoadStatus.Failed(receiverId)
                }
            }
        }
    }

    fun getMyUid() = client.gotrue.currentUserOrNull()?.id ?: ""

    private fun setMessageSeenTrue(chatId: Int) = viewModelScope.launch {
        repository.setMessageSeenTrue(chatId)
    }

    @OptIn(SupabaseExperimental::class)
    fun uploadImage(media: Media) = viewModelScope.launch {
        saveFileBitmapToFile(Uri.parse(media.uri).toFile())?.let {
            val bucket = client.storage["images"]
            bucket.uploadAsFlow(media.name, it)
                .catch { UploadState.Error("Upload Failed!") }
                .collect { status ->
                    when (status) {
                        is UploadStatus.Progress -> {
                            _uploadState.value =
                                UploadState.Uploading(progress = (status.totalBytesSend.toFloat() / status.contentLength * 100))
                        }

                        is UploadStatus.Success -> {
                            _uploadState.value = UploadState.Success
                        }
                    }
                }
        }
    }

    @OptIn(SupabaseExperimental::class)
    fun uploadVideo(media: Media) = viewModelScope.launch {
        val bucket = client.storage["videos"]
        bucket.uploadAsFlow(media.name, Uri.parse(media.uri).toFile())
            .catch { UploadState.Error("Upload Failed!") }
            .collect { status ->
                when (status) {
                    is UploadStatus.Progress -> {
                        _uploadState.value =
                            UploadState.Uploading(progress = (status.totalBytesSend.toFloat() / status.contentLength * 100))
                    }

                    is UploadStatus.Success -> {
                        _uploadState.value = UploadState.Success
                    }
                }
            }
    }

    fun uploadDone() {
        _uploadState.value = null
    }

    fun setChatId(id: Int){
        chatId = id
    }
    init {
        connectRealtime()
    }
}

data class ChatUIState(
    val isLoading: Boolean = false,
    val messages: List<Message>? = null,
    val error: String? = null,
    val deleted: Boolean = false
)

sealed class InsertState<out R> {
    object Loading : InsertState<Nothing>()
    data class Success<out T>(val data: T) : InsertState<T>()
    class Error(val error: String) : InsertState<Nothing>()
}

sealed class UploadState {
    object Success : UploadState()
    class Uploading(val progress: Float = 0f) : UploadState()
    class Error(val error: String) : UploadState()
}


@Serializable
data class PresenceState(val uid: String)
