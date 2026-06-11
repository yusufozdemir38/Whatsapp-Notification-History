package com.example.notificationhistory

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Sistem bildirimi gönderen yardımcı sınıf.
 *
 * Kullanım senaryoları:
 * 1. Silinen mesaj tespit edildiğinde → "X bir mesajı sildi"
 * 2. Fotoğraf kurtarıldığında → ayrıca MediaWatcherService'te de bildirim var
 */
object NotificationHelper {

    private const val CHANNEL_ID_DELETED = "deleted_messages"
    private const val CHANNEL_NAME_DELETED = "Silinen Mesajlar"

    /**
     * Uygulama başlarken kanalları oluştur.
     * MainActivity.onCreate()'de çağrılmalı.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Silinen mesaj bildirimi kanalı
            val deletedChannel = NotificationChannel(
                CHANNEL_ID_DELETED,
                CHANNEL_NAME_DELETED,
                NotificationManager.IMPORTANCE_HIGH  // Ses + titreşim
            ).apply {
                description = "Birisi WhatsApp mesajını sildiğinde bildirim alırsınız"
                enableVibration(true)
            }
            manager.createNotificationChannel(deletedChannel)
        }
    }

    /**
     * Silinen mesaj bildirimi gönderir.
     *
     * @param context   Context
     * @param sender    Mesajı silen kişinin adı
     * @param message   Silinen mesajın içeriği
     */
    fun sendDeletedMessageNotification(context: Context, sender: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Uygulamayı açan intent
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DELETED)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🗑 $sender bir mesajı sildi!")
            .setContentText(message.take(100))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Silinen mesaj: $message")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .setColor(0xFFE53935.toInt())  // Kırmızı
            .build()

        // Her silinen mesaj için farklı ID (üst üste binmesin)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
