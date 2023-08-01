package com.example.mvpchatapplication.ui.message

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.utils.handleApiResponse
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.PostgrestResult
import io.github.jan.supabase.postgrest.query.Returning
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.createChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MessageRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val realtime: Realtime,
) {

    fun getAllMessages(chatId: Int): Flow<PagingData<Message>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { MessagesPagingSource(postgrest = postgrest, chatId = chatId) }
        ).flow
    }

    suspend fun getAllChat(chatId: Int, pageNumber: Int, loadSize: Int): Response<List<Message>> {
        return withContext(Dispatchers.IO) {
            try {
                val messages = postgrest["messages"]
                    .select(
                        Columns.raw("""*, profiles (id, profile_image)"""),
                        filter = {
                            eq("chat_id", chatId)
                            range(0L, 10L)
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

    suspend fun connectRealtime(chatId: Int): Flow<PostgresAction.Insert> {
        val realtimeChannel = realtime.createChannel("#messages")
        realtime.connect()
        val flow = realtimeChannel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "messages"
            filter = "chat_id=eq.$chatId"
        }
        realtimeChannel.join()
        return flow
    }

    suspend fun insert(message: Message): Response<PostgrestResult> {
        return handleApiResponse {
            postgrest.from("messages")
                .insert(message, returning = Returning.MINIMAL)
        }
    }

    suspend fun deleteChat(roomId: Int): Response<PostgrestResult> {
        return handleApiResponse {
            postgrest.from("rooms").delete {
                eq("room_id", roomId)
            }
        }
    }

    suspend fun getChat(receiverId: String): Response<Chat> {
        return withContext(Dispatchers.IO) {
            try {
                val chat = postgrest["chats"]
                    .select() {
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

    suspend fun createChat(receiverId: String): Response<PostgrestResult> {
        return try {
            val result = postgrest.from("chats").insert(
                Chat(id = 0, user2 = receiverId),
                returning = Returning.MINIMAL
            )
            Response.Success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            Response.Error(e)
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
        const val PAGE_SIZE = 20
    }
}