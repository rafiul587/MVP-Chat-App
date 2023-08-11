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
    private lateinit var pagingSource: ChatsPagingSource
    fun getAllChatRooms(): Flow<PagingData<Chat>> {
        return Pager(
            config = PagingConfig(
                pageSize = MessageRepository.PAGE_SIZE,
                enablePlaceholders = false,
                prefetchDistance = 5,
                initialLoadSize = 30
            ),
            pagingSourceFactory = {
                pagingSource = ChatsPagingSource(postgrest)
                pagingSource
            }
        ).flow
    }

    fun invalidate() {
        pagingSource.invalidate()
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
            postgrest["messages"].select() {
                limit(1)
                eq("id", messageId)
            }.decodeSingle()
        }
    }
}