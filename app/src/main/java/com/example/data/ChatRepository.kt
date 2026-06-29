package com.example.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {
    val allChats: Flow<List<ChatEntity>> = chatDao.getAllChats()

    fun getMessagesForChat(chatId: Int): Flow<List<MessageEntity>> {
        return chatDao.getMessagesForChat(chatId)
    }

    suspend fun createChat(title: String): Int {
        val chat = ChatEntity(title = title)
        return chatDao.insertChat(chat).toInt()
    }

    suspend fun addMessage(chatId: Int, text: String, isSubtitles: Boolean) {
        val msg = MessageEntity(chatId = chatId, text = text, isSubtitles = isSubtitles)
        chatDao.insertMessage(msg)
    }

    suspend fun renameChat(chatId: Int, newTitle: String) {
        chatDao.renameChat(chatId, newTitle)
    }

    suspend fun deleteChat(chatId: Int) {
        chatDao.deleteMessagesForChat(chatId)
        chatDao.deleteChat(chatId)
    }
}
