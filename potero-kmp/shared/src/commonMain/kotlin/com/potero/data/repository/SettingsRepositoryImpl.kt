package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of SettingsRepository using SQLDelight
 */
class SettingsRepositoryImpl(
    private val database: PoteroDatabase
) : SettingsRepository {

    private val settingsQueries = database.settingsQueries

    override suspend fun get(key: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            settingsQueries.selectByKey(key).executeAsOneOrNull()
        }
    }

    override suspend fun set(key: String, value: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            settingsQueries.upsert(key, value)
        }
    }

    override suspend fun getAll(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsQueries.selectAll().executeAsList()
            val result = mutableMapOf<String, String>()
            for (setting in settings) {
                result[setting.key] = setting.value_
            }
            result.toMap()
        }
    }

    override suspend fun delete(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            settingsQueries.delete(key)
        }
    }

    override suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            settingsQueries.deleteAll()
        }
    }
}
