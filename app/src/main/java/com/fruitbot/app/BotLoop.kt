package com.fruitbot.app

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object BotLoop {

    private const val TAG = "BotLoop"
    private val PKGS = setOf(
        "com.halfbrick.fruitninja",
        "com.halfbrick.fruitninjafree",
        "com.halfbrick.fruitninja2",
        "com.halfbrick.fruitninja_google"
    )

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    @Volatile var running = false; private set

    fun start() {
        if (job?.isActive == true) return
        running = true
        job = scope.launch {
            while (isActive && running) {
                try { tick() } catch (e: CancellationException) { break }
                catch (e: Exception) { Log.w(TAG, e.message) }
                delay(100)
            }
        }
        Log.d(TAG, "started")
    }

    fun stop() {
        running = false; job?.cancel(); job = null
        Log.d(TAG, "stopped")
    }

    private fun tick() {
        if (GestureService.foregroundPkg !in PKGS) return
        val src = SliceService.frame ?: return
        val bmp = try { src.copy(src.config ?: Bitmap.Config.ARGB_8888, false) }
                  catch (e: Exception) { return }
        try {
            for (t in FruitDetector.detect(bmp)) slash(t.x, t.y)
        } finally { bmp.recycle() }
    }

    private fun slash(cx: Float, cy: Float) {
        val angles = floatArrayOf(45f, 135f, 60f, 120f, 30f, 150f)
        val deg = angles[Random.nextInt(angles.size)] + Random.nextFloat() * 20f - 10f
        val r = Math.toRadians(deg.toDouble()).toFloat()
        val h = 110f
        GestureService.swipe(cx - h * cos(r), cy - h * sin(r), cx + h * cos(r), cy + h * sin(r), 55)
    }
}
