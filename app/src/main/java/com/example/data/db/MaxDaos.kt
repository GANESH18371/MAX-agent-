package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAllLogs(limit: Int = 50): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogsSync(limit: Int = 10): List<ActionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActionLogEntity): Long

    @Query("DELETE FROM action_logs WHERE id NOT IN (SELECT id FROM action_logs ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun pruneOldLogs(keepCount: Int = 30)

    @Query("DELETE FROM action_logs")
    suspend fun clearLogs()
}

@Dao
interface UserMemoryDao {
    @Query("SELECT * FROM user_memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memories ORDER BY updatedAt DESC")
    suspend fun getAllMemoriesSync(): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories WHERE memoryKey = :key LIMIT 1")
    suspend fun getMemory(key: String): UserMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMemory(memory: UserMemoryEntity)

    @Query("DELETE FROM user_memories WHERE memoryKey = :key")
    suspend fun deleteMemory(key: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isTriggered = 0 ORDER BY timeInMillis ASC")
    fun getActiveRemindersFlow(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isTriggered = 0 ORDER BY timeInMillis ASC")
    suspend fun getActiveRemindersSync(): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY timeInMillis DESC LIMIT 50")
    fun getAllRemindersFlow(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET isTriggered = 1 WHERE id = :id")
    suspend fun markTriggered(id: Int)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Int)

    @Query("DELETE FROM reminders WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%'")
    suspend fun deleteMatchingReminder(query: String): Int
}
