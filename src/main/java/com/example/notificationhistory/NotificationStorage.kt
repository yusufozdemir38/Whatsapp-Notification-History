package com.example.notificationhistory

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class NotificationStorage(context: Context) {

    companion object {
        private const val PREFS_NAME = "whatsapp_notification_history"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_MAX_HISTORY = "max_history"
        const val DEFAULT_MAX_HISTORY = 100

        private const val FIELD_ID = "id"
        private const val FIELD_SENDER = "sender"
        private const val FIELD_MESSAGE = "message"
        private const val FIELD_TIMESTAMP = "timestamp"
        private const val FIELD_IS_GROUP = "isGroup"
        private const val FIELD_IS_DELETED = "isDeleted"
        private const val FIELD_IS_MEDIA = "isMedia"
        private const val FIELD_IMAGE_PATH = "imagePath"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<NotificationItem> {
        val json = prefs.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        return parseJsonArray(json)
    }

    fun save(item: NotificationItem): Boolean {
        return try {
            val list = getAll().toMutableList()
            val maxHistory = getMaxHistory()
            list.add(0, item)
            while (list.size > maxHistory) list.removeAt(list.size - 1)
            saveList(list)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Belirli bir id'ye sahip kaydı "silindi" olarak işaretle.
     */
    fun markAsDeleted(id: Long): Boolean {
        return try {
            val list = getAll().toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index == -1) return false
            list[index] = list[index].copy(isDeleted = true)
            saveList(list)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Belirli bir gönderenin en son SİLİNMEMİŞ kaydını "silindi" yap.
     * WhatsApp gerçek silmede kimin sildiğini bildiriden alıyoruz ama
     * orijinal mesaj içeriğini bilmiyoruz → gönderene göre en son kaydı işaretle.
     */
    fun markLatestAsDeleted(senderName: String): Boolean {
        return try {
            val list = getAll().toMutableList()
            val index = list.indexOfFirst {
                it.senderName == senderName && !it.isDeleted
            }
            if (index == -1) return false
            list[index] = list[index].copy(isDeleted = true)
            saveList(list)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Medya bildirimlerine fotoğraf yolu atar.
     * MediaWatcherService yeni bir resim kopyaladığında çağırır.
     * Eşleşme: senderName + zaman aralığı (±30 saniye).
     */
    fun attachImagePath(senderName: String, imagePath: String, capturedAtMs: Long): Boolean {
        return try {
            val list = getAll().toMutableList()
            val toleranceMs = 30_000L
            val index = list.indexOfFirst {
                it.isMedia &&
                        it.imagePath == null &&
                        it.senderName == senderName &&
                        kotlin.math.abs(it.timestamp - capturedAtMs) < toleranceMs
            }
            if (index != -1) {
                list[index] = list[index].copy(imagePath = imagePath)
                saveList(list)
                return true
            }
            // Gönderen eşleşmezse en yakın medya bildirimini güncelle
            val fallbackIndex = list.indexOfFirst {
                it.isMedia && it.imagePath == null &&
                        kotlin.math.abs(it.timestamp - capturedAtMs) < toleranceMs
            }
            if (fallbackIndex != -1) {
                list[fallbackIndex] = list[fallbackIndex].copy(imagePath = imagePath)
                saveList(list)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun attachImageToLatestMedia(imagePath: String): Boolean {
        return try {
            val list = getAll().toMutableList()
            val index = list.indexOfFirst { it.isMedia && it.imagePath == null }
            if (index == -1) return false
            list[index] = list[index].copy(imagePath = imagePath)
            saveList(list)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteById(id: Long): Boolean {
        return try {
            val list = getAll().toMutableList()
            val removed = list.removeAll { it.id == id }
            if (removed) saveList(list)
            removed
        } catch (e: Exception) {
            false
        }
    }

    fun clearAll() {
        prefs.edit().remove(KEY_NOTIFICATIONS).apply()
    }

    fun getMaxHistory(): Int = prefs.getInt(KEY_MAX_HISTORY, DEFAULT_MAX_HISTORY)

    fun setMaxHistory(max: Int) {
        val clamped = max.coerceIn(10, 1000)
        prefs.edit().putInt(KEY_MAX_HISTORY, clamped).apply()
        val list = getAll().toMutableList()
        if (list.size > clamped) saveList(list.subList(0, clamped))
    }

    private fun saveList(list: List<NotificationItem>) {
        val jsonArray = JSONArray()
        for (item in list) jsonArray.put(itemToJson(item))
        prefs.edit().putString(KEY_NOTIFICATIONS, jsonArray.toString()).apply()
    }

    private fun itemToJson(item: NotificationItem): JSONObject {
        return JSONObject().apply {
            put(FIELD_ID, item.id)
            put(FIELD_SENDER, item.senderName)
            put(FIELD_MESSAGE, item.message)
            put(FIELD_TIMESTAMP, item.timestamp)
            put(FIELD_IS_GROUP, item.isGroup)
            put(FIELD_IS_DELETED, item.isDeleted)
            put(FIELD_IS_MEDIA, item.isMedia)
            item.imagePath?.let { put(FIELD_IMAGE_PATH, it) }
        }
    }

    private fun parseJsonArray(json: String): List<NotificationItem> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                NotificationItem(
                    id = obj.getLong(FIELD_ID),
                    senderName = obj.getString(FIELD_SENDER),
                    message = obj.getString(FIELD_MESSAGE),
                    timestamp = obj.getLong(FIELD_TIMESTAMP),
                    isGroup = obj.optBoolean(FIELD_IS_GROUP, false),
                    isDeleted = obj.optBoolean(FIELD_IS_DELETED, false),
                    isMedia = obj.optBoolean(FIELD_IS_MEDIA, false),
                    imagePath = obj.optString(FIELD_IMAGE_PATH).takeIf { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
