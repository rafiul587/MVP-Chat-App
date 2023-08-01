package com.example.mvpchatapplication.ui.signin

import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.utils.handleApiResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class SignInRepository @Inject constructor(
    private val goTrue: GoTrue
) {
    suspend fun signIn(email: String, password: String): Response<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = goTrue.loginWith(Email) {
                    this.email = email
                    this.password = password
                }
                Response.Success(response)
            } catch (e: Exception) {
                Response.Error(e)
            }
        }
    }

    suspend fun isLoggedIn(): Response<UserInfo> {
        return handleApiResponse {
            goTrue.retrieveUserForCurrentSession(true)
        }
    }
}