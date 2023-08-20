package com.example.mvpchatapplication.ui.message

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.viewbinding.ViewBinding
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Media
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.databinding.FragmentSendMediaBinding
import com.example.mvpchatapplication.utils.MessageType
import com.example.mvpchatapplication.utils.launchAndCollectLatest
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SendMediaFragment : BindingFragment<FragmentSendMediaBinding>() {

    private var media: Media? = null
    private var receiverId: String? = null
    private var chatId: Int? = null
    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentSendMediaBinding::inflate
    private val viewModel by navGraphViewModels<MessageViewModel>(R.id.navigation_message_graph){defaultViewModelProviderFactory }
    private val navController by lazy { findNavController() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {

            media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable("media", Media::class.java)
            } else it.getParcelable("media")

            receiverId = it.getString("receiverId", "")
            chatId = it.getInt("chatId", 0)

            Log.d("TAG", "onCreate: $media")

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        media?.let {
            if (it.type == MessageType.IMAGE) {
                showImage(Uri.parse(it.uri))
            } else if (it.type == MessageType.VIDEO) {
                showVideo(Uri.parse(it.uri))
            }
        }
        binding.sendBtn.setOnClickListener {

            media?.let {
                if (it.type == MessageType.IMAGE) {
                    viewModel.uploadImage(it)
                } else if (it.type == MessageType.VIDEO) {
                    viewModel.uploadVideo(it)
                }
            }
        }
        viewModel.insertState.launchAndCollectLatest(viewLifecycleOwner) {
            when (it) {
                is InsertState.Error -> {
                    binding.uploadProgress.isVisible = false
                    Toast.makeText(
                        requireContext(),
                        "Message send Failed. Try again!",
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.insertionDone()
                }

                is InsertState.Success -> {
                    binding.uploadProgress.isVisible = false
                    if (media?.type == MessageType.IMAGE) {
                        Toast.makeText(
                            requireContext(),
                            "Image has been sent successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Video has been sent successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    navController.popBackStack(R.id.navigation_capture_media, true)
                }

                else -> {}
            }
        }
        viewModel.uploadState.launchAndCollectLatest(viewLifecycleOwner) {
            when (it) {
                is UploadState.Error -> {
                    binding.uploadProgress.isVisible = false
                    Toast.makeText(
                        requireContext(),
                        "Upload Failed. Try again!",
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.uploadDone()
                }

                UploadState.Success -> {

                    val seen = viewModel.connectedUsers.contains(PresenceState(receiverId!!))

                    val message = Message(
                        authorId = viewModel.getMyUid(),
                        decryptedContent = media!!.name,
                        type = media!!.type,
                        chatId = chatId!!,
                        seen = seen
                    )
                    viewModel.insertData(message)
                    viewModel.uploadDone()
                }

                is UploadState.Uploading -> {
                    binding.uploadProgress.isVisible = true
                    binding.uploadProgress.setProgress(it.progress.toInt(), true)
                }

                else -> {}
            }
        }
    }

    private fun showVideo(videoUri: Uri) {
        binding.capturedVideo.isVisible = true
        val mediaController = MediaController(requireContext())
        mediaController.setAnchorView(binding.capturedVideo)

        //Setting MediaController and URI, then starting the videoView
        binding.capturedVideo.setVideoURI(videoUri)
        binding.capturedVideo.requestFocus()
        binding.capturedVideo.start()
        binding.capturedVideo.setOnCompletionListener {
            it.start()
        }
    }

    private fun showImage(imageUri: Uri) {
        binding.capturedImage.isVisible = true
        binding.capturedImage.setImageURI(imageUri)
    }
}