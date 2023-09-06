package com.example.mvpchatapplication.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.example.mvpchatapplication.utils.BindingFragment
import com.example.mvpchatapplication.MainActivity
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.databinding.FragmentProfileBinding
import com.example.mvpchatapplication.utils.DialogListener
import com.example.mvpchatapplication.utils.getFileFromScaledBitmap
import com.example.mvpchatapplication.utils.isDateValid
import com.example.mvpchatapplication.utils.loadProfileImage
import com.example.mvpchatapplication.utils.scaleFullBitmap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class ProfileFragment : BindingFragment<FragmentProfileBinding>(), DialogListener {

    private val viewModel by viewModels<ProfileViewModel>()
    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentProfileBinding::inflate
    private val navController by lazy { findNavController() }

    private var passwordEditDialog: PasswordEditDialog? = null
    private var emailEditDialog: EmailEditDialog? = null

    private var isUpdated = false
    private val openGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                Glide.with(requireContext())
                    .load(it)
                    .into(binding.profileImage)
                binding.uploadProgress.isVisible = true
                val fullBitmap : Bitmap =  MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, it)
                // 2. Scale down the Bitmap
                val scaledBitmap : Bitmap = scaleFullBitmap(fullBitmap)
                // 3. Convert the scaled Bitmap to File to upload to server
                val scaledFile : File = getFileFromScaledBitmap(requireContext(), scaledBitmap)
                scaledFile.let {
                        viewModel.uploadProfileImage(it.toUri())
                    }
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
                            return@collectLatest
                        }
                        binding.progressBar.isVisible = it.isLoading
                        binding.save.isEnabled = !it.isLoading
                        if (!it.isLoading && it.profile != null) {
                            binding.editTextName.setText(it.profile.name)
                            binding.editTextPhone.setText(it.profile.phone)
                            binding.editTextEmail.setText(it.profile.email)
                            binding.editTextBirthday.setText(it.profile.birthday)
                            binding.profileImage.loadProfileImage(it.profile.profileImageUrl)

                            if (isUpdated) {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.msg_profile_update_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (navController.previousBackStackEntry == null) {
                                    navController.navigate(R.id.action_navigation_profile_to_navigation_chats)
                                } else navController.popBackStack()
                                isUpdated = false
                            }
                        }

                        it.error?.let {
                            Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.msg_profile_image_update_success),
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.resetUploadState()
                        }
                        it.error?.let {
                            Toast.makeText(requireContext(), "Upload Failed!", Toast.LENGTH_SHORT)
                                .show()
                            viewModel.resetUploadState()
                        }
                    }
                }
                launch {
                    viewModel.passwordState.collectLatest {
                        binding.editTextPassword.setText(it)
                    }
                }
                launch {
                    viewModel.userModifyState.collectLatest {
                        when (it) {
                            is UserModifyState.Email -> {
                                binding.editTextEmail.setText(it.email)
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.msg_email_update_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                emailEditDialog?.dismiss()
                            }

                            is UserModifyState.Error -> {
                                Toast.makeText(requireContext(), it.error, Toast.LENGTH_SHORT)
                                    .show()
                                passwordEditDialog?.dismiss()
                            }

                            is UserModifyState.Password -> {
                                binding.editTextPassword.setText(it.password)
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.msg_password_update_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                passwordEditDialog?.dismiss()
                            }

                            null -> {}
                        }
                        viewModel.resetUserModifyState()
                    }
                }
            }
        }

        binding.save.setOnClickListener {
            isUpdated = true
            val name = binding.editTextName.text.trim().toString()
            val phone = binding.editTextPhone.text.trim().toString()
            val birthday = binding.editTextBirthday.text.trim().toString()

            if (name.isEmpty()) {
                binding.editTextName.error = getString(R.string.error_name_require)
                return@setOnClickListener
            }

            if (birthday.isNotEmpty() && !birthday.isDateValid()) {
                binding.editTextBirthday.error = getString(R.string.error_date_format)
                return@setOnClickListener
            }

            val profile = Profile(
                name = name,
                phone = phone,
                birthday = birthday,
            )
            viewModel.updateProfile(profile)
        }

        binding.btnEditPassword.setOnClickListener {
            showPasswordEditDialog()
        }

        binding.btnEditEmail.setOnClickListener {
            showEmailEditDialog()
        }

        binding.editPhoto.setOnClickListener {
            checkPhotoPermission()
        }

        binding.logout.setOnClickListener {
            viewModel.logOut()
            (requireActivity() as MainActivity).leaveMessageChannel()
            (requireActivity() as MainActivity).leaveChatChannel()
        }
    }

    private fun checkPhotoPermission() {
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

                showPermissionExplanationDialog()
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.
            }

            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPhotoPermission()
            }
        }
    }

    private fun requestPhotoPermission() {
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

    private fun showPermissionExplanationDialog() {
        val explanation = getString(R.string.need_photo_permission_explanation)
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.permission_request))
        builder.setMessage(explanation)
        builder.setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
            // Request the permission
            requestPhotoPermission()
        }
        builder.setNegativeButton("No Thanks") { _, _ ->
            // Handle the user's decision to not grant permission
            // You can add custom logic here, such as displaying a message or disabling certain features
        }
        builder.show()
    }

    private fun showPasswordEditDialog() {
        val password = binding.editTextPassword.text?.trim().toString() ?: ""
        passwordEditDialog = PasswordEditDialog(this, password)
        passwordEditDialog?.show(childFragmentManager, "PasswordEditDialog")
    }

    private fun showEmailEditDialog() {
        val email = binding.editTextEmail.text.trim().toString()
        emailEditDialog = EmailEditDialog(this, email)
        emailEditDialog?.show(childFragmentManager, "EmailEditDialog")
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isUpdated", isUpdated)
    }

    private fun openGallery() {
        openGallery.launch("image/*")
    }

    override fun onEmailChanged(email: String) {
        viewModel.modifyUser(email = email)
    }

    override fun onPasswordChanged(password: String) {
        viewModel.modifyUser(password = password)
    }

}