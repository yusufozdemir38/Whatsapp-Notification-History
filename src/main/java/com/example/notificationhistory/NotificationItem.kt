package com.example.notificationhistory

/**
 * Bildirim veri modeli.
 *
 * @param id          Benzersiz tanımlayıcı (timestamp tabanlı)
 * @param senderName  Gönderenin adı
 * @param message     Mesaj içeriği
 * @param timestamp   Alınma zamanı (epoch ms)
 * @param isGroup     Grup mesajı mı?
 * @param isDeleted   Mesaj silindi mi?
 * @param isMedia     Medya mesajı mı? (fotoğraf/video/ses)
 * @param imagePath   Yerel kaydedilmiş fotoğraf yolu (varsa)
 */
data class NotificationItem(
    val id: Long,
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val isGroup: Boolean = false,
    val isDeleted: Boolean = false,
    val isMedia: Boolean = false,
    val imagePath: String? = null   // MediaWatcherService tarafından doldurulur
) {
    fun getFormattedTime(): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayCal = java.util.Calendar.getInstance()
        val diff = System.currentTimeMillis() - timestamp

        return when {
            cal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR) &&
            cal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) ->
                String.format("%02d:%02d",
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE))

            diff < 48 * 60 * 60 * 1000L ->
                "Dün ${String.format("%02d:%02d",
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE))}"

            else -> {
                val months = arrayOf("Oca","Şub","Mar","Nis","May","Haz","Tem","Ağu","Eyl","Eki","Kas","Ara")
                "${cal.get(java.util.Calendar.DAY_OF_MONTH)} " +
                "${months[cal.get(java.util.Calendar.MONTH)]} " +
                String.format("%02d:%02d",
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE))
            }
        }
    }
}
