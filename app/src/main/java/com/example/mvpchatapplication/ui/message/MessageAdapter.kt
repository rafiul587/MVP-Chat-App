package com.example.mvpchatapplication.ui.message

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.databinding.ItemMessageBinding
import com.example.mvpchatapplication.utils.MessageContent
import com.example.mvpchatapplication.utils.MessageDate
import com.example.mvpchatapplication.utils.MessageType
import com.example.mvpchatapplication.utils.MessageViewType
import com.example.mvpchatapplication.utils.loadProfileImage


class MessageAdapter(
    private val messageList: List<MessageViewType>,
    private val listener: MessageClickListener
) :
    PagingDataAdapter<MessageViewType, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    interface MessageClickListener {
        fun onImageClick(position: Int)
        fun onVideoClick(position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            MessageViewType.DATE -> {
                val binding = ItemMessageBinding.inflate(inflater, parent, false)
                MessageDateViewHolder(binding)
            }

            MessageViewType.OWN -> {
                val binding = ItemMessageBinding.inflate(inflater, parent, false)
                MessageViewHolder(binding)
            }

            else -> {
                val binding = ItemMessageBinding.inflate(inflater, parent, false)
                MessageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        when (viewHolder.itemViewType) {
            MessageViewType.DATE -> {
                val date = messageList[position] as MessageDate
                val dateViewHolder: MessageDateViewHolder = viewHolder as MessageDateViewHolder
                dateViewHolder.bind(date.date)
            }

            MessageViewType.OWN -> {
                val ownMessage = messageList[position] as MessageContent
                val chatLeftViewHolder: MessageViewHolder = viewHolder as MessageViewHolder
                chatLeftViewHolder.bind(ownMessage.message)
            }

            MessageViewType.OTHERS -> {
                val othersMessage = messageList[position] as MessageContent
                val dateViewHolder: MessageViewHolder = viewHolder as MessageViewHolder
                dateViewHolder.bind(othersMessage.message)
            }
        }
    }

    inner class MessageViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            Log.d("TAG", "bind: $message")
            when (message.type) {
                MessageType.IMAGE -> {
                    binding.imageMessage.isVisible = true
                    binding.videoView.isVisible = false
                    binding.message.isVisible = false
                    binding.playIcon.isVisible = false
                    Glide.with(itemView)
                        .load(message.content)
                        .override(450)
                        .transform(RoundedCorners(10))
                        .error(R.drawable.round_person_outline_24)
                        .placeholder(R.drawable.img)
                        .into(binding.imageMessage)
                    binding.imageMessage.setOnClickListener {
                        listener.onImageClick(adapterPosition)
                    }
                }

                MessageType.VIDEO -> {
                    binding.videoView.isVisible = true
                    binding.playIcon.isVisible = true
                    binding.imageMessage.isVisible = false
                    binding.message.isVisible = false
                    binding.videoView.setOnClickListener {
                        listener.onVideoClick(adapterPosition)
                    }
                }

                else -> {
                    binding.videoView.isVisible = false
                    binding.imageMessage.isVisible = false
                    binding.playIcon.isVisible = false
                    binding.message.isVisible = true
                    binding.message.text = message.content
                }
            }

            binding.msgTime.text = message.createdAt
            binding.profileImage.loadProfileImage(message.profiles?.profileImageUrl)
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    override fun getItemViewType(position: Int): Int {
        return messageList[position].getType("")
    }

    inner class  MessageDateViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            binding.msgTime.text = date
        }

    }

    inner class MessageOthersViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {

        }

    }

    inner class MessageOwnViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {

        }

    }

    class MessageDiffCallback : DiffUtil.ItemCallback<MessageViewType>() {
        override fun areItemsTheSame(oldItem: MessageViewType, newItem: MessageViewType): Boolean {
            return when {
                oldItem is MessageContent && newItem is MessageContent ->
                    oldItem.message.id == newItem.message.id
                oldItem is MessageDate && newItem is MessageDate ->
                    oldItem.date == newItem.date
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: MessageViewType, newItem: MessageViewType): Boolean {
            return when {
                oldItem is MessageContent && newItem is MessageContent ->
                    oldItem.message == newItem.message
                oldItem is MessageDate && newItem is MessageDate ->
                    oldItem.date == newItem.date
                else -> false
            }
        }
    }
}

