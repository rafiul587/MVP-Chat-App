package com.example.mvpchatapplication.ui.passwordreset

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.databinding.FragmentPasswordResetBinding
import com.example.mvpchatapplication.utils.isValidEmail
import com.example.mvpchatapplication.utils.launchAndCollectLatest
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.gotrue.GoTrue
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class PasswordResetFragment : BindingFragment<FragmentPasswordResetBinding>() {

    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentPasswordResetBinding::inflate

    private val navController by lazy { findNavController() }

    private val viewModel by viewModels<PasswordResetViewModel>()

    @Inject
    lateinit var goTrue: GoTrue

    var path: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            path = it.getString("path")
            Log.d("TAG", "onCreate: $path, ${isSendResetLinkScreen()}")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        goTrue.sessionStatus.launchAndCollectLatest(viewLifecycleOwner) {
            Log.d("TAG", "showResetPasswordUI: $it")
        }
        if (isSendResetLinkScreen()) {
            showSendResetLinkUI()
        } else {
            showResetPasswordUI()
        }
        viewModel.passwordResetUiState.launchAndCollectLatest(this) {
            binding.progressBar.isVisible = it.isLoading

            binding.resetPasswordBtn.isEnabled = !it.isLoading
            binding.sendResetLinkBtn.isEnabled = !it.isLoading

            it.error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.errorMessageShown()
            }
            if (it.isSuccess) {
                if (isSendResetLinkScreen()) {
                    binding.sendResetLinkLayout.isVisible = false
                    binding.sendResetLinkBtn.isVisible = false
                    binding.sendLinkSuccessMessage.isVisible = true
                    val email = binding.editTextEmail.text?.trim().toString()
                    binding.sendLinkSuccessMessage.text =
                        getString(R.string.message_send_link_success, email)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Password has been reset successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navController.popBackStack()
                }
            }
        }
    }

    private fun showResetPasswordUI() {
        binding.passwordResetLayout.isVisible = true
        binding.sendResetLinkLayout.isVisible = false
        binding.resetPasswordBtn.setOnClickListener {
            Log.d("TAG", "showResetPasswordUI: ")
            val newPassword = binding.editTextNewPassword.text?.trim().toString()
            if (newPassword.length < 6) {
                binding.passwordEditTextLayout.error = "Minimum password length is 6!"
                return@setOnClickListener
            }
            viewModel.modifyUser(newPassword)
        }
    }

    private fun showSendResetLinkUI() {
        Log.d("TAG", "showSendResetLinkUI: ")
        binding.sendResetLinkLayout.isVisible = true
        binding.passwordResetLayout.isVisible = false
        binding.sendResetLinkBtn.setOnClickListener {
            Log.d("TAG", "showSendResetLinkUI: ")
            val email = binding.editTextEmail.text?.trim().toString()
            if (!email.isValidEmail()) {
                binding.passwordEditTextLayout.error = "Invalid Email!"
                return@setOnClickListener
            }
            Log.d("TAG", "showSendResetLinkUI22222222: ")
            viewModel.sendForgotPasswordLink(email)
        }
    }

    private fun isSendResetLinkScreen() = path == null
}