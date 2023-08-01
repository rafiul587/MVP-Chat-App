package com.example.mvpchatapplication.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Profile(
    val id: String = "",
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("profile_image")
    val profileImageUrl: String? = null,
    val birthday: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
): Parcelable
