package com.example.mvpchatapplication.data.models

import com.example.mvpchatapplication.utils.MessageType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Int = 0,
    @SerialName("author_id")
    val authorId: String? = null,
    @SerialName("chat_id")
    val chatId: Int,
    val content: String? = null,
    @SerialName("decrypted_content")
    val decryptedContent: String? = null,
    val type: MessageType,
    @SerialName("created_at")
    val createdAt: String? = null,
    val seen: Boolean = false,
    val profiles: Profile? = null
)
