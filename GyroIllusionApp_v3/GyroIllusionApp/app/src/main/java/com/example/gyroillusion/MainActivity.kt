package com.example.gyroillusion

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
                putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Paralaks İllüzyon Servisi Başlatıldı!", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Ekran yakalama izni verilmedi.", Toast.LENGTH_SHORT).show()
        }
    }

override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val btnStart = findViewById<Button>(R.id.btnStartService)
        val btnStop = findViewById<Button>(R.id.btnStopService)

        btnStart.setOnClickListener {
            checkPermissionsAndStart()
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            startService(intent)
            Toast.makeText(this, "Servis Durduruldu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Lütfen 'Diğer uygulamaların üzerinde gösterilme' iznini verin.", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(captureIntent)
        }
    }
}