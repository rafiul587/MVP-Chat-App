package com.example.mvpchatapplication.ui.chatstest

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.mvpchatapplication.BuildConfig
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.databinding.ItemMessageDateBinding
import com.example.mvpchatapplication.databinding.ItemMessageOthersBinding
import com.example.mvpchatapplication.databinding.ItemMessageOwnBinding
import com.example.mvpchatapplication.utils.MessageContent
import com.example.mvpchatapplication.utils.MessageDate
import com.example.mvpchatapplication.utils.MessageType
import com.example.mvpchatapplication.utils.MessageViewType
import com.example.mvpchatapplication.utils.loadProfileImage
import com.example.mvpchatapplication.utils.toTime

class MessageTestAdapter(
    val messageList: List<MessageViewType>,
    private val listener: MessageClickListener,
    private val uid: String,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface MessageClickListener {
        fun onImageClick(message: Message)
        fun onVideoClick(message: Message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            MessageViewType.DATE -> {
                val binding = ItemMessageDateBinding.inflate(inflater, parent, false)
                MessageDateViewHolder(binding)
            }

            MessageViewType.OWN -> {
                val binding = ItemMessageOwnBinding.inflate(inflater, parent, false)
                MessageOwnViewHolder(binding)
            }

            else -> {
                val binding = ItemMessageOthersBinding.inflate(inflater, parent, false)
                MessageOthersViewHolder(binding)
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
                val ownMessage = messageList[position]  as MessageContent
                val chatLeftViewHolder: MessageOwnViewHolder = viewHolder as MessageOwnViewHolder
                chatLeftViewHolder.bind(ownMessage.message)
            }

            MessageViewType.OTHERS -> {
                val othersMessage = messageList[position]  as MessageContent
                val dateViewHolder: MessageOthersViewHolder = viewHolder as MessageOthersViewHolder
                dateViewHolder.bind(othersMessage.message)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return messageList[position].getType(userId = uid)
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    inner class MessageDateViewHolder(val binding: ItemMessageDateBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            binding.date.text = date
        }

    }


    inner class MessageOthersViewHolder(val binding: ItemMessageOthersBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.imageMessage.isVisible = message.type != MessageType.TEXT
            binding.message.isVisible = message.type == MessageType.TEXT
            binding.playIcon.isVisible = message.type == MessageType.VIDEO
            when (message.type) {
                MessageType.IMAGE -> {
                    binding.imageMessage.loadMedia(message.content!!, MessageType.IMAGE)
                }

                MessageType.VIDEO -> {
                    binding.imageMessage.loadMedia(message.content!!, MessageType.VIDEO)
                }

                else -> {
                    binding.message.text = message.content
                }
            }

            binding.msgTime.text = message.createdAt?.toTime()
            binding.profileImage.loadProfileImage(message.profiles?.profileImageUrl)
        }

        init {
            itemView.setOnClickListener {
                handleClickListener(absoluteAdapterPosition)
            }
        }
    }


    inner class MessageOwnViewHolder(val binding: ItemMessageOwnBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.imageMessage.isVisible = message.type != MessageType.TEXT
            binding.message.isVisible = message.type == MessageType.TEXT
            binding.playIcon.isVisible = message.type == MessageType.VIDEO
            Log.d("TAG", "bind: $message")
            when (message.type) {
                MessageType.IMAGE -> {
                    binding.imageMessage.loadMedia(message.content!!, MessageType.IMAGE)
                }

                MessageType.VIDEO -> {
                    binding.imageMessage.loadMedia(message.content!!, MessageType.VIDEO)
                }

                else -> {
                    binding.message.text = message.content
                }
            }

            binding.msgTime.text = message.createdAt?.toTime()
            binding.profileImage.loadProfileImage(message.profiles?.profileImageUrl)
        }

        init {
            itemView.setOnClickListener {
                handleClickListener(absoluteAdapterPosition)
            }
        }

    }

    fun ImageView.loadMedia(fileName: String, type: MessageType) {
        val url = if (type == MessageType.IMAGE) {
            "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/images/${fileName}"
        } else {
            "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/videos/${fileName}"
        }
        Glide.with(this)
            .load(url)
            .error(android.R.drawable.progress_horizontal)
            .placeholder(android.R.drawable.progress_horizontal)
            .transition(DrawableTransitionOptions.withCrossFade())
            .override((300 * context.resources.displayMetrics.density).toInt())
            .transform(RoundedCorners(10))
            .into(this)
    }

    fun handleClickListener(position: Int) {
        val messageViewType = messageList.get(position)
        if (messageViewType is MessageContent) {
            if (messageViewType.message.type == MessageType.IMAGE) {
                listener.onImageClick(messageViewType.message)
            } else if (messageViewType.message.type == MessageType.VIDEO) {
                listener.onVideoClick(messageViewType.message)
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<MessageViewType>() {


        override fun areItemsTheSame(oldItem: MessageViewType, newItem: MessageViewType): Boolean {
            Log.d("TAG", "areItemsTheSame: ${newItem}, ${oldItem}")
            return when {
                oldItem is MessageContent && newItem is MessageContent -> oldItem.message.id == newItem.message.id

                oldItem is MessageDate && newItem is MessageDate -> {

                    oldItem.date == newItem.date

                }

                else -> false
            }
        }

        override fun areContentsTheSame(
            oldItem: MessageViewType,
            newItem: MessageViewType
        ): Boolean {
            return when {
                oldItem is MessageContent && newItem is MessageContent -> oldItem.message == newItem.message

                oldItem is MessageDate && newItem is MessageDate -> {
                    Log.d("TAG", "areContentsTheSame: ${newItem.date}, ${oldItem.date}")
                    oldItem.date == newItem.date

                }

                else -> false
            }
        }

        override fun getChangePayload(oldItem: MessageViewType, newItem: MessageViewType): Any? {
            return if (oldItem is MessageDate && newItem is MessageDate && oldItem.date == newItem.date) {
                newItem // Only return the new date if both are MessageDate and have the same date
            } else {
                super.getChangePayload(oldItem, newItem)
            }
        }
    }
}

