package com.example.mvpchatapplication.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class SupabaseLifecycleObserver @Inject constructor(
    val client: SupabaseClient
) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        owner.lifecycleScope.launch {
            kotlin.runCatching {
                if (client.realtime.status.value != Realtime.Status.CONNECTED) {
                    client.realtime.connect()
                }
            }
        }
        client.realtime.status.onEach {
            kotlin.runCatching {
                if (it == Realtime.Status.DISCONNECTED) {
                    client.realtime.removeAllChannels()
                }
            }
        }.launchIn(owner.lifecycleScope)

        client.gotrue.sessionStatus.onEach {
            kotlin.runCatching {
                when (it) {
                    is SessionStatus.Authenticated -> {
                        if (client.realtime.status.value != Realtime.Status.CONNECTED) {
                            client.realtime.connect()
                        }
                    }

                    else -> {}
                }
            }
        }.launchIn(owner.lifecycleScope)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            kotlin.runCatching {
                client.realtime.disconnect()
            }
        }
        super.onDestroy(owner)
    }
}