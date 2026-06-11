package com.example.notificationhistory

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

/**
 * Ayarlar ekranı.
 *
 * Kullanıcı burada:
 * 1. maxHistory değerini değiştirebilir (SeekBar + EditText)
 * 2. Mevcut kayıt sayısını görebilir
 * 3. Bildirim erişim iznini yeniden verebilir
 * 4. Tüm geçmişi temizleyebilir
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var storage: NotificationStorage
    private lateinit var seekBar: SeekBar
    private lateinit var etMaxHistory: EditText
    private lateinit var tvCurrentCount: TextView
    private lateinit var tvSeekLabel: TextView
    private lateinit var btnSave: Button
    private lateinit var btnClearAll: Button
    private lateinit var btnPermission: Button

    // Hızlı seçim değerleri
    private val presetValues = listOf(50, 100, 200, 500, 1000)

    // SeekBar değeri: 0..100 → 10..1000 arası lineer olmayan ölçek
    private var currentMaxHistory = NotificationStorage.DEFAULT_MAX_HISTORY
    private var isUpdatingFromCode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.apply {
            title = "Ayarlar"
            setDisplayHomeAsUpEnabled(true)
        }

        storage = NotificationStorage(this)
        currentMaxHistory = storage.getMaxHistory()

        setupViews()
        loadCurrentValues()
    }

    private fun setupViews() {
        seekBar = findViewById(R.id.seekBarMaxHistory)
        etMaxHistory = findViewById(R.id.etMaxHistory)
        tvCurrentCount = findViewById(R.id.tvCurrentCount)
        tvSeekLabel = findViewById(R.id.tvSeekLabel)
        btnSave = findViewById(R.id.btnSave)
        btnClearAll = findViewById(R.id.btnClearAll)
        btnPermission = findViewById(R.id.btnPermission)

        // SeekBar: 10 - 1000 arası, adım 10
        seekBar.max = 99 // 0..99 → (value+1)*10 = 10..1000

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val value = (progress + 1) * 10
                    currentMaxHistory = value
                    isUpdatingFromCode = true
                    etMaxHistory.setText(value.toString())
                    isUpdatingFromCode = false
                    updateSeekLabel(value)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // EditText: kullanıcı elle değer girebilir
        etMaxHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingFromCode) return
                val value = s.toString().toIntOrNull() ?: return
                val clamped = value.coerceIn(10, 1000)
                currentMaxHistory = clamped
                val seekProgress = (clamped / 10) - 1
                seekBar.progress = seekProgress.coerceIn(0, 99)
                updateSeekLabel(clamped)
            }
        })

        // Hızlı seçim butonları
        setupPresetButtons()

        // Kaydet
        btnSave.setOnClickListener {
            val valueText = etMaxHistory.text.toString().toIntOrNull()
            if (valueText == null || valueText < 10 || valueText > 1000) {
                etMaxHistory.error = "10 ile 1000 arasında bir değer girin"
                return@setOnClickListener
            }
            storage.setMaxHistory(valueText)
            Snackbar.make(btnSave, "Ayar kaydedildi! Maksimum: $valueText mesaj", Snackbar.LENGTH_LONG).show()
            loadCurrentValues() // Güncel kayıt sayısını yenile
        }

        // Tümünü temizle
        btnClearAll.setOnClickListener {
            val count = storage.getAll().size
            if (count == 0) {
                Snackbar.make(btnClearAll, "Silinecek kayıt yok", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Tümünü Sil")
                .setMessage("$count adet kayıt silinecek. Emin misiniz?")
                .setPositiveButton("Sil") { _, _ ->
                    storage.clearAll()
                    loadCurrentValues()
                    Snackbar.make(btnClearAll, "Tüm geçmiş temizlendi", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        // İzin butonu
        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun setupPresetButtons() {
        val presetIds = listOf(
            R.id.btnPreset50,
            R.id.btnPreset100,
            R.id.btnPreset200,
            R.id.btnPreset500
        )
        val presetVals = listOf(50, 100, 200, 500)

        presetIds.forEachIndexed { index, id ->
            val value = presetVals[index]
            findViewById<Button>(id).setOnClickListener {
                currentMaxHistory = value
                isUpdatingFromCode = true
                etMaxHistory.setText(value.toString())
                isUpdatingFromCode = false
                seekBar.progress = (value / 10) - 1
                updateSeekLabel(value)
            }
        }
    }

    private fun loadCurrentValues() {
        val maxHistory = storage.getMaxHistory()
        val currentCount = storage.getAll().size

        currentMaxHistory = maxHistory
        isUpdatingFromCode = true
        etMaxHistory.setText(maxHistory.toString())
        isUpdatingFromCode = false
        seekBar.progress = ((maxHistory / 10) - 1).coerceIn(0, 99)
        updateSeekLabel(maxHistory)

        tvCurrentCount.text = "Şu an kayıtlı: $currentCount / $maxHistory mesaj"

        // İzin durumu
        val isEnabled = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )?.contains(packageName) == true

        btnPermission.text = if (isEnabled) {
            "✅ Bildirim Erişimi Aktif"
        } else {
            "⚠️ Bildirim Erişimi Ver"
        }
    }

    private fun updateSeekLabel(value: Int) {
        tvSeekLabel.text = when {
            value <= 50  -> "Düşük (${value})"
            value <= 200 -> "Orta (${value})"
            value <= 500 -> "Yüksek (${value})"
            else         -> "Çok Yüksek (${value})"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadCurrentValues() // İzin durumunu güncelle
    }
}
