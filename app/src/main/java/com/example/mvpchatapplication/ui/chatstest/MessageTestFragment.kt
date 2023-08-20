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
import androidx.paging.LoadState
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.BuildConfig
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.databinding.FragmentMessageBinding
import com.example.mvpchatapplication.utils.MessageType
import com.example.mvpchatapplication.utils.loadProfileImage
import com.google.android.material.progressindicator.CircularProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch


@AndroidEntryPoint
class MessageTestFragment : BindingFragment<FragmentMessageBinding>(),
    MessageTestAdapter.MessageClickListener {

    private val viewModel by viewModels<MessageTestViewModel>()
    private val navController by lazy { findNavController() }
    private lateinit var messageTestAdapter: MessageTestAdapter

    private var receiverProfile: Profile? = null
    private var chatId: Int? = null

    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentMessageBinding::inflate
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
            val message = binding.editTextMessage.text.trim().toString()
            if (message.isEmpty()) {
                return@setOnClickListener
            }
            viewModel.insertData(
                message = Message(
                    chatId = chatId!!,
                    decryptedContent = message,
                    type = MessageType.TEXT
                )
            )
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
                viewModel.connectRealtime(chatId!!)
            }
        }
    }


    private fun setUpStateListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.chatUiState.collectLatest {
                        binding.progressBar.isVisible = it.isLoading
                        if (it.deleted) {
                            navController.popBackStack()
                            return@collectLatest
                        }
                        it.messages?.let { message ->
                            messageTestAdapter.submitData(viewLifecycleOwner.lifecycle, message)
                        }

                    }
                }
                launch {
                    viewModel.messages.collectLatest {
                        it.error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            viewModel.messageErrorShown()
                        }
                    }
                }

                launch {
                    messageTestAdapter.loadStateFlow
                        // Only emit when REFRESH LoadState for RemoteMediator changes.


                        // Only react to cases where REFRESH completes, such as NotLoading.
                        .filter { it.source.refresh is LoadState.NotLoading }
                        // Scroll to top is synchronous with UI updates, even if remote load was
                        // triggered.
                        .collect { scrollToBottom() }
                }
            }
        }
    }

    private fun scrollToBottom() {
        val itemCount = messageTestAdapter.itemCount
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
        messageTestAdapter = MessageTestAdapter(this, viewModel.getMyUid())
        binding.messagesRecyclerView.apply {
            adapter = messageTestAdapter
            addOnScrollListener(object : OnScrollListener() {})
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
        viewModel.leaveChannel()
    }
}