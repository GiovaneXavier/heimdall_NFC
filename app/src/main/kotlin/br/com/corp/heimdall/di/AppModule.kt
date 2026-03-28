package br.com.corp.heimdall.di

import android.content.Context
import br.com.corp.heimdall.core.crypto.HmacValidator
import br.com.corp.heimdall.core.security.SecurePreferences
import br.com.corp.heimdall.core.util.SystemTimeProvider
import br.com.corp.heimdall.core.util.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHmacValidator(): HmacValidator = HmacValidator()

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()

    /**
     * O systemId configurado para este dispositivo é lido das preferences seguras.
     * Retorna string vazia se não configurado (o setup screen solicitará ao admin).
     */
    @Provides
    @Singleton
    @ConfiguredSystemId
    fun provideConfiguredSystemId(prefs: SecurePreferences): String =
        prefs.getString("configured_system_id") ?: ""
}
