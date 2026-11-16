package com.example.ageexam

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Single source of truth for how a page looks.
 * The in-app preview and the real home-screen widget both call drawPage(),
 * so they are pixel-for-pixel the same at any size.
 */
object Draw {
    /** Height of a digit relative to the font size (sans-serif digits are ~0.72). */
    private const val CAP = 0.74f

    /** Space between stacked lines, relative to the font size. */
    private const val GAP = 0.26f

    class Plan(val stacked: Boolean, val sizePx: Float)

    fun typeface(weight: Int): Typeface = when (weight) {
        0 -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
        2 -> Typeface.create("sans-serif", Typeface.BOLD)
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    /** Digits all measure like "0" so the size does not jump when the numbers change. */
    private fun stable(s: String): String {
        val sb = StringBuilder()
        for (ch in s) sb.append(if (ch.isDigit()) '0' else ch)
        return sb.toString()
    }

    fun plan(segs: List<String>, wDp: Float, hDp: Float, density: Float, weight: Int): Plan {
        val pad = maxOf(minOf(wDp, hDp) * 0.10f, 6f) * density
        val availW = maxOf(wDp * density - 2f * pad, 1f)
        val availH = maxOf(hDp * density - 2f * pad, 1f)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = typeface(weight)
        p.textSize = 100f

        val lineW = maxOf(p.measureText(stable(segs.joinToString("."))), 1f)
        val single = minOf(availW * 100f / lineW, availH / CAP)
        if (segs.size < 2) return Plan(false, single)

        var widest = 1f
        for (s in segs) widest = maxOf(widest, p.measureText(stable(s)))
        val n = segs.size
        val block = CAP * n + GAP * (n - 1)
        val stackedSize = minOf(availW * 100f / widest, availH / block)

        // Stack the numbers whenever that lets the text be clearly bigger.
        return if (stackedSize > single * 1.15f) Plan(true, stackedSize) else Plan(false, single)
    }

    /** Draws one page centred on (cx, cy). Sizes are in dp, converted with density. */
    fun drawPage(
        canvas: Canvas, segs: List<String>, wDp: Float, hDp: Float,
        density: Float, cfg: AppConfig, cx: Float, cy: Float
    ) {
        val plan = plan(segs, wDp, hDp, density, cfg.weight)
        val s = plan.sizePx
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = typeface(cfg.weight)
        p.color = 0xFF000000.toInt() or (cfg.textColor and 0xFFFFFF)
        p.textAlign = Paint.Align.CENTER
        p.textSize = s

        if (plan.stacked) {
            val n = segs.size
            val block = s * (CAP * n + GAP * (n - 1))
            var base = cy - block / 2f + s * CAP
            for (seg in segs) {
                canvas.drawText(seg, cx, base, p)
                base += s * (CAP + GAP)
            }
        } else {
            canvas.drawText(segs.joinToString("."), cx, cy + s * CAP / 2f, p)
        }
    }
}
