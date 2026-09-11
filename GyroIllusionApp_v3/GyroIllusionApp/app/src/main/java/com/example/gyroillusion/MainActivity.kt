package com.example.gyroillusion

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Üstte Gösterim (Overlay) İzni Kontrolü
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
        }

        // 2. Sensör Yönetimi ve Güvenli Sensör Seçimi
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager?.let { manager ->
            // Öncelikle Jiroskop ara
            gyroSensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            
            // Jiroskop yoksa yedek olarak Dönme Sensörünü (Rotation Vector) kullan
            if (gyroSensor == null) {
                gyroSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            }
        }

        if (gyroSensor == null) {
            Toast.makeText(this, "Cihazda uyumlu sensör bulunamadı!", Toast.LENGTH_LONG).show()
        } else {
            // Sensörü doğrudan çalıştır ve arka plana geçse bile aktif tut
            sensorManager?.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Ekran boyutu ve katmanlar tam yüklendiğinde efekti yeniden doğrula
        if (hasFocus && gyroSensor != null) {
            sensorManager?.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            // Jiroskop veya Sensör verisi her değiştiğinde burası tetiklenir
            val x = it.values[0]
            val y = it.values[1]

            // Efekt/Çizim mantığı burada işlenir
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Sensör hassasiyet değişimleri
    }

    override fun onPause() {
        super.onPause()
        // DİKKAT: Ana ekrana geçildiğinde efektin durmaması için unregisterListener BURAYA EKLENMEDİ.
    }

    override fun onDestroy() {
        super.onDestroy()
        // Uygulama tamamen kapatıldığında sensörü serbest bırak
        sensorManager?.unregisterListener(this)
    }
}
