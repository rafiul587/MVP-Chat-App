package com.example.mvpchatapplication.data.sources

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.mvpchatapplication.data.models.Chat
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order

class ChatsPagingSource(
        private val postgrest: Postgrest,
) : PagingSource<Int, Chat>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Chat> {
        return try {
            // Calculate the page number based on the LoadParams
            val from = (params.key?.plus(1)) ?: 0
            val to = from +  params.loadSize

            Log.d("TAG", "serverData load: $from, $to, ${params.loadSize}")

            // Assuming your "getAllChat" function now supports loading messages in reverse order.
            // Pass the page number and page size accordingly to get older messages.
            val chats = postgrest.from("inbox").select() {
                order("updated_at", Order.DESCENDING)
                range(from.toLong(), to.toLong())
            }.decodeList<Chat>()

            // Extract the data from the API response

            // Calculate the next key (page number) for the previous page of messages
            val prevKey = if (from > 0 && chats.isNotEmpty()) {
                from - params.loadSize
            } else {
                null
            }

            // Since we're loading in reverse order, the next key is the next page (pageNumber + 1)
            val nextKey = if (chats.isNotEmpty()) {
                to
            } else {
                null
            }

            Log.d("TAG", "serverData loadsffffss: $chats")
            Log.d("TAG", " serverData loadsffffss: $prevKey, $nextKey")

            LoadResult.Page(
                    data = chats,
                    prevKey = prevKey,
                    nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Chat>): Int? {
        return null
    }
}