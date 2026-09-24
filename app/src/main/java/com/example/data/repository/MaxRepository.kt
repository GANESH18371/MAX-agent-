package com.example.data.repository

import com.example.data.db.ActionLogDao
import com.example.data.db.ActionLogEntity
import com.example.data.db.ReminderDao
import com.example.data.db.ReminderEntity
import com.example.data.db.UserMemoryDao
import com.example.data.db.UserMemoryEntity
import kotlinx.coroutines.flow.Flow

class MaxRepository(
    private val actionLogDao: ActionLogDao,
    private val userMemoryDao: UserMemoryDao,
    private val reminderDao: ReminderDao
) {
    val allLogs: Flow<List<ActionLogEntity>> = actionLogDao.getAllLogs(50)
    val allMemories: Flow<List<UserMemoryEntity>> = userMemoryDao.getAllMemories()
    val activeReminders: Flow<List<ReminderEntity>> = reminderDao.getActiveRemindersFlow()

    suspend fun getRecentLogs(limit: Int = 10): List<ActionLogEntity> {
        return actionLogDao.getRecentLogsSync(limit)
    }

    suspend fun getAllMemoriesSync(): List<UserMemoryEntity> {
        return userMemoryDao.getAllMemoriesSync()
    }

    suspend fun logAction(
        userCommand: String,
        targetApp: String,
        aiReasoning: String,
        actionType: String,
        actionDetail: String,
        isSuccess: Boolean
    ) {
        actionLogDao.insertLog(
            ActionLogEntity(
                userCommand = userCommand,
                targetApp = targetApp,
                aiReasoning = aiReasoning,
                actionType = actionType,
                actionDetail = actionDetail,
                isSuccess = isSuccess
            )
        )
        // Automatically prune old logs so storage remains light and processing efficient
        actionLogDao.pruneOldLogs(30)
    }

    suspend fun saveMemory(key: String, value: String, category: String = "general") {
        userMemoryDao.insertOrUpdateMemory(
            UserMemoryEntity(
                memoryKey = key,
                memoryValue = value,
                category = category,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Checks if user explicitly commanded a fact to be remembered (e.g., "yaad rakho ki...", "mera favorite app YouTube hai")
     */
    suspend fun checkForExplicitMemoryCommand(commandText: String): Boolean {
        val lowerCmd = commandText.lowercase()

        if (lowerCmd.startsWith("yaad rakho") || lowerCmd.contains("remember that") || lowerCmd.contains("note kar lo")) {
            val fact = lowerCmd
                .replace("yaad rakho ki", "")
                .replace("yaad rakho", "")
                .replace("remember that", "")
                .replace("note kar lo", "")
                .trim()

            if (fact.isNotBlank()) {
                val key = "user_fact_${System.currentTimeMillis() / 1000}"
                saveMemory(key, fact, "explicit_memory")
                return true
            }
        }

        if (lowerCmd.contains("mera favorite") || lowerCmd.contains("meri favorite") || lowerCmd.contains("mujhe pasand hai")) {
            val key = "user_preference_${System.currentTimeMillis() / 1000}"
            saveMemory(key, commandText, "user_preference")
            return true
        }

        return false
    }

    suspend fun getMemory(key: String): String? {
        return userMemoryDao.getMemory(key)?.memoryValue
    }

    suspend fun deleteMemory(key: String) {
        userMemoryDao.deleteMemory(key)
    }

    suspend fun clearLogs() {
        actionLogDao.clearLogs()
    }

    suspend fun deleteReminder(id: Int) {
        reminderDao.deleteReminderById(id)
    }
}
