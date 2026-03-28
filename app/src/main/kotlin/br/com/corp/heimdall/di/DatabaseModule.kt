package br.com.corp.heimdall.di

import android.content.Context
import androidx.room.Room
import br.com.corp.heimdall.core.security.SecurePreferences
import br.com.corp.heimdall.data.local.db.AccessLogDao
import br.com.corp.heimdall.data.local.db.HeimdallDatabase
import br.com.corp.heimdall.data.repository.AuditLogRepositoryImpl
import br.com.corp.heimdall.domain.repository.AuditLogRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DB_NAME = "heimdall.db"
    private const val PREF_DB_KEY = "db_cipher_key"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        securePrefs: SecurePreferences,
    ): HeimdallDatabase {
        val cipherKey = getOrCreateDbKey(securePrefs)
        val factory = SupportFactory(cipherKey.toByteArray(Charsets.UTF_8))
        return Room.databaseBuilder(context, HeimdallDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideAccessLogDao(db: HeimdallDatabase): AccessLogDao = db.accessLogDao()

    /** Gera e persiste a chave do banco se ainda não existir. */
    private fun getOrCreateDbKey(prefs: SecurePreferences): String {
        val existing = prefs.getString(PREF_DB_KEY)
        if (!existing.isNullOrBlank()) return existing

        val key = generateRandomKey()
        prefs.putString(PREF_DB_KEY, key)
        return key
    }

    private fun generateRandomKey(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuditLogModule {

    @Binds
    @Singleton
    abstract fun bindAuditLogRepository(impl: AuditLogRepositoryImpl): AuditLogRepository
}
