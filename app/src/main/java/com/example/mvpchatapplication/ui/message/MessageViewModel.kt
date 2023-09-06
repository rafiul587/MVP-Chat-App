package com.example.mvpchatapplication.ui.message

import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvpchatapplication.R
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
import com.example.mvpchatapplication.utils.isJoinedOrJoining
import com.example.mvpchatapplication.utils.saveFileBitmapToFile
import com.example.mvpchatapplication.utils.toDayOrDateString
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.decodeJoinsAs
import io.github.jan.supabase.realtime.decodeLeavesAs
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.lang.Exception
import javax.inject.Inject


@HiltViewModel
class MessageViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val client: SupabaseClient,
    @MessageChannel private val channel: RealtimeChannel
) : ViewModel() {

    private var _messageUiState = MutableStateFlow(MessageUIState())
    val messageUiState = _messageUiState.asStateFlow()

    private var _insertState: MutableStateFlow<InsertState<Message>?> = MutableStateFlow(null)
    val insertState = _insertState.asStateFlow()

    private var _uploadState = MutableStateFlow<UploadState?>(null)
    val uploadState = _uploadState.asStateFlow()

    private var _realtimeConnecting = MutableStateFlow<Boolean>(false)
    val realtimeConnecting = _realtimeConnecting.asStateFlow()

    val chatId = savedStateHandle.get<Int>("chatId")
    val receiverProfile = savedStateHandle.get<Profile>("profile")

    private var messageLoadStatus: MessageLoadStatus? = savedStateHandle["messageLoadStatus"]
        set(value) {
            field = value
            savedStateHandle["messageLoadStatus"] = messageLoadStatus
        }

    val messageList = mutableListOf<MessageViewType>()
    val separators = mutableListOf<String>()
    val connectedUsers = mutableSetOf<PresenceState>()

    var lastLoadedItemId = 0

    var isLastPage = false
    var insertJob: Job? = null

    private fun connectRealtime() = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (!channel.isJoinedOrJoining()) {

                channel.join(true)
                channel.track(PresenceState(uid = getMyUid()))
            } else {
                insertionDone()
            }

        } catch (e: Exception) {
            insertionDone()
            Log.d("TAG", "connectRealtime failed: ${e.message}")
        }
    }

    private fun getAllMessages(chatId: Int) = viewModelScope.launch {
        _messageUiState.update { it.copy(isLoading = true) }
        when (val response = repository.getAllMessages(chatId)) {

            is Response.Error -> {
                if (response.error is HttpRequestException) {
                    _messageUiState.update {
                        it.copy(
                            isLoading = false, error = R.string.network_error
                        )
                    }
                } else _messageUiState.update {
                    it.copy(
                        isLoading = false, error = R.string.other_errors
                    )
                }
                messageLoadStatus = MessageLoadStatus.ChatFound(chatId)
            }

            is Response.Success -> {
                processList(response.data)
                messageLoadStatus = MessageLoadStatus.Success(chatId)
                _messageUiState.update {
                    it.copy(
                        isLoading = false, messages = response.data
                    )
                }
            }
        }
    }

    /** This method is called when paginate messages. We save the last loaded message and request chats less that the last loaded id everytime */
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
                        it.copy(
                            isLoading = false, messages = response.data
                        )
                    }
                }

                is Response.Error -> {
                    Log.d("TAG", "nextPage: ${response.error.message}")
                    _messageUiState.update {
                        it.copy(
                            isLoading = false, error = R.string.other_errors
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
                date = formattedTime/*when next page's data loaded checking if the data is from the last dates of the previous page
                Suppose last data was from today and next pages first data is also from today so I removed the first today */
                if (separators.contains(date)) {
                    messageList.remove(MessageDate(date))
                } else {/*This check is basically to skip the first date so that I can add it after all the message of that date is loaded
                    This is required because I want to make {Hi, Hello, Today, Nice, Yesterday} instead of {Today, Hi, Hello, Yesterday, Nice} because this list will be reversed in recyclerView */
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
        Log.d(
            "TAG",
            "addMessage: $message, ${separators.isNotEmpty() && separators.first() == formattedTime}"
        )
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


    /**
     *[MessageLoadStatus.NotFound] If this is a new chat first it creates a chat and the insert the message.
     *[MessageLoadStatus.Failed] If message load failed so we don't know if it is a new chat or not so we try to [getChat] again.
     *[MessageLoadStatus.ChatFound] If chat found but failed to load messages of that chat then request [getAllMessage] as well as insert the message
     *[MessageLoadStatus.Success] If chat found and all the message is also loaded that means we can insert the message from the next time.
     */
    fun checkAndInsert(message: Message) = viewModelScope.launch {
        Log.d("TAG", "insertData connectRealtime: $messageLoadStatus")
        if (realtimeConnecting.value || messageUiState.value.isLoading) {
            return@launch
        }
        when (val status = messageLoadStatus) {
            is MessageLoadStatus.NotFound -> {
                createChat(status.receiverId, message)
            }

            is MessageLoadStatus.Failed -> {
                getChat(status.receiverId, message)
            }

            is MessageLoadStatus.ChatFound -> {
                getAllMessages(status.chatId)

                val mMessage =
                    if (message.chatId == -1) message.copy(chatId = status.chatId) else message
                insertMessage(mMessage)

            }

            is MessageLoadStatus.Success -> {
                val mMessage =
                    if (message.chatId == -1) message.copy(chatId = status.chatId) else message
                insertMessage(mMessage)
            }

            else -> {}
        }
    }

    private suspend fun createChat(receiverId: String, message: Message) {
        when (val response = repository.createChat(receiverId)) {
            is Response.Error -> {
                _insertState.value = InsertState.Error("Failed to create chat!")
            }

            is Response.Success -> {
                messageLoadStatus = MessageLoadStatus.Success(response.data.id)
                val mMessage =
                    if (message.chatId == -1) message.copy(chatId = response.data.id) else message
                insertMessage(mMessage)
            }
        }
    }

    private suspend fun insertMessage(message: Message) {
        _insertState.value = InsertState.Loading
        when (repository.insert(message = message)) {
            is Response.Error -> {
                _insertState.value = InsertState.Error("Message send failed!")
            }

            else -> {

            }
        }
    }


    fun messageUiErrorShown() {
        _messageUiState.update { it.copy(error = null) }
    }

    fun insertionDone() {
        _insertState.value = null
    }

    fun deleteChat() = viewModelScope.launch {
        if (messageLoadStatus !is MessageLoadStatus.Success && messageLoadStatus !is MessageLoadStatus.ChatFound) return@launch
        val chatId =
            if (messageLoadStatus is MessageLoadStatus.Success) (messageLoadStatus as MessageLoadStatus.Success).chatId
            else (messageLoadStatus as MessageLoadStatus.ChatFound).chatId

        _messageUiState.update { it.copy(isLoading = true) }

        when (repository.deleteChat(chatId)) {
            is Response.Error -> {
                _messageUiState.update {
                    it.copy(
                        isLoading = false, error = R.string.deletion_failed
                    )
                }
            }

            is Response.Success -> {
                _messageUiState.update { it.copy(isLoading = false, deleted = true) }
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
    private suspend fun getChat(receiverId: String, message: Message? = null) {
        when (val response = repository.getChat(receiverId)) {

            is Response.Success -> {
                Log.d("TAG", "getChat: ${response.data.id}")
                getAllMessages(response.data.id)
                messageLoadStatus = MessageLoadStatus.ChatFound(response.data.id)
            }

            is Response.Error -> {
                messageLoadStatus = if (response.error is NoSuchElementException) {
                    MessageLoadStatus.NotFound(receiverId)
                } else {
                    _messageUiState.update {
                        it.copy(
                            isLoading = false, error = R.string.message_load_failed
                        )
                    }
                    MessageLoadStatus.Failed(receiverId)
                }
            }
        }

        message?.let {
            checkAndInsert(message)
        }
    }

    fun getMyUid() = savedStateHandle.get<String>("uid") ?: run {
        val uid = client.gotrue.currentUserOrNull()?.id ?: ""
        savedStateHandle["uid"] = uid
        uid
    }

    private fun setMessageSeenTrue(chatId: Int) = viewModelScope.launch {
        repository.setMessageSeenTrue(chatId)
    }

    @OptIn(SupabaseExperimental::class)
    fun uploadImage(media: Media) = viewModelScope.launch {
        saveFileBitmapToFile(Uri.parse(media.uri).toFile())?.let {
            val bucket = client.storage["images"]
            bucket.uploadAsFlow(media.name, it)
                .catch { _uploadState.value = UploadState.Error("Upload Failed!") }
                .collect { status ->
                    Log.d("TAG", "uploadImage: $status")
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
            .catch { _uploadState.value = UploadState.Error("Upload Failed!") }.collect { status ->
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

    /** Reset upload state after upload is done */
    fun uploadDone() {
        _uploadState.value = null
    }


    /** Track whether user is on the chat or not */
    private fun addUserPresenceTracker() {
        kotlin.runCatching {
            if (channel.isJoinedOrJoining()) return
            channel.presenceChangeFlow().distinctUntilChanged().onEach {
                    connectedUsers += it.decodeJoinsAs()
                    connectedUsers -= it.decodeLeavesAs()
                }.catch {
                    insertionDone()
                }.launchIn(viewModelScope)
        }
    }

    /** Listen realtime message when new message insert */
    private fun realtimeMessageUpdate() {
        kotlin.runCatching {
            if (channel.isJoinedOrJoining()) return
            insertJob?.cancel()
            insertJob = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }.distinctUntilChanged().onEach { data ->
                    val message = data.decodeRecord<Message>()
                    if (message.authorId != getMyUid()) {
                        setMessageSeenTrue(message.id)
                    }
                    getMessageById(message.id)
                }.catch {
                    insertionDone()
                }.launchIn(viewModelScope)
        }
    }

    private fun loadChat() {
        client.gotrue.sessionStatus.onEach {
            Log.d("Statuspp", "Auth Message: $it")
            if (it is SessionStatus.Authenticated) {

                if (messageList.isEmpty()) {
                    receiverProfile?.let {
                        if (chatId == null) {
                            getChat(it.id)
                        } else {
                            getAllMessages(chatId)
                        }
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun checkRealtimeConnection() {
        combine(
            client.realtime.status,
            channel.status,
        ) { realtimeStatus, channelStatus ->
            Pair(realtimeStatus, channelStatus)
        }.onEach { (realtimeStatus, channelStatus) ->
            // Update the visibility of the progress bar based on the combined values
            _realtimeConnecting.value =
                !(realtimeStatus == Realtime.Status.CONNECTED && channelStatus == RealtimeChannel.Status.JOINED)
        }.catch {
            _messageUiState.update {
                it.copy(
                    isLoading = false, error = R.string.other_errors
                )
            }
        }.launchIn(viewModelScope)
    }

    init {
        addUserPresenceTracker()
        realtimeMessageUpdate()
        checkRealtimeConnection()
        loadChat()

        client.realtime.status.onEach {
            if (it == Realtime.Status.CONNECTED) {
                connectRealtime()
            }
        }.launchIn(viewModelScope)

    }
}

data class MessageUIState(
    val isLoading: Boolean = false,
    val messages: List<Message>? = null,
    val error: Int? = null,
    val deleted: Boolean = false
)

sealed class InsertState<out R> {
    data object Loading : InsertState<Nothing>()
    data class Success<out T>(val data: T) : InsertState<T>()
    class Error(val error: String) : InsertState<Nothing>()
}

sealed class UploadState {
    data object Success : UploadState()
    class Uploading(val progress: Float = 0f) : UploadState()
    class Error(val error: String) : UploadState()
}

@Serializable
data class PresenceState(val uid: String)
