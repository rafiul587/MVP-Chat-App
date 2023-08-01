package com.example.mvpchatapplication.ui.chats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Chat
import com.example.mvpchatapplication.databinding.ItemChatBinding
import com.example.mvpchatapplication.utils.loadProfileImage

class ChatsAdapter(
    private val chatList: List<Chat>,
    val listener: OnChatClickListener,
    val myUid: String
) : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {

    interface OnChatClickListener {
        fun onChatClick(position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chatList[position]
        holder.bind(chat)
    }

    override fun getItemCount(): Int = chatList.size

    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            // Bind chat data to the views in the item layout
            if (chat.lastMessageAuthorId != myUid && chat.lastMessageSeen == false) {
                binding.name.setTextAppearance(R.style.MessageNotSeen)
                binding.message.setTextAppearance(R.style.MessageNotSeen)
                binding.notSeenView.isVisible = true
            } else {
                binding.name.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat)
                binding.message.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat)
                binding.notSeenView.isVisible = false
            }
            binding.name.text = chat.otherUserName
            binding.profileImage.loadProfileImage(chat.otherUserProfileImage)
            binding.message.text = chat.lastMessage
            binding.lastMsgTime.text = chat.updatedAt
        }

        init {
            itemView.setOnClickListener {
                listener.onChatClick(adapterPosition)
            }
        }
    }
}
