package com.example.mvpchatapplication.ui.profile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.storage.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ProfileRepository,
) : ViewModel() {

    private var _profileUiState = MutableStateFlow(ProfileUiState())
    val profileUiState = _profileUiState.asStateFlow()

    private var _uploadState = MutableStateFlow(UploadState())
    val uploadState = _uploadState.asStateFlow()

    private var _passwordState = MutableStateFlow("")
    val passwordState = _passwordState.asStateFlow()

    private var _userModifyState = MutableStateFlow<UserModifyState?>(null)
    val userModifyState = _userModifyState.asStateFlow()


    private fun getUserProfile() = viewModelScope.launch {
        when (val result = repository.getUserProfile()) {
            is Response.Success -> {
                _profileUiState.update { it.copy(isLoading = false, profile = result.data) }
            }

            is Response.Error -> {
                _profileUiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.error.message ?: "Something went wrong!"
                    )
                }
            }
        }
    }

    fun updateProfile(profile: Profile) = viewModelScope.launch {
        _profileUiState.update { it.copy(isLoading = true) }
        when (val result = repository.updateProfile(profile)) {
            is Response.Success -> {
                _profileUiState.update { it.copy(isLoading = false, profile = result.data) }
            }

            is Response.Error -> _profileUiState.update {
                it.copy(
                    isLoading = false,
                    error = it.error
                )
            }
        }

    }

    fun uploadProfileImage(uri: Uri) = viewModelScope.launch {
        repository.uploadProfileImage(uri = uri)
            .flowOn(Dispatchers.IO)
            .catch {
                _uploadState.update { it.copy(error = "Upload Failed") }
            }
            .collect { status ->
                when (status) {
                    is UploadStatus.Progress -> _uploadState.update { it.copy(progress = (status.totalBytesSend / status.contentLength).toFloat() * 100) }
                    is UploadStatus.Success -> _uploadState.update { it.copy(isSuccess = true) }
                }
            }
    }

    fun profileUpdateErrorShown() {
        _profileUiState.update { it.copy(error = null) }
    }

    fun resetUploadState() {
        _uploadState.value = UploadState()
    }

    fun logOut() = viewModelScope.launch {
        _profileUiState.update { it.copy(isLoading = true) }
        when (repository.logOut()) {
            is Response.Error -> _profileUiState.update { it.copy(error = "Logout Failed!") }
            is Response.Success -> _profileUiState.update { it.copy(isLoggedOut = true) }
        }
    }

    private fun getPassword() = viewModelScope.launch {
        when (val response = repository.getPassword()) {
            is Response.Error -> {
                response.error.printStackTrace()
            }

            is Response.Success -> {
                Log.d("TAG", "getPassword: ${response.data}")
                val password = response.data
                _passwordState.value = password.substring(1, password.length - 1)
            }
        }
    }

    fun modifyUser(email: String = "", password: String = "") = viewModelScope.launch {
        val response = if (email.isNotEmpty()) {
            repository.modifyEmail(email)
        } else {
            repository.modifyPassword(password)
        }

        when (response) {
            is Response.Error -> {
                _userModifyState.value = UserModifyState.Error("Update Failed. Try again later!")
            }

            is Response.Success -> {
                _userModifyState.value =
                    if (email.isNotEmpty()) UserModifyState.Email(response.data.newEmail)
                    else {
                        val newPassword = response.data.userMetadata?.get("pw").toString()
                        UserModifyState.Password(newPassword.substring(1, newPassword.length - 1))
                    }
            }
        }
    }

    fun resetUserModifyState() {
        _userModifyState.value = null
    }

    init {
        getUserProfile()
        getPassword()
    }
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: Profile? = null,
    val isLoggedOut: Boolean = false,
    val error: String? = null
)

sealed class UserModifyState {
    class Password(val password: String?) : UserModifyState()
    class Email(val email: String?) : UserModifyState()
    class Error(val error: String) : UserModifyState()
}

data class UploadState(
    val isSuccess: Boolean = false,
    val progress: Float? = null,
    val error: String? = null
)
