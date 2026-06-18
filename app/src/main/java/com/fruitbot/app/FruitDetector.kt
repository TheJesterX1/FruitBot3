package com.fruitbot.app

import android.graphics.Bitmap
import android.graphics.Color

object FruitDetector {

    private const val STEP = 10
    private const val MIN_HITS = 5
    private const val MERGE_SQ = 90f * 90f

    data class Target(val x: Float, val y: Float)

    fun detect(bmp: Bitmap): List<Target> {
        val W = bmp.width; val H = bmp.height
        val y0 = (H * 0.12).toInt(); val y1 = (H * 0.92).toInt()
        val hsv = FloatArray(3)
        val px = ArrayList<Float>(); val py = ArrayList<Float>()

        for (y in y0 until y1 step STEP)
            for (x in 0 until W step STEP) {
                val c = bmp.getPixel(x, y)
                Color.colorToHSV(c, hsv)
                if (isFruit(c, hsv)) { px.add(x.toFloat()); py.add(y.toFloat()) }
            }

        return cluster(px, py)
    }

    private fun isFruit(c: Int, hsv: FloatArray): Boolean {
        if (hsv[1] < 0.35f || hsv[2] < 0.28f || hsv[2] > 0.97f) return false
        val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        if (lum < 35 || lum > 235) return false
        val h = hsv[0]
        return h <= 22f || h >= 320f || h in 22f..165f || h in 260f..320f
    }

    private fun cluster(xs: ArrayList<Float>, ys: ArrayList<Float>): List<Target> {
        val n = xs.size; val done = BooleanArray(n); val out = mutableListOf<Target>()
        for (i in 0 until n) {
            if (done[i]) continue; done[i] = true
            val cx = mutableListOf(xs[i]); val cy = mutableListOf(ys[i])
            for (j in i + 1 until n) {
                if (done[j]) continue
                val dx = xs[i] - xs[j]; val dy = ys[i] - ys[j]
                if (dx * dx + dy * dy <= MERGE_SQ) { cx.add(xs[j]); cy.add(ys[j]); done[j] = true }
            }
            if (cx.size >= MIN_HITS)
                out.add(Target(cx.average().toFloat(), cy.average().toFloat()))
        }
        return out
    }
}
