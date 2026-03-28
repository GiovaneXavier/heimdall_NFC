package br.com.corp.heimdall.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro de auditoria de cada leitura de token.
 *
 * Gravado após toda tentativa de validação, independente do resultado.
 * O banco é criptografado com SQLCipher — chave derivada de [SecurePreferences].
 */
@Entity(tableName = "access_log")
data class AccessLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Timestamp Unix em milissegundos do momento da leitura. */
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,

    /** Matrícula do funcionário, ou vazio se o token era inválido. */
    @ColumnInfo(name = "employee_id")
    val employeeId: String,

    /** Nome do funcionário, ou vazio se não disponível. */
    @ColumnInfo(name = "employee_name")
    val employeeName: String,

    /** Identificador do dispositivo que emitiu o token. */
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    /** Canal de leitura: "NFC" ou "QR". */
    @ColumnInfo(name = "channel")
    val channel: String,

    /** "APPROVED" ou "DENIED". */
    @ColumnInfo(name = "result")
    val result: String,

    /** Motivo da negação (enum [DenialReason].name), ou vazio se aprovado. */
    @ColumnInfo(name = "denial_reason")
    val denialReason: String,
)
