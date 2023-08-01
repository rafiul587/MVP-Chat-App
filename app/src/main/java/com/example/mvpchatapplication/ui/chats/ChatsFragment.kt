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
import androidx.viewbinding.ViewBinding
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Chat
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
    private val chatList = mutableListOf<Chat>()
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
                binding.searchBox.error = "Email is empty!"
                return@setOnClickListener
            }
            if (!email.isValidEmail()) {
                Toast.makeText(requireContext(), "Email is invalid!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.searchUserWithEmail(email = email)
        }
    }

    private fun setUiUpdateStateListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.chatsUiState.collectLatest {
                        it.chats?.let {
                            chatList.clear()
                            chatList.addAll(it)
                            chatsAdapter.notifyDataSetChanged()
                        }
                    }
                }

                launch {
                    viewModel.searchState.collectLatest {
                        it.profile?.let { profile ->
                            binding.searchResultLayout.isVisible = true
                            if (profile.id.isEmpty()) {

                            } else {
                                binding.searchResultLayout.setOnClickListener {
                                    val bundle = Bundle()
                                    bundle.putParcelable("profile", profile)
                                    navController.navigate(
                                        R.id.action_navigation_chats_to_navigation_message,
                                        bundle
                                    )
                                }
                                binding.name.text = profile.name
                                binding.profileImage.loadProfileImage(profile.profileImageUrl)
                            }
                        }
                        it.error?.let {
                            binding.searchResultLayout.isVisible = false
                            Log.d("TAG", "addUiUpdateStateListeners: $it")
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
        chatsAdapter = ChatsAdapter(chatList, this, viewModel.getMyUid())
        binding.chatsRecyclerView.apply {
            adapter = chatsAdapter
        }
    }

    override fun onChatClick(position: Int) {
        val chat = chatList[position]
        if(chat.lastMessageAuthorId != viewModel.getMyUid()){
            viewModel.setMessageSeenTrue(chat.lastMessageId!!)
        }
        navController.navigate(
            R.id.action_navigation_chats_to_navigation_message,
            bundleOf("chatId" to chat.id, "profile" to Profile(name = chat.otherUserName, profileImageUrl = chat.otherUserProfileImage))
        )
    }
}