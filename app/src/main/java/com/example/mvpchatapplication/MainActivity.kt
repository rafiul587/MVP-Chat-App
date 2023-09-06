package com.example.mvpchatapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.example.mvpchatapplication.databinding.ActivityMainBinding
import com.example.mvpchatapplication.di.ChatChannel
import com.example.mvpchatapplication.di.MessageChannel
import com.example.mvpchatapplication.utils.SupabaseLifecycleObserver
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.handleDeeplinks
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    @Inject
    lateinit var client: SupabaseClient

    @Inject
    @ChatChannel
    lateinit var chatChannel: RealtimeChannel

    @Inject
    @MessageChannel
    lateinit var messageChannel: RealtimeChannel

    @Inject
    lateinit var supabaseObserver: SupabaseLifecycleObserver
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        // Get the NavController associated with the NavHostFragment
        val navController = navHostFragment.findNavController()
        client.handleDeeplinks(intent)
        lifecycle.addObserver(supabaseObserver)
        intent?.data?.let {
            val bundle = Bundle()
            bundle.putString("path", it.host)
            navController.navigate(R.id.navigation_password_reset, bundle)
        }

    }

    fun leaveMessageChannel() {
        lifecycleScope.launch {
            messageChannel.leave()
        }
    }

    fun leaveChatChannel() {
        lifecycleScope.launch {
            chatChannel.leave()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(supabaseObserver)
    }
}
