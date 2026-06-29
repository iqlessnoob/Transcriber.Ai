package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chatId: Int,
    val text: String,
    val isSubtitles: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
