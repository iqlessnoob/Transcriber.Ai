package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY id DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id ASC")
    fun getMessagesForChat(chatId: Int): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE chats SET title = :newTitle WHERE id = :chatId")
    suspend fun renameChat(chatId: Int, newTitle: String)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: Int)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Int)
}
