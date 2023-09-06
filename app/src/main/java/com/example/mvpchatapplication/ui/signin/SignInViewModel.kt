package com.example.mvpchatapplication.ui.signin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.Response
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: SignInRepository
) : ViewModel() {

    private var _uiState = MutableStateFlow(SignInUiState())
    val uiState = _uiState.asStateFlow()

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }

        when (val response = repository.signIn(email, password)) {
            is Response.Success -> {
                _uiState.update { it.copy(isLoading = false, isSignInSuccess = true) }
            }

            is Response.Error -> {
                if (response.error is HttpRequestException) {
                    _uiState.update { it.copy(isLoading = false, error = R.string.network_error) }
                } else if (response.error.message?.contains("invalid_grant") == true) {
                    _uiState.update { it.copy(isLoading = false, error = R.string.credential_error) }
                } else  _uiState.update { it.copy(isLoading = false, error = R.string.other_errors) }
            }
        }
    }

    fun errorMessageShown() {
        _uiState.update { it.copy(error = null) }
    }
}


data class SignInUiState(
    val isLoading: Boolean = false,
    val userInfo: UserInfo? = null,
    val isSignInSuccess: Boolean = false,
    val error: Int? = null,
)
