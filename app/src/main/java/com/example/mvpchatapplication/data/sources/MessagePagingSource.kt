package com.example.mvpchatapplication.data.sources

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.utils.MessageContent
import com.example.mvpchatapplication.utils.MessageDate
import com.example.mvpchatapplication.utils.MessageViewType
import com.example.mvpchatapplication.utils.toDayOrDateString
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

class MessagesPagingSource(
        private val postgrest: Postgrest,
        private val chatId: Int
) : PagingSource<Int, MessageViewType>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageViewType> {
        return try {
            // Calculate the page number based on the LoadParams
            val from = (params.key?.plus(1)) ?: 0
            val to = from + params.loadSize // Calculate the ending point of the range

            //Log.d("TAG", "serverData load: $pageNumber, $startIndex, $endIndex ${params.loadSize}")

            // Assuming your "getAllChat" function now supports loading messages in reverse order.
            // Pass the page number and page size accordingly to get older messages.
            val messages = postgrest["messages"]
                    .select(
                            Columns.raw("""(*), profiles (id, profile_image)"""),
                            filter = {
                                order("created_at", Order.DESCENDING)
                                eq("chat_id", chatId)
                                range(from.toLong(), to.toLong())
                            }
                    )
                    .decodeList<Message>()
            Log.d("TAG", "serverData loadsss: $messages")
            val items: MutableList<MessageViewType> = ArrayList()
            val separators = mutableListOf<String>()
            var date: String = ""

            messages.forEachIndexed { index, event ->
                val formattedTime = event.createdAt!!.toDayOrDateString()
                if (formattedTime != date) {
                    if (separators.isNotEmpty()) {
                        items.add(MessageDate(separators.last()))
                    }
                    date = formattedTime
                    separators.add(date)
                }
                items.add(MessageContent(event))
            }
            items.add(MessageDate(separators.last()))
            // Extract the data from the API response

            // Calculate the next key (page number) for the previous page of messages
            /*val nextKey = if (pageNumber > 0 && messages.isNotEmpty()) {
                pageNumber - 1
            } else {
                null
            }

            // Since we're loading in reverse order, the next key is the next page (pageNumber + 1)
            val prevKey = if (messages.isNotEmpty()) {
                pageNumber + 1
            } else {
                null
            }*/

            val prevKey = if (from > 0 && messages.isNotEmpty()) {
                from - params.loadSize
            } else {
                null
            }
            val nextKey = if (items.isNotEmpty()) to else null


            Log.d("TAG", "serverData loadsffffss: ${params.loadSize}, $from, $to")
            Log.d("TAG", " serverData loadsffffss: ${params.key}, $prevKey, $nextKey")

            LoadResult.Page(
                    data = items,
                    prevKey = prevKey,
                    nextKey = nextKey
            )
        } catch (e: Exception) {
            e.printStackTrace()
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MessageViewType>): Int? {
        return null
    }
}