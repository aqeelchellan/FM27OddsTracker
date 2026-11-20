package com.aky.fm27odds

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class CaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL = "fm27_tracker"
        private const val NOTIFICATION_ID = 27
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null
    private val busy = AtomicBoolean(false)
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val handler = Handler(Looper.getMainLooper())
    private var lastScan = 0L
    private val scanLoop = object : Runnable {
        override fun run() {
            captureAndRead()
            handler.postDelayed(this, 1100)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("FM27 Odds Tracker")
            .setContentText("Reading the game screen while enabled")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build())
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                   else intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        startCapture()
        showOverlay()
        handler.post(scanLoop)
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay("FM27Odds", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            text = "FM27 ODDS\nWaiting for odds…"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xDD172033.toInt())
            setPadding(16, 12, 16, 12)
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                   else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18; y = 120
        }
        view.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; return true }
                    android.view.MotionEvent.ACTION_MOVE -> { params.x = startX - (event.rawX-downX).roundToInt(); params.y = startY + (event.rawY-downY).roundToInt(); windowManager?.updateViewLayout(view, params); return true }
                }; return false
            }
        })
        overlay = view
        windowManager?.addView(view, params)
    }

    private fun captureAndRead() {
        if (!busy.compareAndSet(false, true)) return
        val image = reader?.acquireLatestImage()
        if (image == null) { busy.set(false); return }
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()
            image.close()
            val input = InputImage.fromBitmap(cropped, 0)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    val text = result.text
                    val odds = OddsParser.find(text)
                    handler.post {
                        overlay?.text = if (odds != null) {
                            "FM27 ODDS\nH  ${odds.home}   D  ${odds.draw}   A  ${odds.away}\n" +
                            "H ${odds.homePct}%  D ${odds.drawPct}%  A ${odds.awayPct}%\n${odds.favourite}"
                        } else "FM27 ODDS\nWaiting for odds…"
                    }
                }
                .addOnFailureListener { }
                .addOnCompleteListener { cropped.recycle(); busy.set(false) }
        } catch (_: Exception) {
            try { image.close() } catch (_: Exception) {}
            busy.set(false)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "FM27 monitoring", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanLoop)
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        display?.release(); reader?.close(); projection?.stop(); recognizer.close()
        super.onDestroy()
    }
}
