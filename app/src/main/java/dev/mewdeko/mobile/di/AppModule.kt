package dev.mewdeko.mobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mewdeko.mobile.core.net.MewdekoJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/** Application-wide singletons: HTTP, JSON, and the app-lifetime coroutine scope. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** The process-lifetime scope used for work that must outlive a screen. */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Plain application context for stores that need one. */
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    /** Shared Ktor client. Base URLs vary per dashboard, so none is bound here. */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(MewdekoJson) }
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }
}
