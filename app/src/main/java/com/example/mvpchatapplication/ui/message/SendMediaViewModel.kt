package com.example.mvpchatapplication.ui.message

import android.net.Uri
import androidx.core.net.toFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvpchatapplication.data.models.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SendMediaViewModel @Inject constructor(
        private val client: SupabaseClient,
) : ViewModel() {


    private var _uploadState = MutableStateFlow<UploadState?>(null)
    val uploadState = _uploadState.asStateFlow()


    @OptIn(SupabaseExperimental::class)
    fun uploadImage(media: Media) = viewModelScope.launch {
        val bucket = client.storage["images"]
        bucket.uploadAsFlow(media.name, Uri.parse(media.uri).toFile())
                .catch { UploadState.Error("Upload Failed!") }
                .collect { status ->
                    when (status) {
                        is UploadStatus.Progress -> {
                            _uploadState.value = UploadState.Uploading(progress = (status.totalBytesSend.toFloat() / status.contentLength * 100))
                        }

                        is UploadStatus.Success -> {
                            _uploadState.value = UploadState.Success
                        }
                    }
                }
    }

    @OptIn(SupabaseExperimental::class)
    fun uploadVideo(media: Media) = viewModelScope.launch {
        val bucket = client.storage["videos"]
        bucket.uploadAsFlow(media.name, Uri.parse(media.uri).toFile())
                .catch { UploadState.Error("Upload Failed!") }
                .collect { status ->
                    when (status) {
                        is UploadStatus.Progress -> {
                            _uploadState.value = UploadState.Uploading(progress = (status.totalBytesSend.toFloat() / status.contentLength * 100))
                        }

                        is UploadStatus.Success -> {
                            _uploadState.value = UploadState.Success
                        }
                    }
                }
    }

    fun uploadErrorShown() {
        _uploadState.value = null
    }

}

sealed class UploadState {
    object Success : UploadState()
    class Uploading(val progress: Float = 0f) : UploadState()
    class Error(val error: String) : UploadState()
}
