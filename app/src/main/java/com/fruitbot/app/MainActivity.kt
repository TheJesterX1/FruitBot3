package com.fruitbot.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var badge1: TextView
    private lateinit var badge2: TextView
    private lateinit var badge3: TextView
    private lateinit var btnStep1: Button
    private lateinit var btnStep2: Button
    private lateinit var btnStep3: Button
    private lateinit var btnLaunch: Button

    private var projMgr: MediaProjectionManager? = null
    private var captureCode = -1
    private var captureData: Intent? = null

    companion object { private const val REQ = 9001 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        badge1   = findViewById(R.id.badge1)
        badge2   = findViewById(R.id.badge2)
        badge3   = findViewById(R.id.badge3)
        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        btnStep3 = findViewById(R.id.btnStep3)
        btnLaunch = findViewById(R.id.btnLaunch)

        projMgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        btnStep1.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find 'Fruit Bot' and enable it", Toast.LENGTH_LONG).show()
        }

        btnStep2.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }

        btnStep3.setOnClickListener {
            if (!step1ok()) { Toast.makeText(this, "Do Step 1 first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (!step2ok()) { Toast.makeText(this, "Do Step 2 first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            @Suppress("DEPRECATION")
            startActivityForResult(projMgr!!.createScreenCaptureIntent(), REQ)
        }

        btnLaunch.setOnClickListener {
            // Start the foreground service with the saved projection token
            startForegroundService(Intent(this, SliceService::class.java).apply {
                putExtra(SliceService.KEY_CODE, captureCode)
                putExtra(SliceService.KEY_DATA, captureData)
            })
            Toast.makeText(this, "Bubble launched! Open Fruit Ninja and tap it.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true) // go to background so overlay is visible
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                captureCode = resultCode
                captureData = data
                refresh()
                Toast.makeText(this, "✅ All done! Press LAUNCH OVERLAY", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screen capture denied — try again", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refresh() {
        val s1 = step1ok(); val s2 = step2ok(); val s3 = captureData != null

        fun badge(tv: TextView, ok: Boolean) {
            tv.text = if (ok) "✓ DONE" else "PENDING"
            tv.setTextColor(if (ok) Color.parseColor("#00E676") else Color.parseColor("#FF9800"))
        }
        badge(badge1, s1); badge(badge2, s2); badge(badge3, s3)

        val ready = s1 && s2 && s3
        btnLaunch.isEnabled = ready
        btnLaunch.alpha = if (ready) 1f else 0.4f
    }

    private fun step1ok(): Boolean {
        val svc = "$packageName/${GestureService::class.java.canonicalName}"
        return (Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(svc)
    }

    private fun step2ok() = Settings.canDrawOverlays(this)
}
