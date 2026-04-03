package br.com.corp.heimdall.di

import br.com.corp.heimdall.BuildConfig
import br.com.corp.heimdall.data.remote.api.AuditSyncApi
import br.com.corp.heimdall.data.remote.api.HeimdallApi
import br.com.corp.heimdall.data.remote.api.LegacyApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val timeout = BuildConfig.NETWORK_TIMEOUT_SECONDS
        return OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(
                json.asConverterFactory("application/json; charset=UTF8".toMediaType())
            )
            .build()

    @Provides
    @Singleton
    fun provideHeimdallApi(retrofit: Retrofit): HeimdallApi =
        retrofit.create(HeimdallApi::class.java)

    @Provides
    @Singleton
    fun provideLegacyApi(retrofit: Retrofit): LegacyApi =
        retrofit.create(LegacyApi::class.java)

    @Provides
    @Singleton
    fun provideAuditSyncApi(retrofit: Retrofit): AuditSyncApi =
        retrofit.create(AuditSyncApi::class.java)
}
