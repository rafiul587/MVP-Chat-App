package com.example.mvpchatapplication.ui.passwordreset

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvpchatapplication.data.Response
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordResetViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: PasswordResetRepository,
) : ViewModel() {

    private var _passwordResetUiState = MutableStateFlow(PasswordResetUiState())
    val passwordResetUiState = _passwordResetUiState.asStateFlow()

    fun sendForgotPasswordLink(email: String) = viewModelScope.launch {
        _passwordResetUiState.update { it.copy(isLoading = true) }
        when (val response = repository.sendForgotPasswordLink(email)) {
            is Response.Error -> _passwordResetUiState.update {
                it.copy(isLoading = false, error = response.error.localizedMessage)
            }

            is Response.Success -> _passwordResetUiState.update {
                it.copy(
                    isLoading = false,
                    isSuccess = true
                )
            }
        }
    }

    fun modifyUser(newPassword: String) = viewModelScope.launch {
        _passwordResetUiState.update { it.copy(isLoading = true) }
        when (val response = repository.modifyUser(newPassword)) {
            is Response.Error -> _passwordResetUiState.update {
                it.copy(isLoading = false, error = response.error.localizedMessage)
            }

            is Response.Success -> _passwordResetUiState.update {
                it.copy(
                    isLoading = false,
                    isSuccess = true
                )
            }
        }
    }

    fun errorMessageShown() {
        _passwordResetUiState.update { it.copy(error = null) }
    }

}

data class PasswordResetUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)
