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
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.BuildConfig
import com.example.mvpchatapplication.MainActivity
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.databinding.FragmentMessageTestBinding
import com.example.mvpchatapplication.utils.AdapterNotifyType
import com.example.mvpchatapplication.utils.MessageContent
import com.example.mvpchatapplication.utils.MessageDate
import com.example.mvpchatapplication.utils.MessageType
import com.example.mvpchatapplication.utils.loadProfileImage
import com.google.android.material.progressindicator.CircularProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@AndroidEntryPoint
class MessageFragment : BindingFragment<FragmentMessageTestBinding>(),
    MessageAdapter.MessageClickListener {

    val viewModel by navGraphViewModels<MessageViewModel>(R.id.navigation_message_graph){defaultViewModelProviderFactory }
    private val navController by lazy { findNavController() }
    private lateinit var messageAdapter: MessageAdapter

    private var receiverProfile: Profile? = null
    private var chatId: Int? = null

    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentMessageTestBinding::inflate
    private val toolBarBinding get() = binding.toolBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.initializeBundles()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpTopChatBar()
        initRecyclerView()
        setUpStateListeners()

        binding.sendBtn.setOnClickListener {
            sendMessage()
        }

        binding.sendMediaFile.setOnClickListener {
            navController.navigate(
                R.id.action_navigation_chat_to_navigation_capture_media,
                bundleOf("chatId" to chatId, "receiverId" to receiverProfile!!.id)
            )
        }
    }

    private fun sendMessage() {
        val message = binding.editTextMessage.text?.trim().toString()
        if (message.isEmpty()) {
            return
        }

        val seen = viewModel.connectedUsers.contains(PresenceState(receiverProfile!!.id))

        viewModel.insertData(
            message = Message(
                chatId = chatId!!,
                content = message,
                type = MessageType.TEXT,
                seen = seen
            )
        )
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
            if (chatId == -1) {
                viewModel.getChat(it.id)
            } else {
                viewModel.getAllMessages(chatId!!, it.id)
                viewModel.setChatId(chatId!!)
            }
        }
    }


    private fun setUpStateListeners() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messageUiState.collectLatest {
                        binding.progressBar.isVisible = it.isLoading

                        if (it.deleted) {
                            navController.popBackStack()
                            return@collectLatest
                        }
                        if (!it.isLoading && it.messages != null) {
                            messageAdapter.notifyDataSetChanged()
                            if (viewModel.lastLoadedItemId == 0 && it.messages.isNotEmpty()) {
                                scrollToBottom()
                            }
                        }

                        it.error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            viewModel.messageUiErrorShown()
                        }
                    }
                }

                launch {
                    viewModel.insertState.collectLatest {
                        binding.sendBtn.isEnabled = it !is InsertState.Loading
                        binding.messageSendingBar.isVisible = it is InsertState.Loading
                        when (it) {
                            is InsertState.Error -> {
                                Toast.makeText(
                                    requireContext(),
                                    it.error,
                                    Toast.LENGTH_SHORT
                                ).show()
                                viewModel.insertionDone()
                            }

                            is InsertState.Success -> {
                                val notifyType = viewModel.addMessage(it.data)
                                if (notifyType == AdapterNotifyType.MessageWithNewDate) {
                                    messageAdapter.notifyItemRangeInserted(0, 2)
                                } else messageAdapter.notifyItemInserted(0)

                                scrollToBottom()
                                binding.editTextMessage.setText("")
                                viewModel.insertionDone()
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun scrollToBottom() {
        val itemCount = messageAdapter.itemCount
        if (itemCount > 0) {
            binding.messagesRecyclerView.scrollToPosition(0)
        }
    }


    private fun setUpTopChatBar() {
        toolBarBinding.chatBar.apply {
            inflateMenu(R.menu.message_menu);
            setOnMenuItemClickListener {
                if (it.itemId == R.id.deleteChat) {
                    chatId?.let { viewModel.deleteChat() }
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
        messageAdapter = MessageAdapter(viewModel.messageList, this, viewModel.getMyUid())

        binding.messagesRecyclerView.apply {
            adapter = messageAdapter
            addOnScrollListener(object : OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!viewModel.messageUiState.value.isLoading && !viewModel.isLastPage) {
                        if (visibleItemCount + firstVisibleItemPosition >= totalItemCount && firstVisibleItemPosition >= 0 && totalItemCount >= viewModel.messageList.size) {
                            val last = viewModel.messageList.last()

                            val lastMessage = (if (last is MessageDate) {
                                messageAdapter.messageList[viewModel.messageList.lastIndex - 1]
                            } else last) as MessageContent
                            Log.d(
                                "TAG",
                                "onScrolled: ${lastMessage.message.createdAt!!}, ${lastMessage.message.id}"
                            )
                            viewModel.nextPage(
                                chatId!!,
                                lastMessage.message.id
                            )
                        }
                    }
                }
            })

            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom) {
                    // Calculate the height of the keyboard or message box
                    val heightDiff = oldBottom - bottom;
                    smoothScrollBy(0, heightDiff);
                }
            }
        }
    }

    override fun onImageClick(message: Message) {
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
            .load("${BuildConfig.SUPABASE_URL}/storage/v1/object/public/images/${message.decryptedContent}")
            .into(image)
        cross.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onVideoClick(message: Message) {
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
        val loadingBar = view.findViewById<CircularProgressIndicator>(R.id.videoLoadingBar)!!
        dialog.setContentView(view)
        val mediaController = MediaController(requireContext());
        videoView.setMediaController(mediaController)
        mediaController.setAnchorView(view)
        videoView.setVideoURI(Uri.parse("${BuildConfig.SUPABASE_URL}/storage/v1/object/public/videos/${message.decryptedContent}"))
        videoView.start()

        videoView.setOnPreparedListener { mp ->
            loadingBar.isVisible = false
        }
        videoView.setOnCompletionListener {
            Log.d("TAG", "onVideoClick: Complete")
            // Video playback completed, you can add any required logic here
        }
        videoView.setOnErrorListener { mp, what, extra ->
            // Log or handle the error here
            Log.d("TAG", "onVideoClick: $what")
            return@setOnErrorListener true
        }
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


    override fun onDestroy() {
        super.onDestroy()
        (requireActivity() as MainActivity).leaveMessageChannel()
    }
}