package com.example.mvpchatapplication.ui.chats

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.data.sources.ChatsPagingSource
import com.example.mvpchatapplication.di.ChatChannel
import com.example.mvpchatapplication.ui.message.MessageRepository
import com.example.mvpchatapplication.utils.handleApiResponse
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@ViewModelScoped
class ChatsRepository @Inject constructor(
    private val postgrest: Postgrest,
    @ChatChannel
    private val chatChannel: RealtimeChannel,
) {

    suspend fun getAllChats(lastChatId: Int): Response<List<Chat>> {
        return handleApiResponse {
            postgrest.from("inbox").select() {
                order("updated_at", Order.DESCENDING)
                if (lastChatId > 0) {
                    lt("id", lastChatId)
                }
                limit(PAGE_SIZE.toLong())
            }.decodeList()
        }
    }

    suspend fun connectRealtime(): Flow<PostgresAction> {

        val flow = chatChannel.postgresChangeFlow<PostgresAction>("public") {
            table = "chats"
        }
        chatChannel.join()

        return flow
    }

    suspend fun searchUserWithEmail(email: String): Response<Profile> {
        return handleApiResponse {
            postgrest["profiles"].select(
                columns = Columns.list("id", "name", "profile_image")
            ) {
                limit(1)
                eq("email", email)
            }.decodeSingle()
        }
    }

    suspend fun setMessageSeenTrue(messageId: Int) {
        handleApiResponse {
            postgrest["messages"].update(
                {
                    set("seen", true)
                }
            ) {
                eq("id", messageId)
            }
        }
    }

    suspend fun getMessageById(messageId: Int): Response<Message> {
        return handleApiResponse {
            postgrest["decrypted_messages"].select() {
                limit(1)
                eq("id", messageId)
            }.decodeSingle()
        }
    }

    suspend fun getProfileById(otherUserId: String): Response<Profile> {
        return handleApiResponse {
            postgrest["profiles"].select(){
                eq("id", otherUserId)
            }.decodeSingle()
        }
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}