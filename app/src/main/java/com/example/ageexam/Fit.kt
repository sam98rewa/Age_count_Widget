package com.example.ageexam

import android.graphics.Paint
import android.graphics.Typeface

/**
 * Decides how a page is drawn for a given widget size, so that the text always
 * fills the widget nicely: wide widgets show one line ("28.10.26"),
 * square or tall widgets stack the numbers (28 / 10 / 26) and make them bigger.
 * The app preview uses exactly the same function, so it matches the real widget.
 */
object Fit {
    const val LINE = 1.17f

    class Result(val stacked: Boolean, val sizePx: Float)

    fun typeface(weight: Int): Typeface = when (weight) {
        0 -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
        2 -> Typeface.create("sans-serif", Typeface.BOLD)
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    fun layout(segs: List<String>, wDp: Float, hDp: Float, density: Float, weight: Int): Result {
        val pad = maxOf(minOf(wDp, hDp) * 0.09f, 4f) * density
        val availW = maxOf(wDp * density - 2f * pad, 1f)
        val availH = maxOf(hDp * density - 2f * pad, 1f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = typeface(weight)
        paint.textSize = 100f

        val joinedW = maxOf(paint.measureText(segs.joinToString(".")), 1f)
        val single = minOf(availW * 100f / joinedW, availH / LINE)
        if (segs.size < 2) return Result(false, single * 0.97f)

        var widest = 1f
        for (s in segs) widest = maxOf(widest, paint.measureText(s))
        val stackedSize = minOf(availW * 100f / widest, availH / segs.size / LINE)

        return if (stackedSize > single * 1.1f) {
            Result(true, stackedSize * 0.97f)
        } else {
            Result(false, single * 0.97f)
        }
    }
}
