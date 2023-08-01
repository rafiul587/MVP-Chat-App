package com.example.mvpchatapplication.ui.message

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.MediaController
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Media
import com.example.mvpchatapplication.databinding.FragmentSendMediaBinding
import com.example.mvpchatapplication.utils.Constants
import com.example.mvpchatapplication.utils.MessageType
import com.example.mvpchatapplication.utils.launchAndCollectLatest
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SendMediaFragment : BindingFragment<FragmentSendMediaBinding>() {

    var media: Media? = null
    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentSendMediaBinding::inflate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable("media", Media::class.java)
            } else it.getParcelable("media")
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
                setFragmentResult("requestKey", bundleOf("bundleKey" to it))
                findNavController().popBackStack(R.id.navigation_message, false)
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