package com.example.mvpchatapplication.ui.chatstest

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
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
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
import com.example.mvpchatapplication.utils.MessageContent
import com.example.mvpchatapplication.utils.MessageDate
import com.example.mvpchatapplication.utils.MessageType
import com.example.mvpchatapplication.utils.loadProfileImage
import com.example.mvpchatapplication.utils.toDayOrDateString
import com.google.android.material.progressindicator.CircularProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@AndroidEntryPoint
class MessageTestFragment : BindingFragment<FragmentMessageTestBinding>(),
    MessageTestAdapter.MessageClickListener {

    private val viewModel by viewModels<MessageTestViewModel>()
    private val navController by lazy { findNavController() }
    private lateinit var messageAdapter: MessageTestAdapter

    private var receiverProfile: Profile? = null
    private var chatId: Int? = null
    var treeListener: OnGlobalLayoutListener? = null

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

        /*treeListener = OnGlobalLayoutListener {
            val heightDiff: Int = binding.root.rootView.height - binding.root.height
            if (heightDiff > 100) { // Value should be less than keyboard's height
                Log.e("MyActivity", "keyboard opened")
                binding.messagesRecyclerView.scrollToPosition(0)
            } else {
                Log.e("MyActivity", "keyboard closed")
            }
        }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener { treeListener }*/

        binding.sendBtn.setOnClickListener {
            val message = binding.editTextMessage.text?.trim().toString()
            if (message.isEmpty()) {
                return@setOnClickListener
            }
            Log.d("TAG", "onViewCreated: ${viewModel.connectedUsers}, ${receiverProfile!!.id}")
            if(viewModel.connectedUsers.contains(PresenceState(receiverProfile!!.id))) {
                Log.d("TAG", "onViewCreated: ${viewModel.connectedUsers.size}")
                viewModel.insertData(
                    message = Message(
                        chatId = chatId!!,
                        content = message,
                        type = MessageType.TEXT,
                        seen = true
                    )
                )
            }else {
                viewModel.insertData(
                    message = Message(
                        chatId = chatId!!,
                        content = message,
                        type = MessageType.TEXT,
                    )
                )
            }
        }

        binding.sendMediaFile.setOnClickListener {
            navController.navigate(
                R.id.action_navigation_chat_to_navigation_capture_media,
                bundleOf("chatId" to chatId)
            )
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
            if (chatId == -1) {
                viewModel.getChat(it.id)
            } else {
                viewModel.getAllChat(chatId!!)
                //viewModel.connectRealtime(chatId!!)
            }
        }
    }


    private fun setUpStateListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.chatUiState.collectLatest {
                        //binding.progressBar.isVisible = it.isLoading
                        if (it.deleted) {
                            navController.popBackStack()
                            return@collectLatest
                        }
                        if (!it.isLoading && it.messages != null) {
                            messageAdapter.notifyDataSetChanged()
                           /* messageAdapter.notifyItemRemoved(viewModel.messageList.lastIndex - )
                            Log.d("TAG", "setUpStateListeners: ${viewModel.notifyItemsPair.first}, ${viewModel.notifyItemsPair.second}")
                            messageAdapter.notifyItemRangeInserted(viewModel.notifyItemsPair.first, viewModel.notifyItemsPair.second)*/
                            if (viewModel.page == 1) {
                                scrollToBottom()
                            }
                        }
                    }
                }
                launch {
                    viewModel.messages.collectLatest {
                        when(it){
                            is MessageState.Error -> {
                                Toast.makeText(requireContext(), it.error.message, Toast.LENGTH_SHORT).show()
                                viewModel.messageErrorShown()
                            }
                            MessageState.Loading -> {}
                            is MessageState.Success -> {
                                val formattedTime = it.data.createdAt!!.toDayOrDateString()
                                //Log.d("TAG", "setUpStateListeners: $formattedTime, ${it.data.content}, ${viewModel.separators.first()}")
                                if (viewModel.separators.isNotEmpty() && viewModel.separators.first() == formattedTime) {
                                    viewModel.messageList.add(0, MessageContent(it.data))
                                    messageAdapter.notifyItemInserted(0)
                                } else {
                                    viewModel.messageList.add(0, MessageContent(it.data))
                                    viewModel.messageList.add(1, MessageDate(formattedTime))
                                    viewModel.separators.add(0, formattedTime)
                                    messageAdapter.notifyItemRangeInserted(0, 2 )
                                }
                                scrollToBottom()
                            }
                            null -> {}
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
        messageAdapter = MessageTestAdapter(viewModel.messageList, this, viewModel.getMyUid())
        binding.messagesRecyclerView.apply {
            adapter = messageAdapter
            addOnScrollListener(object : OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                    viewModel.onChangeRecipeScrollPosition(visibleItemCount + firstVisibleItemPosition)
                    if (!viewModel.chatUiState.value.isLoading) {
                        if (visibleItemCount + firstVisibleItemPosition >= totalItemCount && firstVisibleItemPosition >= 0 && totalItemCount >= viewModel.messageList.size) {
                            viewModel.nextPage(chatId!!)
                            /*val position =
                        (layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                    val actualPosition = position - viewModel.separators.size
                    viewModel.onChangeRecipeScrollPosition(actualPosition)
                    Log.d("TAG", "onScrolled: $position, $actualPosition")
                    if (actualPosition >= viewModel.page * MessageTestViewModel.PAGE_SIZE - 1) {
                        viewModel.nextPage(chatId!!)
                    }*/
                        }
                    }
                }
            })
            addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (bottom < oldBottom) { // Check if layout height decreased (keyboard or message box opened)
                    // Calculate the height of the keyboard or message box
                    val heightDiff = oldBottom -bottom;

                    // Adjust RecyclerView Height
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
            .load("${BuildConfig.SUPABASE_URL}/storage/v1/object/public/images/${message.content}")
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
        videoView.setVideoURI(Uri.parse("${BuildConfig.SUPABASE_URL}/storage/v1/object/public/videos/${message.content}"))
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