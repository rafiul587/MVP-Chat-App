package com.example.mvpchatapplication.ui.chats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.databinding.ItemChatBinding
import com.example.mvpchatapplication.utils.MessageType
import com.example.mvpchatapplication.utils.loadProfileImage
import com.example.mvpchatapplication.utils.toTimeOrDateString

class ChatsAdapter(
        val listener: OnChatClickListener,
        val myUid: String
) : PagingDataAdapter<Chat, ChatsAdapter.ChatViewHolder>(ChatDiffCallback()) {

    interface OnChatClickListener {
        fun onChatClick(position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = getItem(position)
        chat?.let { holder.bind(it) }
    }


    inner class ChatViewHolder(private val binding: ItemChatBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            // Bind chat data to the views in the item layout
            if (chat.lastMessageAuthorId != myUid && chat.lastMessageSeen == false) {
                binding.name.setTextAppearance(R.style.MessageNotSeen)
                binding.message.setTextAppearance(R.style.BoldText)
                binding.notSeenView.isVisible = true
            } else {
                binding.name.setTextAppearance(R.style.MessageSeen)
                binding.message.setTextAppearance(R.style.MessageSeenName)
                binding.notSeenView.isVisible = false
            }

            binding.name.text = chat.otherUserName
            binding.profileImage.loadProfileImage(chat.otherUserProfileImage)

            binding.message.text = when (chat.lastMessageType) {
                MessageType.IMAGE -> {
                    if (chat.lastMessageAuthorId == myUid) {
                        itemView.context.getString(R.string.you_sent_photo)
                    } else itemView.context.getString(R.string.user_sent_photo, chat.otherUserName)
                }

                MessageType.VIDEO -> {
                    if (chat.lastMessageAuthorId == myUid) {
                        itemView.context.getString(R.string.you_sent_video)
                    } else itemView.context.getString(R.string.user_sent_video, chat.otherUserName)
                }

                else -> chat.lastMessage
            }
            binding.lastMsgTime.text = chat.updatedAt?.toTimeOrDateString()
        }


        init {
            itemView.setOnClickListener {
                listener.onChatClick(absoluteAdapterPosition)
                updateSeenStatusTrue(absoluteAdapterPosition)
            }
        }
    }

    fun updateSeenStatusTrue(position: Int) {
        snapshot()[position]?.lastMessageSeen = true
        notifyItemChanged(position)
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem == newItem
        }
    }
}
