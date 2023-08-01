package com.example.mvpchatapplication.ui.profile

import android.net.Uri
import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.data.models.Profile
import com.example.mvpchatapplication.utils.handleApiResponse
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.uploadAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.put
import javax.inject.Inject

class ProfileRepository @Inject constructor(
    private val storage: Storage,
    private val goTrue: GoTrue,
    private val postgrest: Postgrest
) {
    private val userId = goTrue.currentUserOrNull()?.id

    suspend fun getUserProfile(): Response<Profile> {
        return handleApiResponse {
            postgrest.from("profiles")
                .select() {
                    eq("id", userId!!)
                }
                .decodeSingle()
        }
    }

    suspend fun updateProfile(profile: Profile): Response<Profile> {
        return handleApiResponse {
            postgrest.from("profiles").update(profile).decodeSingle()
        }
    }

    @OptIn(SupabaseExperimental::class)
    fun uploadProfileImage(uri: Uri): Flow<UploadStatus> {
        return storage["profile_images"]
            .uploadAsFlow("$userId.jpg", uri, upsert = true)
    }

    suspend fun logOut(): Response<Unit> {
        return handleApiResponse {
            goTrue.logout()
        }
    }

    suspend fun modifyUser(email: String, password: String): Response<Unit> {
        return handleApiResponse {
            if (email.isNotEmpty() || password.isNotEmpty()) {
                goTrue.modifyUser {
                    if (email.isNotEmpty()) {
                        this.email = email
                    }
                    if (password.isNotEmpty()) {
                        this.password = password
                        data {
                            put("pw", password)
                        }
                    }
                }
                goTrue.refreshCurrentSession()
            }
        }
    }

    suspend fun getPassword(): Response<String> {
        return handleApiResponse {
            goTrue.retrieveUserForCurrentSession(false).userMetadata?.getValue("pw")
                .toString()
        }
    }
}