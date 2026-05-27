package com.checkingcontainer.core.data

import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.UserEntity
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.UsersRepository
import com.checkingcontainer.core.model.JobTitle
import com.checkingcontainer.core.model.User
import com.checkingcontainer.core.model.UserRole
import com.checkingcontainer.core.network.SupabaseClientHolder
import com.checkingcontainer.core.network.dto.UserDto
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "UsersRepo"
private const val TABLE = "users"

@Singleton
class UsersRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val supabase: SupabaseClientHolder,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : UsersRepository {

    private val syncScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        syncScope.launch { pullFromSupabase() }
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<User>> =
        userDao.observeAll()
            .map { rows -> rows.map(UserEntity::toDomain) }
            .flowOn(ioDispatcher)

    override suspend fun getById(id: Long): User? = withContext(ioDispatcher) {
        userDao.findById(id)?.toDomain()
    }

    override suspend fun count(): Int = withContext(ioDispatcher) { userDao.count() }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun create(user: User): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val id = userDao.insert(user.toEntity())
            syncScope.launch { pushCreate(user.copy(id = id)) }
            id
        }
    }

    override suspend fun update(user: User): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            userDao.update(user.toEntity())
            syncScope.launch { pushUpdate(user) }
            Unit
        }
    }

    override suspend fun setActive(id: Long, isActive: Boolean): Unit = withContext(ioDispatcher) {
        userDao.setActive(id, isActive)
        syncScope.launch { pushSetActive(id, isActive) }
        Unit
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        userDao.delete(id)
        syncScope.launch { pushDelete(id) }
        Unit
    }

    // ── Supabase helpers ─────────────────────────────────────────────────────

    private suspend fun pullFromSupabase() {
        val client = supabase.client ?: return
        runCatching {
            val remote = client.from(TABLE).select().decodeList<UserDto>()
            // Upsert by nick (the unique business key) — skip if already local
            remote.forEach { dto ->
                if (userDao.findByNick(dto.nick) == null) {
                    userDao.insert(dto.toEntity())
                }
            }
            Log.d(TAG, "Pulled ${remote.size} users from Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase pull failed — running in local-only mode", e)
        }
    }

    private suspend fun pushCreate(user: User) {
        val client = supabase.client ?: return
        runCatching {
            client.from(TABLE).insert(user.toDto())
            Log.d(TAG, "Pushed new user ${user.nick} to Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase push (create) failed for ${user.nick}", e)
        }
    }

    private suspend fun pushUpdate(user: User) {
        val client = supabase.client ?: return
        runCatching {
            client.from(TABLE).update({
                set("first_name", user.firstName)
                set("last_name", user.lastName)
                set("pin", user.pin)
                set("job_title", user.jobTitle.name)
                set("role", user.role.name)
                set("company", user.company)
                set("location", user.location)
                set("is_active", user.isActive)
            }) {
                filter { eq("nick", user.nick) }
            }
            Log.d(TAG, "Updated user ${user.nick} on Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase push (update) failed for ${user.nick}", e)
        }
    }

    private suspend fun pushSetActive(id: Long, isActive: Boolean) {
        val client = supabase.client ?: return
        runCatching {
            client.from(TABLE).update({
                set("is_active", isActive)
            }) {
                filter { eq("local_id", id) }
            }
        }.onFailure { e ->
            Log.w(TAG, "Supabase push (setActive) failed for id=$id", e)
        }
    }

    private suspend fun pushDelete(id: Long) {
        val client = supabase.client ?: return
        runCatching {
            client.from(TABLE).delete { filter { eq("local_id", id) } }
            Log.d(TAG, "Deleted user id=$id from Supabase")
        }.onFailure { e ->
            Log.w(TAG, "Supabase push (delete) failed for id=$id", e)
        }
    }
}

// ── Mapping helpers ───────────────────────────────────────────────────────────

private fun User.toDto() = UserDto(
    firstName = firstName,
    lastName = lastName,
    nick = nick,
    pin = pin,
    jobTitle = jobTitle.name,
    role = role.name,
    company = company,
    location = location,
    isActive = isActive,
    localId = id.takeIf { it != 0L },
)

private fun UserDto.toEntity() = UserEntity(
    id = localId ?: 0L,
    firstName = firstName,
    lastName = lastName,
    nick = nick,
    pin = pin,
    jobTitle = runCatching { JobTitle.valueOf(jobTitle) }.getOrDefault(JobTitle.Tecnico),
    role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.Viewer),
    company = company,
    location = location,
    isActive = isActive,
)
