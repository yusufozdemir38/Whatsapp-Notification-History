package com.example.notificationhistory

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * WhatsApp fotoğraflarını MediaStore üzerinden izler.
 *
 * FileObserver Android 10+ Scoped Storage kısıtlaması yüzünden
 * WhatsApp klasörü için olay almıyor. MediaStore ContentObserver
 * tüm Android sürümlerinde güvenilir çalışır, ek izin gerektirmez.
 */
class MediaWatcherService : Service() {

    companion object {
        private const val TAG        = "MediaWatcher"
        private const val CHANNEL_ID = "media_watcher_channel"
        private const val NOTIF_ID   = 1001

        fun start(context: Context) {
            val intent = Intent(context, MediaWatcherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaWatcherService::class.java))
        }
    }

    private lateinit var saveDir: File
    private lateinit var storage: NotificationStorage
    private lateinit var contentObserver: ContentObserver

    // Son kontrol zamanı — sadece bu tarihten sonraki dosyalara bak
    private var lastCheckedMs = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        storage = NotificationStorage(applicationContext)
        createNotificationChannel()

        saveDir = File(getExternalFilesDir(null), "KurtarilanFotograflar").also {
            if (!it.exists()) it.mkdirs()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildForegroundNotification())
        }

        registerMediaObserver()
    }

    private fun registerMediaObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Arka planda çalıştır — UI thread'i bloklamaz
                Thread { checkForNewWhatsAppImages() }.start()
            }
        }

        // Tüm harici görseller için dinle
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
        Log.d(TAG, "MediaStore observer kayıt edildi")
    }

    /**
     * MediaStore'u sorgular, son kontrolden bu yana eklenen
     * WhatsApp fotoğraflarını bulur ve kopyalar.
     */
    private fun checkForNewWhatsAppImages() {
        val queryTime = lastCheckedMs
        lastCheckedMs = System.currentTimeMillis()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,          // dosya yolu
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        // Son kontrolden bu yana eklenen WhatsApp görsellerini sorgula
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND " +
                        "(${MediaStore.Images.Media.DATA} LIKE ? OR " +
                        " ${MediaStore.Images.Media.DATA} LIKE ?)"

        val selectionArgs = arrayOf(
            (queryTime / 1000).toString(),   // DATE_ADDED saniye cinsinden
            "%WhatsApp%Images%",
            "%whatsapp%images%"
        )

        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        ) ?: return

        cursor.use {
            while (it.moveToNext()) {
                val filePath = it.getString(
                    it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                ) ?: continue

                val name = it.getString(
                    it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                ) ?: continue

                Log.d(TAG, "Yeni WhatsApp görseli tespit edildi: $filePath")
                copyAndAttach(File(filePath), name)
            }
        }
    }

    private fun copyAndAttach(file: File, name: String) {
        if (!file.exists() || file.length() == 0L) return

        val destFile = File(saveDir, name)
        if (destFile.exists()) return   // Zaten kopyalanmış

        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Kopyalandı: ${destFile.absolutePath}")

            // En son medya bildiriminin imagePath'ini güncelle
            val attached = storage.attachImageToLatestMedia(destFile.absolutePath)
            Log.d(TAG, "Storage güncellendi: $attached")

            // MainActivity listeyi yenilesin
            sendBroadcast(Intent(WhatsAppNotificationService.ACTION_NOTIFICATION_RECEIVED))

        } catch (e: Exception) {
            Log.e(TAG, "Kopyalama hatası: ${e.message}")
        }
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("WA Bildirim Geçmişi")
            .setContentText("WhatsApp medyası izleniyor...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Medya İzleyici", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WhatsApp fotoğraf yedekleme servisi"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
        Log.d(TAG, "Medya izleyici durduruldu")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
