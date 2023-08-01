package com.example.mvpchatapplication.ui.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.example.mvpchatapplication.BindingFragment
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.databinding.FragmentSignUpBinding
import com.example.mvpchatapplication.utils.isValidEmail
import com.example.mvpchatapplication.utils.launchAndCollectLatest
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignUpFragment : BindingFragment<FragmentSignUpBinding>() {

    private val viewModel by viewModels<SignUpViewModel>()

    private lateinit var navController: NavController

    override val bindingInflater: (LayoutInflater) -> ViewBinding
        get() = FragmentSignUpBinding::inflate

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navController = findNavController()
        binding.signUp.setOnClickListener { singUp() }

        binding.signIn.setOnClickListener { navigateToSignIn() }

        viewModel.uiState.launchAndCollectLatest(viewLifecycleOwner) {

            binding.signUp.isEnabled = !it.isLoading
            binding.progressBar.isVisible = it.isLoading

            if (it.isSignUpSuccess) {
                navigateToProfile()
            }
            it.error?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                viewModel.errorMessageShown()
            }
        }
    }

    private fun singUp() {
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
            binding.editTextPasswordLayout.error = "Password filed is empty!"
            return
        }
        viewModel.signUp(email, password)
    }

    private fun navigateToSignIn() = navController.popBackStack()

    private fun navigateToProfile() = navController.navigate(R.id.action_navigation_sign_up_to_navigation_profile)
}