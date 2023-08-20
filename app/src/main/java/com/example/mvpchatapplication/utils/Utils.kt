package com.example.mvpchatapplication.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.text.format.DateUtils
import android.util.Log
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.example.mvpchatapplication.BuildConfig
import com.example.mvpchatapplication.R
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Message
import com.example.mvpchatapplication.ui.message.CaptureMediaFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


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
        if (date == null) return ""
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

enum class AdapterNotifyType {
    MessageWithNewDate,
    MessageOfExistingDate
}

sealed class MessageLoadStatus {
    data class Success(val chatId: Int): MessageLoadStatus()
    data class Failed(val receiverId: String): MessageLoadStatus()
    data class NotFound(val receiverId: String): MessageLoadStatus()
}

data class MessageContent(val message: Message) : MessageViewType() {
    override fun getType(userId: String): Int {
        return if (message.authorId == userId) {
            OWN
        } else OTHERS
    }
}


data class MessageDate(val date: String) : MessageViewType() {
    override fun getType(userId: String): Int {
        return DATE
    }
}

fun Date.isYesterday(): Boolean = DateUtils.isToday(this.time + DateUtils.DAY_IN_MILLIS)

fun Date.isToday(): Boolean = DateUtils.isToday(this.time)


fun String.toDayOrDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()

    try {
        val date = sdf.parse(this)

        return when  {
            date.isToday() -> {
                "Today"
            }
            date.isYesterday() -> {
                "Yesterday"
            }
            else -> {
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                formatter.timeZone = TimeZone.getDefault()
                formatter.format(date)
            }
        }
    } catch (e: Exception) {
        return ""
    }

}

fun String.toTimeOrDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()

    try {
        val date = sdf.parse(this)

        return when {
            date.isToday() -> {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                formatter.timeZone = TimeZone.getDefault()
                formatter.format(date!!)
            }
            date.isYesterday() -> {
                "Yesterday"
            }
            else -> {
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                formatter.timeZone = TimeZone.getDefault()
                formatter.format(date)
            }
        }
    } catch (e: Exception) {
        return ""
    }

}

fun String.toTime(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()
    return try {
        val date = sdf.parse(this)
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()
        formatter.format(date!!)
    } catch (e: Exception) {
        ""
    }
}

fun saveFileBitmapToFile(file: File): File? {
    return try {

        // BitmapFactory options to downsize the image
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        o.inSampleSize = 6
        // factor of downsizing the image
        var inputStream = FileInputStream(file)
        //Bitmap selectedBitmap = null;
        BitmapFactory.decodeStream(inputStream, null, o)
        inputStream.close()

        // The new size we want to scale to
        val REQUIRED_SIZE = 75

        // Find the correct scale value. It should be the power of 2.
        var scale = 1
        while (o.outWidth / scale / 2 >= REQUIRED_SIZE &&
            o.outHeight / scale / 2 >= REQUIRED_SIZE
        ) {
            scale *= 2
        }
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = scale
        inputStream = FileInputStream(file)
        val selectedBitmap = BitmapFactory.decodeStream(inputStream, null, o2)
        inputStream.close()

        // here i override the original image file
        file.createNewFile()
        val outputStream = FileOutputStream(file)
        selectedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        file
    } catch (e: Exception) {
        null
    }
}

fun scaleFullBitmap(fullSizeBitmap: Bitmap): Bitmap
{
    // assuming that on average the size of the images is 2.5 MB,
    // we would like to down scale it to ~250 KB
    val dividingScale = 8
    val scaledWidth : Int = fullSizeBitmap.width / dividingScale
    val scaledHeight : Int = fullSizeBitmap.height / dividingScale

    return Bitmap.createScaledBitmap(fullSizeBitmap, scaledWidth, scaledHeight, true)
}

fun getFileFromScaledBitmap(context: Context, reducedBitmap: Bitmap): File
{
    /*
     compress method takes quality as one of the parameters.
     For quality, the range of value expected is 0 - 100 where,
     0 - compress for a smaller size, 100 - compress for max quality.
    */
    val byteArrayOutputStream = ByteArrayOutputStream()
    reducedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
    val bitmapData = byteArrayOutputStream.toByteArray()

    val name = SimpleDateFormat(CaptureMediaFragment.FILENAME_FORMAT, Locale.getDefault())
        .format(System.currentTimeMillis())
    val extension = ".jpg"
    val file = File.createTempFile("IMG_$name", extension, context.cacheDir)
    file.deleteOnExit()
    try {
        val fileOutputStream = FileOutputStream(file)
        fileOutputStream.write(bitmapData)
        fileOutputStream.flush()
        fileOutputStream.close()
    }catch(ex : IOException){
        // Don't just print stack trace, do something meaningful! :D
    }
    return file
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
suspend fun <T : Any> handleApiResponse(apiCall: suspend () -> T): Response<T> {
    return withContext(Dispatchers.IO) {
        try {
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

interface DialogListener {
    fun onEmailChanged(email: String)
    fun onPasswordChanged(password: String)
}