package com.example.mvpchatapplication.ui.message

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.data.sources.MessagesPagingSource
import com.example.mvpchatapplication.di.MessageChannel
import com.example.mvpchatapplication.utils.MessageViewType
import com.example.mvpchatapplication.utils.handleApiResponse
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestResult
import io.github.jan.supabase.postgrest.query.Returning
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MessageRepository @Inject constructor(
        private val postgrest: Postgrest,
        private val realtime: Realtime,
        @MessageChannel
        private val messageChannel: RealtimeChannel,
) {

    private lateinit var pagingSource: MessagesPagingSource

    fun getAllMessages(chatId: Int): Flow<PagingData<MessageViewType>> {
        return Pager(
                config = PagingConfig(
                        pageSize = PAGE_SIZE,
                        enablePlaceholders = false,
                        prefetchDistance = 5,
                        initialLoadSize = 30
                ),
                pagingSourceFactory = {
                    pagingSource = MessagesPagingSource(postgrest, chatId)
                    pagingSource
                }
        ).flow
    }

    fun invalidate() {
        kotlin.runCatching {
            pagingSource.invalidate()
        }
    }

    suspend fun getAllChat(chatId: Int, page: Int, pageSize: Int): Response<List<Message>> {
        return withContext(Dispatchers.IO) {
            try {
                val startIndex = page * pageSize
                val endIndex = startIndex + pageSize - 1
                val messages = postgrest["messages"]
                        .select(
                                Columns.raw("""*, profiles (id, profile_image)"""),
                                filter = {
                                    order("created_at", Order.DESCENDING)
                                    eq("chat_id", chatId)
                                    range(startIndex.toLong(), endIndex.toLong())
                                }
                        )
                        .decodeList<Message>()
                Response.Success(messages)
            } catch (e: Exception) {
                e.printStackTrace()
                Response.Error(e)
            }
        }
    }

    suspend fun connectRealtime(): Flow<PostgresAction.Insert> {
        Log.d("TAG", "connectRealtime: message ")
        val flow = messageChannel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "messages"
        }.flowOn(Dispatchers.IO)
        return flow
    }

    suspend fun insert(message: Message): Response<PostgrestResult> {
        return handleApiResponse {
            postgrest.from("messages")
                    .insert(message, returning = Returning.MINIMAL)
        }
    }

    suspend fun deleteChat(chatId: Int): Response<PostgrestResult> {
        return handleApiResponse {
            postgrest.from("chats").delete {
                eq("id", chatId)
            }
        }
    }

    suspend fun getChat(receiverId: String): Response<Chat> {
        return withContext(Dispatchers.IO) {
            try {
                val chat = postgrest["chats"]
                        .select {
                            or {
                                eq("user1", receiverId)
                                eq("user2", receiverId)
                            }
                        }.decodeSingle<Chat>()
                Response.Success(chat)
            } catch (e: Exception) {
                e.printStackTrace()
                Response.Error(e)
            }
        }
    }

    suspend fun createChat(receiverId: String): Response<Chat> {
        return handleApiResponse {
            postgrest.from("chats").insert(
                    Chat(id = 0, user2 = receiverId),
            ).decodeSingle()
        }
    }

    suspend fun setMessageSeenTrue(chatId: Int) {
        postgrest["messages"].update(
                {
                    set("seen", true)
                }
        ) {
            eq("id", chatId)
        }
    }

    companion object {
        const val PAGE_SIZE = 15
    }
}