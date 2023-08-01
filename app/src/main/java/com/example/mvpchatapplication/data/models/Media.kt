package com.example.mvpchatapplication.data.models

import android.os.Parcelable
import com.example.mvpchatapplication.utils.MessageType
import kotlinx.parcelize.Parcelize

@Parcelize
data class Media(
    val type: MessageType,
    val name: String,
    val uri: String
): Parcelable
