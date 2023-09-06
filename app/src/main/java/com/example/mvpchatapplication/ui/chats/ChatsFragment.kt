package com.example.mvpchatapplication.ui.chats

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.example.mvpchatapplication.utils.BindingFragment
import com.example.mvpchatapplication.MainActivity
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.databinding.FragmentChatsBinding
import com.example.mvpchatapplication.utils.isValidEmail
import com.example.mvpchatapplication.utils.loadProfileImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatsFragment : BindingFragment<FragmentChatsBinding>(), ChatsAdapter.OnChatClickListener {

    private val viewModel by viewModels<ChatListViewModel>()
    private lateinit var chatsAdapter: ChatsAdapter

    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentChatsBinding::inflate

    private val navController by lazy { findNavController() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolBar()
        initRecyclerView()
        setUiUpdateStateListeners()

        binding.searchIcon.setOnClickListener {
            val email = binding.searchBox.text.trim().toString()
            if (email.isEmpty()) {
                binding.searchBox.error = getString(R.string.error_empty_email)
                return@setOnClickListener
            }
            if (!email.isValidEmail()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_invalid_email), Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            viewModel.searchUserWithEmail(email = email)
        }

        binding.cross.setOnClickListener {
            binding.searchResultLayout.isVisible = false
            viewModel.resetSearchState()
            binding.searchBox.setText("")
        }
    }

    private fun setUiUpdateStateListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.chatsUiState.collectLatest {

                        binding.progressBar.isVisible = it.isLoading

                        it.error?.let {
                            if (it == R.string.network_error) {
                                binding.errorLayout.isVisible = true
                                binding.emptyLayout.isVisible = false
                                binding.chatsRecyclerView.isVisible = false
                                binding.refresh.setOnClickListener {
                                    viewModel.getAllChats()
                                }
                                viewModel.chatErrorMessageShown()
                                return@collectLatest
                            }

                            Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT)
                                .show()
                            viewModel.chatErrorMessageShown()
                        }

                        it.chats?.let {
                            binding.errorLayout.isVisible = false
                            binding.chatsRecyclerView.isVisible = true
                            if (viewModel.chatList.isEmpty()) {
                                binding.emptyLayout.isVisible = true
                                binding.chatsRecyclerView.isVisible = false
                            } else {
                                binding.emptyLayout.isVisible = false
                                binding.chatsRecyclerView.isVisible = true
                            }
                            chatsAdapter.notifyDataSetChanged()
                        }

                        it.chatAction?.let {
                            when (it) {
                                is ChatAction.Delete -> {
                                    chatsAdapter.notifyItemRemoved(it.index)
                                    viewModel.resetChatAction()
                                }

                                ChatAction.Insert -> {
                                    chatsAdapter.notifyItemInserted(0)
                                    viewModel.resetChatAction()
                                }

                                ChatAction.Update -> {
                                    chatsAdapter.notifyDataSetChanged()
                                    viewModel.resetChatAction()
                                }
                            }
                            binding.errorLayout.isVisible = false
                            binding.chatsRecyclerView.isVisible = true

                            if (viewModel.chatList.isEmpty()) {
                                binding.emptyLayout.isVisible = true
                                binding.chatsRecyclerView.isVisible = false
                            } else {
                                binding.emptyLayout.isVisible = false
                                binding.chatsRecyclerView.isVisible = true
                            }
                        }
                    }
                }

                launch {
                    viewModel.searchState.collectLatest {
                        it.profile?.let { profile ->
                            binding.searchResultLayout.isVisible = true
                            if (profile.id.isEmpty()) {
                                binding.searchResultLayout.isVisible = false
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.error_no_user_found), Toast.LENGTH_SHORT
                                ).show()
                                viewModel.resetSearchState()
                            } else {
                                binding.searchResultLayout.setOnClickListener {
                                    binding.searchBox.setText("")
                                    viewModel.resetSearchState()
                                    val bundle = Bundle()
                                    bundle.putParcelable("profile", profile)
                                    navController.navigate(
                                        R.id.action_navigation_chats_to_navigation_message_graph,
                                        bundle
                                    )
                                }
                                binding.name.text = profile.name
                                binding.profileImage.loadProfileImage(profile.profileImageUrl)
                            }
                        }
                        it.error?.let {
                            binding.searchResultLayout.isVisible = false
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.other_errors),
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.resetSearchState()
                        }
                    }
                }

            }
        }
    }

    private fun setUpToolBar() {
        binding.toolBar.inflateMenu(R.menu.chatlist_menu)
        binding.toolBar.setOnMenuItemClickListener {
            if (it.itemId == R.id.profile) {
                findNavController().navigate(R.id.action_navigation_chats_to_navigation_profile)
                return@setOnMenuItemClickListener true
            } else return@setOnMenuItemClickListener false
        }
    }

    private fun initRecyclerView() {
        chatsAdapter = ChatsAdapter(viewModel.chatList, this, viewModel.getMyUid())
        binding.chatsRecyclerView.apply {
            adapter = chatsAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!viewModel.chatsUiState.value.isLoading && !viewModel.isLastPage) {
                        if (visibleItemCount + firstVisibleItemPosition >= totalItemCount && firstVisibleItemPosition >= 0 && totalItemCount >= viewModel.chatList.size) {
                            val lastChat = viewModel.chatList.last()
                            Log.d("TAG", "onScrolled: ${lastChat.id}")
                            viewModel.nextPage(lastChat.id)
                        }
                    }
                }
            })
        }
    }

    override fun onChatClick(position: Int) {
        val chat = viewModel.chatList[position]
        if (chat.lastMessageAuthorId != viewModel.getMyUid()) {
            viewModel.setMessageSeenTrue(chat.lastMessageId!!)
        }
        val otherUserId = if (chat.user1 == viewModel.getMyUid()) chat.user2 else chat.user1
        val profile =
            Profile(
                id = otherUserId!!,
                name = chat.otherUserName,
                profileImageUrl = chat.otherUserProfileImage
            )
        navController.navigate(
            R.id.action_navigation_chats_to_navigation_message_graph,
            bundleOf("chatId" to chat.id, "profile" to profile)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        (requireActivity() as MainActivity).leaveChatChannel()
    }
}