package com.fruitbot.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class SliceService : Service() {

    companion object {
        const val KEY_CODE = "kc"
        const val KEY_DATA = "kd"
        private const val CH = "FBCh"
        private const val TAG = "SliceService"

        @Volatile var frame: Bitmap? = null
        @Volatile var ready = false
    }

    private var proj: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vdisp: android.hardware.display.VirtualDisplay? = null
    private val main = Handler(Looper.getMainLooper())

    // Overlay
    private var wm: WindowManager? = null
    private var overlayRoot: View? = null
    private var bubble: FrameLayout? = null
    private var icon: TextView? = null
    private var label: TextView? = null

    override fun onCreate() { super.onCreate(); makeChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNote())

        val code = intent?.getIntExtra(KEY_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(KEY_DATA)
        if (code == -1 || data == null) { stopSelf(); return START_NOT_STICKY }

        main.postDelayed({
            startCapture(code, data)
            showOverlay()
        }, 400)

        return START_STICKY
    }

    // ── Capture ────────────────────────────────────────────────────────────────

    private fun startCapture(code: Int, data: Intent) {
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(metrics)
            val W = metrics.widthPixels; val H = metrics.heightPixels; val D = metrics.densityDpi

            proj = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(code, data)

            reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 2).also { r ->
                r.setOnImageAvailableListener({ ir ->
                    val img = try { ir.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
                    try {
                        val pl = img.planes[0]
                        val ps = pl.pixelStride; val rs = pl.rowStride; val pad = rs - ps * W
                        val raw = Bitmap.createBitmap(W + pad / ps, H, Bitmap.Config.ARGB_8888)
                        raw.copyPixelsFromBuffer(pl.buffer)
                        val out = if (pad == 0) raw else { val c = Bitmap.createBitmap(raw,0,0,W,H); raw.recycle(); c }
                        frame?.recycle(); frame = out; ready = true
                    } catch (e: Exception) { Log.e(TAG, "frame: ${e.message}") }
                    finally { img.close() }
                }, main)
            }

            proj!!.createVirtualDisplay("FB", W, H, D,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, main)
            Log.d(TAG, "capture OK ${W}x${H}")
        } catch (e: Exception) { Log.e(TAG, "capture fail: ${e.message}"); stopSelf() }
    }

    // ── Overlay ────────────────────────────────────────────────────────────────

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayRoot = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        bubble = overlayRoot!!.findViewById(R.id.bubble)
        icon   = overlayRoot!!.findViewById(R.id.icon)
        label  = overlayRoot!!.findViewById(R.id.label)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 300 }

        var downX = 0f; var downY = 0f
        var wx = 0; var wy = 0; var dragged = false

        overlayRoot!!.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    wx = lp.x; wy = lp.y; dragged = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) dragged = true
                    if (dragged) {
                        lp.x = wx + dx; lp.y = wy + dy
                        wm?.updateViewLayout(overlayRoot, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!dragged) toggleBot(); true }
                else -> false
            }
        }

        wm!!.addView(overlayRoot, lp)
        Log.d(TAG, "overlay shown")
    }

    private fun toggleBot() {
        if (BotLoop.running) {
            BotLoop.stop()
            main.post {
                bubble?.setBackgroundResource(R.drawable.bubble_start)
                icon?.text = "▶"; label?.text = "START"
            }
        } else {
            BotLoop.start()
            main.post {
                bubble?.setBackgroundResource(R.drawable.bubble_stop)
                icon?.text = "⏹"; label?.text = "RUNNING"
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        BotLoop.stop()
        overlayRoot?.let { wm?.removeView(it) }
        ready = false
        vdisp?.release(); reader?.close()
        proj?.stop(); proj = null
        frame?.recycle(); frame = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNote() = Notification.Builder(this, CH)
        .setContentTitle("Fruit Bot")
        .setContentText("Tap the 🍉 bubble to start/stop")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .build()

    private fun makeChannel() {
        val ch = NotificationChannel(CH, "Fruit Bot", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
