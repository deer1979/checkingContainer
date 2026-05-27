package com.checkingcontainer.core.data.remote

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.ApplicationScope
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.data.remote.dto.AnnouncementDto
import com.checkingcontainer.core.data.remote.dto.ReeferUnitDto
import com.checkingcontainer.core.data.remote.dto.UserDto
import com.checkingcontainer.core.data.remote.dto.toDto
import com.checkingcontainer.core.data.remote.dto.toEntity
import com.checkingcontainer.core.database.dao.AnnouncementDao
import com.checkingcontainer.core.database.dao.ReeferUnitDao
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.ReeferUnitEntity
import com.checkingcontainer.core.database.entity.UserEntity
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class SupabaseSyncService @Inject constructor(
    @Named("supabase_url") private val baseUrl: String,
    @Named("supabase_anon_key") private val anonKey: String,
    private val reeferUnitDao: ReeferUnitDao,
    private val userDao: UserDao,
    private val announcementDao: AnnouncementDao,
    @ApplicationScope private val appScope: CoroutineScope,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun pushReeferUnit(entity: ReeferUnitEntity) {
        appScope.launch {
            runCatching {
                upsert("reefer_units", json.encodeToString(listOf(entity.toDto())))
                reeferUnitDao.markSynced(entity.id)
                Log.i(TAG, "pushReeferUnit OK — id=${entity.id} container=${entity.containerNo}")
            }.onFailure { Log.e(TAG, "pushReeferUnit FAILED — ${it.message}") }
        }
    }

    fun pushUser(entity: UserEntity) {
        appScope.launch {
            runCatching {
                upsert("users", json.encodeToString(listOf(entity.toDto())))
                userDao.markSynced(entity.id)
                Log.i(TAG, "pushUser OK — id=${entity.id} nick=${entity.nick}")
            }.onFailure { Log.e(TAG, "pushUser FAILED — ${it.message}") }
        }
    }

    suspend fun pushPendingReeferUnits() = withContext(ioDispatcher) {
        reeferUnitDao.getPending().forEach { entity ->
            runCatching {
                upsert("reefer_units", json.encodeToString(listOf(entity.toDto())))
                reeferUnitDao.markSynced(entity.id)
                Log.i(TAG, "pushPending reefer_unit OK — id=${entity.id}")
            }.onFailure { Log.e(TAG, "pushPending reefer_unit FAILED — ${it.message}") }
        }
    }

    suspend fun pushPendingUsers() = withContext(ioDispatcher) {
        userDao.getPending().forEach { entity ->
            runCatching {
                upsert("users", json.encodeToString(listOf(entity.toDto())))
                userDao.markSynced(entity.id)
                Log.i(TAG, "pushPending user OK — id=${entity.id}")
            }.onFailure { Log.e(TAG, "pushPending user FAILED — ${it.message}") }
        }
    }

    suspend fun pullAnnouncements() = withContext(ioDispatcher) {
        runCatching {
            val body = get("announcements?order=published_at.desc")
            val dtos = json.decodeFromString<List<AnnouncementDto>>(body)
            if (dtos.isNotEmpty()) announcementDao.replaceAll(dtos.map { it.toEntity() })
            Log.i(TAG, "pullAnnouncements OK — ${dtos.size} rows")
        }.onFailure { Log.e(TAG, "pullAnnouncements FAILED — ${it.message}") }
    }

    private fun upsert(table: String, jsonBody: String) {
        val conn = openConnection("$baseUrl/rest/v1/$table", "POST")
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
        conn.doOutput = true
        conn.outputStream.bufferedWriter().use { it.write(jsonBody) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw IOException("Supabase upsert $table HTTP $code — $err")
        }
        conn.disconnect()
    }

    private fun get(path: String): String {
        val conn = openConnection("$baseUrl/rest/v1/$path", "GET")
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw IOException("Supabase get $path HTTP $code — $err")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return body
    }

    companion object {
        private const val TAG = "SupabaseSync"
    }

    private fun openConnection(urlStr: String, method: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer $anonKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        return conn
    }
}
