package com.example.mvpchatapplication.data.models

import com.example.mvpchatapplication.utils.MessageType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: Int = 0,
    val user1: String? = null,
    val user2: String,
    @SerialName("last_message_id")
    val lastMessageId: Int? = null,
    @SerialName("last_message_author_id")
    val lastMessageAuthorId: String? = null,
    @SerialName("last_message_content")
    val lastMessage: String? = null,
    @SerialName("last_message_seen")
    val lastMessageSeen: Boolean? = null,
    @SerialName("last_message_type")
    val lastMessageType: MessageType? = null,
    @SerialName("name")
    val otherUserName: String? = null,
    @SerialName("profile_image")
    val otherUserProfileImage: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)