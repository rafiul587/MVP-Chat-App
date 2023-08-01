package com.example.mvpchatapplication.ui.chats

import android.util.Log
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.utils.handleApiResponse
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.createChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ChatsRepository @Inject constructor(
    private val postgrest: Postgrest, private val realtime: Realtime
) {

    suspend fun getAllChatRooms(): Response<List<Chat>> {
        return handleApiResponse {
            postgrest.from("inbox").select().decodeList()
        }
    }

    suspend fun connectRealtime(): Flow<PostgresAction.Insert> {
        val realtimeChannel = realtime.createChannel("#chats")
        realtime.connect()
        val flow = realtimeChannel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "chats"
        }
        realtimeChannel.join()
        return flow
    }

    suspend fun insert(message: Message) {
        postgrest.from("messages").insert(message)
    }

    suspend fun searchUserWithEmail(email: String): Response<Profile> {
        return withContext(Dispatchers.IO) {
            try {
                val result = postgrest["profiles"].select(
                    columns = Columns.list("id", "name", "profile_image")
                ) {
                    limit(1)
                    eq("email", email)
                }.decodeSingle<Profile>()
                Log.d("TAG", "searchUserWithEmail: $result")
                Response.Success(result)
            } catch (e: Exception) {
                Log.d("TAG", "searchUserWithEmail: $e")
                Response.Error(e)
            }
        }
    }

    suspend fun setMessageSeenTrue(messageId: Int) {
        postgrest["messages"].update(
            {
                set("seen", true)
            }
        ) {
            eq("id", messageId)
        }
    }
}