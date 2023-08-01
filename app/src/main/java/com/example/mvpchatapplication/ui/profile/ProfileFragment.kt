package com.example.mvpchatapplication.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.databinding.FragmentProfileBinding
import com.example.mvpchatapplication.utils.isDateValid
import com.example.mvpchatapplication.utils.loadProfileImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : BindingFragment<FragmentProfileBinding>() {

    private val viewModel by viewModels<ProfileViewModel>()
    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentProfileBinding::inflate
    private val navController by lazy { findNavController() }
    private var isUpdated = false
    private val openGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                Glide.with(requireContext())
                    .load(it)
                    .into(binding.profileImage)
                binding.uploadProgress.isVisible = true
                viewModel.uploadProfileImage(it)
            }
        }

    private val photoPermissionResult =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Handle Permission granted/rejected
            if (permissions.entries.all { it.value }) {
                openGallery()
                // Permission is granted
            } else {
                // Permission is denied
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            isUpdated = it.getBoolean("isUpdated")
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.profileUiState.collectLatest {
                        if (it.isLoggedOut) {
                            navController.navigate(R.id.action_navigation_profile_to_navigation_sign_in)
                        }
                        binding.progressBar.isVisible = it.isLoading
                        binding.save.isEnabled = !it.isLoading
                        it.profile?.let {
                            binding.editTextName.setText(it.name)
                            binding.editTextPhone.setText(it.phone)
                            binding.editTextEmail.setText(it.email)
                            binding.editTextBirthday.setText(it.birthday)
                            binding.profileImage.loadProfileImage(it.profileImageUrl)
                            /*if (isUpdated) {
                                Toast.makeText(
                                    requireContext(),
                                    "Profile Updated Successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (navController.previousBackStackEntry == null) {
                                    navController.navigate(R.id.action_navigation_profile_to_navigation_chats)
                                } else navController.popBackStack()
                                isUpdated = false
                            }*/
                        }

                        it.error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            viewModel.profileUpdateErrorShown()
                        }
                    }
                }
                launch {
                    viewModel.uploadState.collect {
                        it.progress?.let {
                            binding.uploadProgress.progress = it.toInt()
                        }
                        if (it.isSuccess) {
                            binding.uploadProgress.isVisible = false
                            Toast.makeText(requireContext(), "Profile image updated successfully!", Toast.LENGTH_SHORT).show()
                            viewModel.resetUploadState()
                        }
                        it.error?.let {
                            Toast.makeText(requireContext(), "Upload Failed!", Toast.LENGTH_SHORT).show()
                            viewModel.resetUploadState()
                        }
                    }
                }
                launch {
                    viewModel.passwordState.collectLatest {

                        binding.editTextPassword.setText(it)
                    }
                }
            }
        }

        binding.save.setOnClickListener {
            isUpdated = true
            val name = binding.editTextName.text.trim().toString()
            val phone = binding.editTextPhone.text.trim().toString()
            val email = binding.editTextEmail.text.trim().toString()
            val password = binding.editTextPassword.text?.trim().toString()
            val birthday = binding.editTextBirthday.text.trim().toString()

            if (name.isEmpty()) {
                binding.editTextName.error = "Name is required"
                return@setOnClickListener
            }

            // Add validation for email if needed
            if (email.isEmpty()) {
                binding.editTextEmail.error = "Email is required"
                return@setOnClickListener
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.editTextEmail.error = "Invalid email format"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                binding.editTextPassword.error = "Password is required"
                return@setOnClickListener
            }

            if (birthday.isNotEmpty() && !birthday.isDateValid()) {
                binding.editTextBirthday.error = "Invalid date format. Use DD/MM/YYYY!"
                return@setOnClickListener
            }

            val profile = Profile(
                name = name,
                phone = phone,
                email = email,
                birthday = birthday,
            )
            val oldProfile = viewModel.profileUiState.value.profile
            val oldPassword = viewModel.passwordState.value
            viewModel.updateProfile(profile)

            if (oldProfile != null && oldPassword.isNotEmpty()) {
                if (oldProfile.email != profile.email || oldPassword != password) {
                    viewModel.modifyUser(
                        if (oldProfile.email != profile.email) email else "",
                        if (oldPassword != password) password else ""
                    )
                }
            }
        }

        binding.editPhoto.setOnClickListener {
            requestPhotoPermission()
        }

        binding.logout.setOnClickListener {
            viewModel.logOut()
        }
    }

    private fun requestPhotoPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                openGallery()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES) || shouldShowRequestPermissionRationale(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.
            }

            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                val permissions = mutableListOf<String>()
                permissions += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
                photoPermissionResult.launch(permissions.toTypedArray())
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isUpdated", isUpdated)
    }

    private fun openGallery() {
        openGallery.launch("image/*")
    }
}