# 📱 WhatsApp Bildirim Geçmişi

WhatsApp'ta silinen mesajları ve fotoğrafları kurtaran Android uygulaması.

---

## ✨ Özellikler

- 🗑️ **Silinen mesajları tespit eder** — "SİLİNDİ" etiketi ve kırmızı arka planla işaretler
- 📷 **Fotoğrafları otomatik kaydeder** — gönderilen fotoğraf silinse bile uygulamada kalır
- 👥 **Grup ve bireysel mesajları ayırt eder**
- 🔄 **Cihaz yeniden başlatıldığında otomatik devam eder**
- ⚙️ **Kayıt sınırı ayarlanabilir** — 10 ile 1000 arasında (varsayılan 100)
- 🔒 **Tamamen yerel** — veriler yalnızca cihazda saklanır, sunucuya gönderilmez

---

## 📋 Gereksinimler

- Android 8.0 (API 26) ve üzeri
- Bildirim erişim izni
- Fotoğraf kaydetme için medya okuma izni (Android 13+ için READ_MEDIA_IMAGES)

---

## 📥 Kurulum

1. [Releases](../../releases) sayfasından son APK dosyasını indirin
2. Telefonunuzda **Ayarlar → Güvenlik → Bilinmeyen kaynaklar** iznini açın
3. APK dosyasına dokunarak kurulumu tamamlayın
4. Uygulama açılınca **Bildirim Erişimi** iznini verin

Alternatif Sorunlar
Uygulama kurulurken Play Store dışından yüklenen her imzasız/bilinmeyen APK için Google Play Protect böyle uyarı veriyor. Zararlı bir şey yok.

Anladım'a basın.
Tekrar kurulum ekranına gelince "Yine de yükle" veya "Devam et" seçeneği çıkacak, ona basın.

Eğer o seçenek çıkmazsa:
Ayarlar → Uygulamalar → Play Protect → uyarıyı görmezden gel seçeneği olacak.
Bu uyarı sadece ilk kurulumda çıkar, bir daha çıkmaz.

---

## 🚀 Nasıl Çalışır?

Uygulama arka planda çalışan bir `NotificationListenerService` ile WhatsApp bildirimlerini dinler. Gelen her mesaj ve fotoğraf yerel depolamaya kaydedilir. WhatsApp mesajı sildiğinde uygulama bunu tespit eder ve kaydı "silindi" olarak işaretler — orijinal içerik korunur.

Fotoğraflar için ayrı bir `MediaWatcherService` çalışır; `MediaStore` üzerinden yeni WhatsApp görsellerini izler ve otomatik olarak yedekler.

---

## 🛠️ Derleme

```bash
git clone https://github.com/yusufozdemir38/whatsapp-bildirim-gecmisi.git
cd whatsapp-bildirim-gecmisi
./gradlew assembleRelease
```

Android Studio ile açıp direkt çalıştırabilirsiniz.

**Gerekli:**
- Android Studio Hedgehog veya üzeri
- JDK 17
- Android SDK 34

---

## ⚠️ Yasal Uyarı

Bu uygulama yalnızca kişisel kullanım amacıyla geliştirilmiştir. WhatsApp'ın kullanım koşullarına uygun biçimde kullanmak kullanıcının sorumluluğundadır. Uygulama Meta veya WhatsApp ile herhangi bir bağlantısı bulunmamaktadır.

---

## 📄 Lisans

MIT License — dilediğiniz gibi kullanabilir, değiştirebilir ve dağıtabilirsiniz.
