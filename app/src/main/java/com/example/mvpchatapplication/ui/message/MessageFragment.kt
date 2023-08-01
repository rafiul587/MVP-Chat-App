package com.example.mvpchatapplication.ui.message

import android.app.Dialog
import android.graphics.Color
import android.graphics.Insets
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Media
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.databinding.FragmentMessageBinding
import com.example.mvpchatapplication.utils.MessageContent
import com.example.mvpchatapplication.utils.MessageDate
import com.example.mvpchatapplication.utils.MessageType
import com.example.mvpchatapplication.utils.MessageViewType
import com.example.mvpchatapplication.utils.loadProfileImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@AndroidEntryPoint
class MessageFragment : BindingFragment<FragmentMessageBinding>(),
    MessageAdapter.MessageClickListener {

    private val viewModel by viewModels<ChatViewModel>()
    private val navController by lazy { findNavController() }
    private val messageList = mutableListOf<MessageViewType>()
    private lateinit var messageAdapter: MessageAdapter

    private var receiverProfile: Profile? = null
    private var chatId: Int? = null

    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentMessageBinding::inflate
    private val toolBarBinding get() = binding.toolBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.initializeBundles()

        setFragmentResultListener("requestKey") { key, bundle ->
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable("bundleKey", Media::class.java)
            } else {
                bundle.getParcelable("bundleKey")
            }
            result?.let {
                messageList.add(
                    MessageContent(
                        Message(
                            0,
                            viewModel.getMyUid(),
                            chatId = chatId!!,
                            content = it.uri,
                            type = it.type
                        )
                    )
                )
                messageAdapter.notifyItemChanged(messageList.lastIndex)
            }
            Log.d("TAG", "onCreate: $result")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpTopChatBar()
        initRecyclerView()
        setUpStateListeners()

        binding.sendBtn.setOnClickListener {
            val content = binding.editTextMessage.text.trim().toString()
            viewModel.insertData(
                message = Message(
                    chatId = chatId!!,
                    content = content,
                    type = MessageType.TEXT
                )
            )
        }
        binding.sendMediaFile.setOnClickListener {
            navController.navigate(R.id.action_navigation_chat_to_navigation_capture_media)
        }
    }

    private fun setUpStateListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.chatUiState.collectLatest {
                        binding.progressBar.isVisible = it.isLoading
                        it.messages?.let { it1 ->
                            //groupDataIntoHashMap(it1);
                        }
                    }
                }
                launch {
                    viewModel.messages.collectLatest {
                        it.message?.let {
                            Log.d("TAG", "setUpStateListeners: $it")
                            messageList.add(MessageContent(it))
                            messageAdapter.notifyItemChanged(messageList.lastIndex)
                        }
                        it.error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            viewModel.messageErrorShown()
                        }
                    }
                }
            }
        }
    }

    private fun Bundle.initializeBundles() {
        chatId = getInt("chatId", -1)
        receiverProfile =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelable("profile", Profile::class.java)
            } else {
                this.getParcelable("profile")
            }
        receiverProfile?.let {
            Log.d("TAG", "initializeBundles: $receiverProfile, $chatId")
            if (chatId == -1) {
                viewModel.getChat(it.id)
            } else {
                viewModel.getAllChat(chatId!!)
                viewModel.connectRealtime(chatId!!)
            }
        }
    }

    private fun setUpTopChatBar() {
        toolBarBinding.chatBar.apply {
            inflateMenu(R.menu.message_menu);
            setOnMenuItemClickListener {
                if (it.itemId == R.id.deleteChat) {
                    chatId?.let { chatId -> viewModel.deleteChat(chatId) }
                    return@setOnMenuItemClickListener true;
                }
                return@setOnMenuItemClickListener false;
            }
            setNavigationOnClickListener {
                navController.navigateUp()
            }
        }
        toolBarBinding.otherUserName.text = receiverProfile!!.name
        toolBarBinding.profileImage.loadProfileImage(receiverProfile?.profileImageUrl)
    }

    private fun initRecyclerView() {
        messageAdapter = MessageAdapter(messageList, this)
        binding.messagesRecyclerView.apply {
            adapter = messageAdapter
            this.scrollToPosition(messageAdapter.itemCount - 1)
        }
    }

    private fun groupDataIntoHashMap(messages: List<MessageState>) {
        val groupedHashMap: LinkedHashMap<String, MutableSet<Message>> = LinkedHashMap()
        var list: MutableSet<Message>
        for (message in messages) {
            message.message?.let {
                //Log.d(TAG, travelActivityDTO.toString());
                val hashMapKey: String = it.createdAt!!
                //Log.d(TAG, "start date: " + DateParser.convertDateToString(travelActivityDTO.getStartDate()));
                if (groupedHashMap.containsKey(hashMapKey)) {
                    // The key is already in the HashMap; add the pojo object
                    // against the existing key.
                    groupedHashMap[hashMapKey]!!.add(it)
                } else {
                    // The key is not there in the HashMap; create a new key-value pair
                    list = LinkedHashSet()
                    list.add(it)
                    groupedHashMap[hashMapKey] = list
                }
            }
            message.error?.let {
                messageList.removeLast()
                Toast.makeText(requireContext(), "Message Sent Failed!", Toast.LENGTH_SHORT).show()
            }
        }
        //Generate list from map
        generateListFromMap(groupedHashMap)
    }


    private fun generateListFromMap(groupedHashMap: LinkedHashMap<String, MutableSet<Message>>): List<MessageViewType> {
        // We linearly add every item into the consolidatedList.
        val consolidatedList = mutableListOf<MessageViewType>()
        for (date in groupedHashMap.keys) {
            val dateItem = MessageDate(date)
            consolidatedList.add(dateItem)
            for (message in groupedHashMap[date]!!) {
                val generalItem = MessageContent(message)
                consolidatedList.add(generalItem)
            }
        }
        messageList.clear()
        messageList.addAll(consolidatedList)
        messageAdapter.notifyDataSetChanged()
        binding.messagesRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
        Log.d("TAG", "generateListFromMap: $consolidatedList")
        return consolidatedList
    }


    override fun onImageClick(position: Int) {
        val display = getScreenWidth()
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.GRAY))
        val view = layoutInflater.inflate(R.layout.full_screen_image_dilaog, null)
        val image = view.findViewById<ImageView>(R.id.image)!!
        image.layoutParams.width = display.first
        image.layoutParams.height = display.second
        dialog.setCancelable(false)
        val cross = view.findViewById<ImageView>(R.id.cross)!!
        dialog.setContentView(view, binding.root.layoutParams)
        Glide.with(requireContext())
            .load(messageList[position])
            .transform(CenterCrop())
            .into(image)
        cross.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onVideoClick(position: Int) {
        val display = getScreenWidth()
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.GRAY))
        val view = layoutInflater.inflate(R.layout.full_screen_video_dilaog, null)
        val videoView = view.findViewById<VideoView>(R.id.videoView)!!
        videoView.layoutParams.width = display.first
        videoView.layoutParams.height = display.second
        dialog.setCancelable(false)
        val cross = view.findViewById<ImageView>(R.id.cross)!!
        dialog.setContentView(view, binding.root.layoutParams)
        val mediaController = MediaController(requireContext());
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        videoView.setVideoURI(Uri.parse((messageList[position] as MessageContent).message.content))
        cross.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun getScreenWidth(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = requireActivity().windowManager.currentWindowMetrics
            val insets: Insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val width = windowMetrics.bounds.width() - insets.left - insets.right
            val height = windowMetrics.bounds.height() - insets.top - insets.bottom
            Pair(width, height)
        } else {
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            val height = displayMetrics.widthPixels
            val width = displayMetrics.widthPixels
            Pair(width, height)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.disconnectFromRealtime()
    }
}