package com.example.notificationhistory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Cihaz yeniden başladığında NotificationListenerService'in
 * sistem tarafından otomatik olarak bağlanmasını sağlar.
 *
 * NOT: NotificationListenerService, sistem tarafından yönetildiğinden
 * doğrudan başlatılamaz. Bu receiver sadece servisi "hatırlatmak" içindir.
 * Gerçek bağlantı Android işletim sistemi tarafından yapılır.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Cihaz açıldı, bildirim servisi hazır")
            // NotificationListenerService sistem servisi olduğundan
            // Android otomatik olarak yeniden bağlar.
            // Eğer bağlanmazsa kullanıcı ayarlardan tekrar açıp kapatabilir.
        }
    }
}
