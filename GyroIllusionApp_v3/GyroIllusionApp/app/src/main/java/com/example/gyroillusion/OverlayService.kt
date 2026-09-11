package com.example.gyroillusion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class OverlayService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "GyroOverlayChannel"
        private const val DEADZONE_ANGLE = 3.5f
    }

    private lateinit var windowManager: WindowManager
    private lateinit var sensorManager: SensorManager
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    private var overlayView: View? = null
    private var imageView: ImageView? = null

    private var isEffectActive = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = createNotification()
                startForeground(1001, notification)

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

                if (resultCode != -1 && resultData != null) {
                    val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mpManager.getMediaProjection(resultCode, resultData)
                    setupImageReader()
                    startGyroscope()
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )
        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayMetrics.DENSITY_DEFAULT,
            imageReader?.surface, null, null
        )
    }

    private fun startGyroscope() {
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

        if (abs(roll) > DEADZONE_ANGLE || abs(pitch) > DEADZONE_ANGLE) {
            if (!isEffectActive) {
                isEffectActive = true
                captureScreenAndShowOverlay()
            }
            updateOverlayMatrix(-roll, -pitch)
        } else {
            if (isEffectActive) {
                isEffectActive = false
                hideOverlay()
            }
        }
    }

    private fun captureScreenAndShowOverlay() {
        val image = imageReader?.acquireLatestImage() ?: return
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)

        mainHandler.post {
            showOverlayView(croppedBitmap)
        }
    }

    private fun showOverlayView(bitmap: Bitmap) {
        if (overlayView == null) {
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.overlay_layout, null)
            imageView = overlayView?.findViewById(R.id.overlayImageView)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            layoutParams.gravity = Gravity.TOP or Gravity.START

            windowManager.addView(overlayView, layoutParams)
        }

        imageView?.setImageBitmap(bitmap)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
            imageView?.setRenderEffect(blurEffect)
        }

        overlayView?.visibility = View.VISIBLE
    }

    private fun updateOverlayMatrix(roll: Float, pitch: Float) {
        mainHandler.post {
            val view = imageView ?: return@post
            val camera = Camera()
            val matrix = Matrix()

            camera.save()
            camera.rotateY(roll * 1.3f)
            camera.rotateX(pitch * 1.3f)
            camera.getMatrix(matrix)
            camera.restore()

            val centerX = screenWidth / 2f
            val centerY = screenHeight / 2f

            matrix.preTranslate(-centerX, -centerY)
            matrix.postTranslate(centerX, centerY)

            view.animationMatrix = matrix
            view.invalidate()
        }
    }

    private fun hideOverlay() {
        mainHandler.post {
            overlayView?.visibility = View.GONE
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Parallax Effect Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("3D Paralaks Efekti Aktif")
            .setContentText("Telefonu eğdiğinizde optik illüzyon devreye girecek.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        mediaProjection?.stop()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
        }
    }
}