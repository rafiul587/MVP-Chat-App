package com.example.mvpchatapplication.ui.signup

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.Response
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: SignUpRepository
) : ViewModel() {

    var _uiState = MutableStateFlow(SignUpUiState())
    val uiState = _uiState.asStateFlow()

    fun signUp(email: String, password: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }

        when (val response = repository.signUp(email, password)) {

            is Response.Success -> {
                _uiState.update { it.copy(isLoading = false, isSignUpSuccess = true) }
            }

            is Response.Error -> {
                if (response.error is HttpRequestException) {
                    Log.d("TAG", "signUp: ${response.error.printStackTrace()}")
                    _uiState.update { it.copy(isLoading = false, error = R.string.network_error) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = R.string.other_errors) }
                }
            }
        }
    }

    fun errorMessageShown() {
        _uiState.update { it.copy(error = null) }
    }
}


data class SignUpUiState(
    val isLoading: Boolean = false,
    val isSignUpSuccess: Boolean = false,
    val error: Int? = null,
)
