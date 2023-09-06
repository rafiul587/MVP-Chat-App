package com.example.mvpchatapplication.ui.signin

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.example.mvpchatapplication.utils.BindingFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.databinding.FragmentSignInBinding
import com.example.mvpchatapplication.utils.isValidEmail
import com.example.mvpchatapplication.utils.launchAndCollectLatest
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignInFragment : BindingFragment<FragmentSignInBinding>() {

    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentSignInBinding::inflate

    private val navController by lazy { findNavController() }

    private val viewModel by viewModels<SignInViewModel>()

    @Inject
    lateinit var goTrue: GoTrue


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                binding.progressBar.isVisible = true
                goTrue.sessionStatus.collectLatest {
                    binding.errorLayout.isVisible = it is SessionStatus.NetworkError
                    when (it) {
                        is SessionStatus.Authenticated -> {
                            binding.progressBar.isVisible = false
                            navigateToChats()
                            Log.d("TAG", "onViewCreated: $it")
                        }

                        SessionStatus.LoadingFromStorage -> {
                            Log.d("TAG", "onViewCreated: $it")
                        }

                        SessionStatus.NetworkError -> {
                            binding.progressBar.isVisible = false
                            binding.signInScreenGroup.isVisible = false
                            Log.d("TAG", "onViewCreated: $it")
                        }

                        SessionStatus.NotAuthenticated -> {
                            binding.progressBar.isVisible = false
                            binding.signInScreenGroup.isVisible = true
                            binding.signUp.setOnClickListener { navigateToSignUp() }
                            binding.signIn.setOnClickListener { singIn() }
                            binding.forgotPassword.setOnClickListener {
                                navController.navigate(R.id.action_navigation_sign_in_to_navigation_password_reset)
                            }

                            viewModel.uiState.launchAndCollectLatest(viewLifecycleOwner) {

                                binding.signIn.isEnabled = !it.isLoading
                                binding.progressBar.isVisible = it.isLoading
                                it.error?.let {
                                    Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                                    viewModel.errorMessageShown()
                                }
                            }
                            Log.d("TAG", "onViewCreated: $it")
                        }
                    }
                }
            }
        }

        Log.d("TAG", "onViewCreated UID: ${goTrue.currentUserOrNull()?.id}")

    }

    private fun singIn() {
        val email = binding.editTextEmail.text?.trim().toString()
        val password = binding.editTextPassword.text?.trim().toString()
        if (email.isEmpty()) {
            binding.editTextEmail.error = "Email filed is empty!"
            return
        }
        if (!email.isValidEmail()) {
            binding.editTextEmail.error = "Email is not a valid email!"
            return
        }
        if (password.isEmpty()) {
            binding.editTextPassword.error = "Password filed is empty!"
            return
        }
        viewModel.signIn(email, password)
    }

    private fun navigateToSignUp() = navController.navigate(R.id.navigation_sign_up)

    private fun navigateToChats() {
        navController.navigate(R.id.action_navigation_sign_in_to_navigation_chats, null,
            navOptions = NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, true)
                .build())
    }
}