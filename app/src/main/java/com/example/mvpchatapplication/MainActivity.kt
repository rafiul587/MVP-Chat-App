package com.example.mvpchatapplication

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.example.mvpchatapplication.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    @Inject lateinit var client: SupabaseClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        // Get the NavController associated with the NavHostFragment
        val navController = navHostFragment.findNavController()
        client.handleDeeplinks(intent)
        intent?.data?.let {
            val bundle = Bundle()
            bundle.putString("path", it.host)
            navController.navigate(R.id.navigation_password_reset, bundle)

            Log.d("TAG", "onCreate: ${it.query}, ${it.path}, ${it.host}")
        }
    }
}
/*

fun main() {
    fun bindEpochTimeMsToDateWithDaysAgo(epochTimeMs: Long): String {
        val currentTime = Date().time
        val timeDifference = currentTime - epochTimeMs
        val numOfDays = TimeUnit.MILLISECONDS.toDays(timeDifference)

        val text = when {
            numOfDays in 1..6 -> SimpleDateFormat("EEE", Locale.getDefault()).format(
                Date(
                    epochTimeMs
                )
            )

            numOfDays >= 7 -> {
                val unit = when {
                    numOfDays >= 365 -> Pair(numOfDays / 365, "y") // Years
                    numOfDays >= 30 -> Pair(numOfDays / 30, "m") // Months
                    else -> Pair(numOfDays / 7, "w") // Weeks
                }
                "${unit.first}${unit.second} ago"
            }

            else -> {
                val pat =
                    SimpleDateFormat().toLocalizedPattern().replace("\\W?[YyMd]+\\W?".toRegex(), "")
                val formatter = SimpleDateFormat(pat, Locale.getDefault())
                formatter.format(Date(epochTimeMs))
            }
        }
        return text
    }

        val timestamps = listOf(
            System.currentTimeMillis(), // Current time
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5), // 5 minutes ago (just now)
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30), // 30 minutes ago
            System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2), // 2 hours ago
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1), // Yesterday
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4), // 4 days ago
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(9), // 9 days ago (1 week + 2 days)
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45), // 45 days ago (1 month + 15 days)
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(370), // 370 days ago (1 year + 5 days)
        )

        for (timestamp in timestamps) {
            println("Timestamp: $timestamp")
            println("Formatted Text: ${bindEpochTimeMsToDateWithDaysAgo(timestamp).trim()}")
            println()
        }
}*/
