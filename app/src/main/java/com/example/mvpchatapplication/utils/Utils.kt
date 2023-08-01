package com.example.mvpchatapplication.utils

import android.util.Log
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.example.mvpchatapplication.BuildConfig
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * This is just a wrapper of [Lifecycle.repeatOnLifecycle] for specific use case. This can lead to some unintended behaviour
 * if you use it within another coroutine. So read the article before using it
 * [Article Link](https://medium.com/androiddevelopers/repeatonlifecycle-api-design-story-8670d1a7d333)
 */

inline fun <T> Flow<T>.launchAndCollectLatest(
    owner: LifecycleOwner,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline action: suspend CoroutineScope.(T) -> Unit
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(minActiveState) {
            collectLatest {
                action(it)
            }
        }
    }
}

fun ImageView.loadProfileImage(url: String?) {
    Log.d("TAG", "loadProfileImage: ${BuildConfig.SUPABASE_URL}/storage/v1/object/public/profile_images/$url")
    val urlSplit = url?.split("_")
    Glide.with(this)
        .load("${BuildConfig.SUPABASE_URL}/storage/v1/object/public/profile_images/${urlSplit?.lastOrNull()}?mod=${urlSplit?.firstOrNull() ?: ""}")
        .override(200)
        .error(R.drawable.baseline_account_circle_24)
        .placeholder(R.drawable.icon_park_outline_loading_one)
        .circleCrop()
        .into(this)
}

fun String.isValidEmail() =
    isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

fun String.isValidPhoneNumber() =
    isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

@Serializable
@SerialName("message_type")
enum class MessageType {
    @SerialName("Text")
    TEXT,

    @SerialName("Image")
    IMAGE,

    @SerialName("Video")
    VIDEO
}

object DateParser {
    private val dateFormat1: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX")
    fun convertDateToString(date: String?): String {
        if(date == null) return ""
        var strDate = ""
        strDate = dateFormat1.format(date)
        return strDate
    }
}

fun String.isDateValid(): Boolean {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    dateFormat.isLenient = false

    return try {
        dateFormat.parse(this)
        true
    } catch (e: ParseException) {
        false
    }
}

abstract class MessageViewType {
    companion object {
        const val DATE = 0
        const val OWN = 1
        const val OTHERS = 2
    }

    abstract fun getType(userId: String): Int
}

data class MessageContent(val message: Message) : MessageViewType() {
    override fun getType(userId: String): Int {
        return if (message.authorId === userId) {
            OWN
        } else OTHERS
    }
}

data class MessageDate(val date: String) : MessageViewType() {
    override fun getType(userId: String): Int {
        return DATE
    }
}

/**
 * Executes an API call on the [Dispatchers.IO] with error handling. It catches any exceptions
 * that may occur during the API call and converts them into a [Response] object.
 *
 * @param apiCall The suspending function that represents the API call.
 * @return A [Response] object representing the result of the API call.
 *
 * @see Response
 */
suspend fun <T: Any> handleApiResponse(apiCall: suspend () -> T): Response<T> {
    return withContext(Dispatchers.IO) {
        try {
            Log.d("TAG", "handleApiResponse: ")
            Response.Success(apiCall())
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
            e.printStackTrace()
            Response.Error(e)
        }
    }
}