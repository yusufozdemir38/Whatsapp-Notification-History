package com.example.notificationhistory

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var storage: NotificationStorage
    private lateinit var adapter: NotificationAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvPermissionBanner: View

    // Fotoğraf okuma izni launcher
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) {
            MediaWatcherService.start(this)
        }
        // İzin reddedilse bile uygulama çalışmaya devam eder,
        // sadece fotoğraf thumbnail özelliği devre dışı kalır
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WhatsAppNotificationService.ACTION_NOTIFICATION_RECEIVED) {
                refreshList()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = NotificationStorage(this)
        NotificationHelper.createChannels(this)

        setupViews()
        setupRecyclerView()

        // Fotoğraf iznini kontrol et ve servisi başlat
        requestMediaPermissionAndStartService()
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
        checkBatteryOptimization()
        refreshList()

        val filter = IntentFilter(WhatsAppNotificationService.ACTION_NOTIFICATION_RECEIVED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(notificationReceiver)
    }

    // ──────────────────────────────────────────
    // Fotoğraf İzni
    // ──────────────────────────────────────────

    private fun requestMediaPermissionAndStartService() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 9–12
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsNeeded.isEmpty()) {
            // İzin zaten var, servisi başlat
            MediaWatcherService.start(this)
        } else {
            // İzin iste
            mediaPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    // ──────────────────────────────────────────
    // UI Kurulum
    // ──────────────────────────────────────────

    private fun setupViews() {
        supportActionBar?.title = "WhatsApp Bildirim Geçmişi"

        tvEmpty = findViewById(R.id.tvEmpty)
        tvPermissionBanner = findViewById(R.id.bannerPermission)
        recyclerView = findViewById(R.id.recyclerView)

        tvPermissionBanner.setOnClickListener {
            openNotificationAccessSettings()
        }

        findViewById<FloatingActionButton>(R.id.fabClear).setOnClickListener {
            confirmClearAll()
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(
            onDeleteClick = { item ->
                AlertDialog.Builder(this)
                    .setTitle("Kaydı Sil")
                    .setMessage("Bu bildirimi geçmişten silmek istediğinize emin misiniz?")
                    .setPositiveButton("Sil") { _, _ ->
                        storage.deleteById(item.id)
                        refreshList()
                        Snackbar.make(recyclerView, "Kayıt silindi", Snackbar.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("İptal", null)
                    .show()
            },
            onImageClick = { imagePath ->
                try {
                    val file = File(imagePath)
                    val uri = FileProvider.getUriForFile(
                        this,
                        "$packageName.provider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Fotoğrafı görüntüle"))
                } catch (e: Exception) {
                    Snackbar.make(recyclerView, "Fotoğraf açılamadı", Snackbar.LENGTH_SHORT).show()
                }
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }
    }

    // ──────────────────────────────────────────
    // İzin Kontrolleri
    // ──────────────────────────────────────────

    private fun checkNotificationPermission() {
        val enabled = isNotificationListenerEnabled()
        tvPermissionBanner.visibility = if (enabled) View.GONE else View.VISIBLE
        if (!enabled) showPermissionDialog()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("📋 Bildirim Erişimi Gerekli")
            .setMessage(
                "WhatsApp bildirimlerini kaydedebilmek için " +
                "\"Bildirim Erişimi\" iznini vermeniz gerekiyor.\n\n" +
                "Açılan sayfada uygulamanızı bulup etkinleştirin."
            )
            .setPositiveButton("Ayarlara Git") { _, _ -> openNotificationAccessSettings() }
            .setNegativeButton("Sonra") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("battery_opt_asked", false)) {
                    prefs.edit().putBoolean("battery_opt_asked", true).apply()
                    showBatteryOptimizationDialog()
                }
            }
        }
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔋 Pil Optimizasyonu")
            .setMessage(
                "Arka planda sürekli çalışabilmek için pil optimizasyonunu " +
                "devre dışı bırakmanız önerilir.\n\n" +
                "Aksi hâlde Android, servisi uyku modunda durdurabilir."
            )
            .setPositiveButton("Ayarı Aç") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    ))
                }
            }
            .setNegativeButton("Atla", null)
            .show()
    }

    // ──────────────────────────────────────────
    // Liste Yönetimi
    // ──────────────────────────────────────────

    private fun refreshList() {
        val items = storage.getAll()
        adapter.submitList(items)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        supportActionBar?.subtitle = if (items.isNotEmpty()) "${items.size} kayıt" else null
    }

    private fun confirmClearAll() {
        if (storage.getAll().isEmpty()) {
            Snackbar.make(recyclerView, "Silinecek kayıt yok", Snackbar.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Tümünü Temizle")
            .setMessage("Tüm bildirim geçmişi silinecek. Emin misiniz?")
            .setPositiveButton("Tümünü Sil") { _, _ ->
                storage.clearAll()
                refreshList()
                Snackbar.make(recyclerView, "Tüm geçmiş temizlendi", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // ──────────────────────────────────────────
    // Menü
    // ──────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
