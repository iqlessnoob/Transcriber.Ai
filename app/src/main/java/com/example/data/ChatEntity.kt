package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdTime: Long = System.currentTimeMillis()
)
