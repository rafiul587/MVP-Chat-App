package com.example.mvpchatapplication.ui.profile

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.utils.handleApiResponse
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.uploadAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import javax.inject.Inject

class ProfileRepository @Inject constructor(
    private val storage: Storage,
    private val goTrue: GoTrue,
    private val postgrest: Postgrest,
    private val realtime: Realtime,
) {
    private val uid = goTrue.currentUserOrNull()?.id

    suspend fun getUserProfile(): Response<Profile> {
        return handleApiResponse {
            postgrest.from("profiles")
                .select() {
                    eq("id", uid!!)
                }
                .decodeSingle()
        }
    }

    suspend fun updateProfile(profile: Profile): Response<Profile> {
        return handleApiResponse {
            postgrest.from("profiles").update({
                set("name", profile.name)
                set("phone", profile.phone)
                set("birthday", profile.birthday)
                Log.d(
                    "TAG",
                    "updateProfile: ${profile.name}, ${profile.phone}, ${profile.birthday}"
                )
            }) {
                eq("id", uid!!)
            }.decodeSingle()
        }
    }

    @OptIn(SupabaseExperimental::class)
    fun uploadProfileImage(uri: Uri): Flow<UploadStatus> {
        return storage["profile_images"]
            .uploadAsFlow("$uid.jpg", uri, upsert = true)
    }

    suspend fun logOut(): Response<Unit> {
        return handleApiResponse {
            goTrue.logout()
            realtime.removeAllChannels()
        }
    }

    suspend fun modifyEmail(email: String): Response<UserInfo> {
        return handleApiResponse {
            val userInfo = goTrue.modifyUser {
                this.email = email
            }
            goTrue.refreshCurrentSession()
            userInfo
        }
    }

    suspend fun modifyPassword(password: String): Response<UserInfo> {
        return handleApiResponse {
            val userInfo = goTrue.modifyUser {
                this.password = password
                data {
                    put("pw", password)
                }
            }
            goTrue.refreshCurrentSession()
            userInfo
        }
    }

    suspend fun getPassword(): Response<String> {
        return handleApiResponse {
            goTrue.retrieveUserForCurrentSession(false).userMetadata?.getValue("pw")
                .toString()
        }
    }
}