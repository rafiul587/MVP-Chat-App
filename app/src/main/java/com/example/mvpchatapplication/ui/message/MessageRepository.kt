package com.example.mvpchatapplication.ui.message

import android.util.Log
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.di.MessageChannel
import com.example.mvpchatapplication.utils.handleApiResponse
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestResult
import io.github.jan.supabase.postgrest.query.Returning
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MessageRepository @Inject constructor(
    private val postgrest: Postgrest,
    @MessageChannel
    private val messageChannel: RealtimeChannel,
) {

    suspend fun getAllMessages(
        chatId: Int,
        lastMessageId: Int = 0
    ): Response<List<Message>> {
        return handleApiResponse {
            postgrest["messages"]
                .select(
                    Columns.raw("""*, profiles (id, profile_image)"""),
                    filter = {
                        order("created_at", Order.DESCENDING)
                        eq("chat_id", chatId)
                        if (lastMessageId > 0) {
                            lt("id", lastMessageId)
                        }
                        limit(PAGE_SIZE.toLong())
                    })
                .decodeList()
        }
    }

    fun connectRealtime(): Flow<PostgresAction.Insert> {
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

    suspend fun getMessageById(messageId: Int): Response<Message> {
        return handleApiResponse {
            postgrest["messages"]
                .select(
                    Columns.raw("""*, profiles (id, profile_image)"""),
                    filter = {
                        order("created_at", Order.DESCENDING)
                        eq("id", messageId)
                        limit(1)
                    })
                .decodeSingle()
        }
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}