package br.com.corp.heimdall.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AccessLogEntry)

    /** Retorna os últimos [limit] registros, do mais recente para o mais antigo. */
    @Query("SELECT * FROM access_log ORDER BY timestamp_ms DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AccessLogEntry>>

    /** Retorna todos os registros a partir de um timestamp (para export/sincronia). */
    @Query("SELECT * FROM access_log WHERE timestamp_ms >= :sinceMs ORDER BY timestamp_ms ASC")
    suspend fun getEntriesSince(sinceMs: Long): List<AccessLogEntry>

    @Query("DELETE FROM access_log WHERE timestamp_ms < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}
