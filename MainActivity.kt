package com.aky.fm27odds

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class MainActivity : Activity() {
    private val requestCapture = 7001
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }
        val title = TextView(this).apply {
            text = "FM27 Odds Tracker"
            textSize = 25f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Allow overlay access, then start screen monitoring."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        val overlay = Button(this).apply {
            text = "1. Allow floating overlay"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else status.text = "Overlay permission is enabled."
            }
        }
        val start = Button(this).apply {
            text = "2. Start monitoring"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    status.text = "Enable floating overlay permission first."
                    return@setOnClickListener
                }
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), requestCapture)
            }
        }
        val stop = Button(this).apply {
            text = "Stop monitoring"
            setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)); status.text = "Stopped." }
        }
        layout.addView(title); layout.addView(status); layout.addView(overlay); layout.addView(start); layout.addView(stop)
        setContentView(layout)
    }

    @Deprecated("Activity result callback kept compatible with older Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCapture && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            status.text = "Monitoring is active. Switch to FM27."
        }
    }
}
