package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userCommand: String,
    val targetApp: String,
    val aiReasoning: String,
    val actionType: String, // OPEN_APP, CLICK, TYPE, SWIPE, SKIP_AD, SPEAK
    val actionDetail: String,
    val isSuccess: Boolean
)

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey val memoryKey: String,
    val memoryValue: String,
    val category: String, // preference, habitual_query, last_activity
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val timeInMillis: Long,
    val formattedTime: String,
    val isTriggered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
