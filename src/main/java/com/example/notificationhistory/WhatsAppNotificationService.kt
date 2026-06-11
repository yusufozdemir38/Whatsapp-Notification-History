package com.example.notificationhistory

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * WhatsApp bildirimlerini yakalayan arka plan servisi.
 *
 * DÜZELTME (v3):
 * Çift yönlü silme tespiti:
 *
 * Yöntem 1 — onNotificationPosted():
 *   WhatsApp bazı durumlarda "Bu mesaj silindi" metniyle YENİ bir
 *   bildirim gönderir. DELETED_PHRASES listesiyle yakalanır.
 *
 * Yöntem 2 — onNotificationRemoved() + REASON_APP_CANCEL:
 *   WhatsApp bazen yeni bildirim göndermeden doğrudan eski bildirimi
 *   iptal eder. Uygulama iptallerini (reason=8) kullanıcı kaydırmasından
 *   (reason=2) ayırt ederek sadece gerçek silmeleri işaretler.
 *   Yanlış pozitif riski: mesaj okunduğunda WA da REASON_APP_CANCEL
 *   gönderebilir. Bunu önlemek için son 5 saniye içinde kaydedilmiş
 *   bildirimler dışındaki kayıtları işaretlemiyoruz.
 */
class WhatsAppNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "WANotifService"

        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        private const val EXTRA_TITLE = Notification.EXTRA_TITLE
        private const val EXTRA_TEXT = Notification.EXTRA_TEXT
        private const val EXTRA_BIG_TEXT = Notification.EXTRA_BIG_TEXT

        const val ACTION_NOTIFICATION_RECEIVED =
            "com.example.notificationhistory.NOTIFICATION_RECEIVED"

        // WhatsApp'ın "silindi" bildirimi için anahtar kelimeler
        private val DELETED_PHRASES = listOf(
            "bu mesaj silindi",
            "this message was deleted",
            "you deleted this message",
            "bu mesaj geri alındı",
            "mesaj silindi",
            "message was deleted",
            "deleted this message"
        )

        // onNotificationRemoved — uygulama iptali sebebi
        private const val REASON_APP_CANCEL = 8
        private const val REASON_APP_CANCEL_ALL = 9

        /**
         * Mesaj kaydedildikten sonra bu süre (ms) içinde bildirimi
         * kaldırılırsa "okundu & WA kapandı" senaryosu sayılır → işaretleme.
         * Bu süreden SONRA kaldırılırsa zaten okunmuş demektir → atla.
         */
        private const val DELETION_WINDOW_MS = 20_000L
    }

    private lateinit var storage: NotificationStorage

    override fun onCreate() {
        super.onCreate()
        storage = NotificationStorage(applicationContext)
        Log.d(TAG, "Servis başlatıldı")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(EXTRA_TITLE)?.toString()?.trim() ?: return
        val rawText = extras.getCharSequence(EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(EXTRA_TEXT)?.toString()?.trim()
            ?: ""

        // DEBUG: Her gelen WhatsApp bildirimini logla
        Log.d(TAG, "▶ Bildirim geldi | title=$title | text=${rawText.take(80)}")

        if (isSystemNotification(title, rawText)) {
            Log.d(TAG, "  → Sistem bildirimi, atlandı")
            return
        }

        // ── Yöntem 1: Metin bazlı silme tespiti ────────────────────────
        val lowerText = rawText.lowercase()
        if (DELETED_PHRASES.any { lowerText.contains(it) }) {
            Log.d(TAG, "  → SİLDİ tespiti (metin): $title | rawText=$rawText")
            val marked = storage.markLatestAsDeleted(senderName = title)
            if (marked) {
                Log.d(TAG, "  → Kayıt silindi olarak işaretlendi: $title")
                sendBroadcast(Intent(ACTION_NOTIFICATION_RECEIVED))
            } else {
                Log.w(TAG, "  → İşaretlenecek kayıt bulunamadı: $title")
            }
            return
        }
        // ────────────────────────────────────────────────────────────────

        val isMediaNotification = rawText.contains("image omitted", ignoreCase = true) ||
                rawText.contains("video omitted", ignoreCase = true) ||
                rawText.contains("audio omitted", ignoreCase = true) ||
                rawText.startsWith("📷") || rawText.startsWith("🎥") ||
                rawText.startsWith("🎵") || rawText.startsWith("📎")

        val text = when {
            rawText.isEmpty() && isMediaNotification -> "📷 Fotoğraf"
            rawText.contains("image omitted", ignoreCase = true) -> "📷 Fotoğraf"
            rawText.contains("video omitted", ignoreCase = true) -> "🎥 Video"
            rawText.contains("audio omitted", ignoreCase = true) -> "🎵 Ses mesajı"
            rawText.isEmpty() -> {
                Log.d(TAG, "  → Boş metin, atlandı")
                return
            }
            else -> rawText
        }

        val isGroup = text.contains(": ") && !title.contains("WhatsApp")

        val item = NotificationItem(
            id = sbn.postTime,
            senderName = title,
            message = text,
            timestamp = sbn.postTime,
            isGroup = isGroup,
            isMedia = isMediaNotification
        )

        val saved = storage.save(item)
        if (saved) {
            Log.d(TAG, "  → Kaydedildi: $title → ${text.take(50)}")
            sendBroadcast(Intent(ACTION_NOTIFICATION_RECEIVED))
        }
    }

    /**
     * Yöntem 2: Uygulama iptali (REASON_APP_CANCEL) üzerinden silme tespiti.
     *
     * WhatsApp, "Herkesten sil" yapıldığında:
     *   a) Yeni "Bu mesaj silindi" bildirimi gönderebilir  → Yöntem 1 yakalar
     *   b) Sadece mevcut bildirimi iptal edebilir           → Yöntem 2 yakalar
     *
     * Yanlış pozitif önlemi: kullanıcı WhatsApp'ı açıp mesajı okursa
     * WA da bildirimi iptal eder (reason=8). Bunu ayırt etmek için
     * DELETION_WINDOW_MS sınırı uygulanıyor; pencere dışındaki kayıtlar
     * zaten okunmuş sayılır ve işaretlenmez.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val packageName = sbn.packageName
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) return

        Log.d(TAG, "◀ Bildirim kaldırıldı | reason=$reason | pkg=$packageName")

        if (reason != REASON_APP_CANCEL && reason != REASON_APP_CANCEL_ALL) {
            Log.d(TAG, "  → Kullanıcı kaydırması/temizleme, işaretleme yapılmadı")
            return
        }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(EXTRA_TITLE)?.toString()?.trim() ?: return

        // Sadece DELETION_WINDOW_MS içinde kaydedilmiş son kaydı işaretle
        val now = System.currentTimeMillis()
        val recent = storage.getAll().firstOrNull {
            it.senderName == title &&
            !it.isDeleted &&
            (now - it.timestamp) < DELETION_WINDOW_MS
        }

        if (recent != null) {
            Log.d(TAG, "  → SİLDİ tespiti (app cancel, pencere içi): $title")
            val marked = storage.markAsDeleted(recent.id)
            if (marked) {
                Log.d(TAG, "  → Kayıt işaretlendi (id=${recent.id})")
                sendBroadcast(Intent(ACTION_NOTIFICATION_RECEIVED))
            }
        } else {
            Log.d(TAG, "  → Pencere dışı veya kayıt yok, işaretleme atlandı: $title")
        }
    }

    // Eski imza — yeni imzaya yönlendir
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Üst sınıf onNotificationRemoved(sbn, rankingMap, reason) çağırır
        // Burası kasıtlı boş
    }

    private fun isSystemNotification(title: String, text: String): Boolean {
        val systemPhrases = listOf(
            "okunmamış mesaj",
            "missed call",
            "kaçırılan arama",
            "ongoing call",
            "süregelen arama",
            "tap to return",
            "ongoing video",
            "missed video"
        )
        val combined = "$title $text".lowercase()
        return systemPhrases.any { combined.contains(it) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Bildirim dinleyici bağlandı")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Bildirim dinleyici bağlantısı kesildi")
        requestRebind(ComponentName(this, WhatsAppNotificationService::class.java))
    }
}
