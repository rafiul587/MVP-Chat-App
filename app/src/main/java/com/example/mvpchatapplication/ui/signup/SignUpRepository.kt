package com.example.mvpchatapplication.ui.signup

import com.example.mvpchatapplication.data.Response
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class SignUpRepository @Inject constructor(
    private val goTrue: GoTrue
) {

    suspend fun signUp(email: String, password: String): Response<Email.Result?> {
        return withContext(Dispatchers.IO) {
            try {
                val response = goTrue.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    this.data = buildJsonObject {
                        put("pw", password)
                    }
                }
                Response.Success(response)
            } catch (e: Exception) {
                e.printStackTrace()
                Response.Error(e)
            }
        }
    }
}