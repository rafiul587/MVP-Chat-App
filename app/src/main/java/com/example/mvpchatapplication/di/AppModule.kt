package com.example.mvpchatapplication.di

import com.example.mvpchatapplication.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.FlowType
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.createChannel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @OptIn(SupabaseExperimental::class)
    @Singleton
    @Provides
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            //Already the default serializer
            install(GoTrue){
                scheme = "com.example.mvpchatapplication"
                host = "login-callback"
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    @Provides
    @Singleton
    fun provideSupabaseDatabase(client: SupabaseClient): Postgrest {
        return client.postgrest
    }

    @Provides
    @Singleton
    @ChatChannel
    fun provideChatChannel(realtime: Realtime): RealtimeChannel {
        return realtime.createChannel("#chats")
    }

    @Provides
    @Singleton
    @MessageChannel
    fun
            provideMessageChannel(realtime: Realtime): RealtimeChannel {
        return realtime.createChannel("#messages")
    }

    @Provides
    @Singleton
    fun provideSupabaseGoTrue(client: SupabaseClient): GoTrue {
        return client.gotrue
    }

    @Provides
    @Singleton
    fun provideSupabaseRealtime(client: SupabaseClient): Realtime {
        return client.realtime
    }
    @Provides
    @Singleton
    fun provideSupabaseStorage(client: SupabaseClient): Storage {
        return client.storage
    }

    /*    @Singleton
        @Provides
        fun provideAppDataStore(@ApplicationContext context: Context): AppDataStore =
            AppDataStore(context = context)*/
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatChannel

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MessageChannel