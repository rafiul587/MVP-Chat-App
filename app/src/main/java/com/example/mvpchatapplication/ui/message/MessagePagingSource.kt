package com.example.mvpchatapplication.ui.message

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.mvpchatapplication.data.models.Message
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns

class MessagesPagingSource(
    private val postgrest: Postgrest,
    private val chatId: Int
) : PagingSource<Int, Message>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Message> {
        return try {
            // Calculate the page number based on the LoadParams
            val pageNumber = params.key ?: 0
            val startIndex = pageNumber * params.loadSize
            val endIndex = startIndex + params.loadSize

            // Assuming your "getAllChat" function now supports loading messages in reverse order.
            // Pass the page number and page size accordingly to get older messages.
            val messages = postgrest["messages"]
                .select(
                    Columns.raw("""*, profiles (id, profile_image)"""),
                    filter = {
                        eq("chat_id", chatId)
                        range(startIndex.toLong(), endIndex.toLong())
                    }
                )
                .decodeList<Message>()
            // Extract the data from the API response

            // Calculate the next key (page number) for the previous page of messages
            val prevKey = if (pageNumber > 0 && messages.isNotEmpty()) {
                pageNumber - 1
            } else {
                null
            }

            // Since we're loading in reverse order, the next key is the next page (pageNumber + 1)
            val nextKey = if (messages.isNotEmpty()) {
                pageNumber + 1
            } else {
                null
            }

            LoadResult.Page(
                data = messages,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Message>): Int? {
        return null
    }
}