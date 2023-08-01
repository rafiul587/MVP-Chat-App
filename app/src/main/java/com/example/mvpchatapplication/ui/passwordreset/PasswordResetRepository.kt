package com.example.mvpchatapplication.ui.passwordreset

import com.example.mvpchatapplication.data.Response
import com.example.mvpchatapplication.utils.handleApiResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.serialization.json.put
import javax.inject.Inject

class PasswordResetRepository @Inject constructor(
    private val goTrue: GoTrue
) {

    suspend fun sendForgotPasswordLink(email: String): Response<Unit> {
        return handleApiResponse { goTrue.sendRecoveryEmail(email) }
    }

    suspend fun modifyUser(newPassword: String): Response<UserInfo> {
        return handleApiResponse {
            goTrue.modifyUser {
                password = newPassword
                data {
                    put("pw", newPassword)
                }
            }
        }
    }
}