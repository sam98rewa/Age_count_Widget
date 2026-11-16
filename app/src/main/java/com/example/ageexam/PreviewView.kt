package com.example.ageexam

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.time.LocalDateTime

/**
 * Live preview of the widget. Uses the same layout maths as the real widget.
 * Swipe up/down (or tap) to flip between the age page and the exam page.
 */
class PreviewView(ctx: Context) : View(ctx) {

    private var cfg: AppConfig = ConfigStore.defaults()
    private var wDp = 152f
    private var hDp = 172f
    private var pos = 0f
    private var page = 0
    private var downY = 0f
    private var animator: ValueAnimator? = null

    private val d = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clock = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000)
        }
    }

    fun setConfig(c: AppConfig) {
        cfg = c
        invalidate()
    }

    fun setSizeDp(w: Float, h: Float) {
        wDp = w
        hDp = h
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(clock)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(clock)
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> downY = e.y
            MotionEvent.ACTION_UP -> {
                val dy = e.y - downY
                val target = if (Math.abs(dy) > 24 * d) {
                    if (dy < 0) 1 else 0
                } else {
                    1 - page
                }
                goTo(target)
            }
        }
        return true
    }

    private fun goTo(p: Int) {
        page = p
        animator?.cancel()
        val a = ValueAnimator.ofFloat(pos, p.toFloat())
        a.duration = 240
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            pos = it.animatedValue as Float
            invalidate()
        }
        a.start()
        animator = a
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Fake wallpaper so transparency is visible
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, w, h, 0xFF4568DC.toInt(), 0xFFB06AB3.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(0f, 0f, w, h, 24 * d, 24 * d, paint)
        paint.shader = null

        val cardW = wDp * d
        val cardH = hDp * d
        val margin = 22 * d
        val s = minOf(1f, (w - 2 * margin) / cardW, (h - 2 * margin) / cardH)

        canvas.save()
        canvas.translate(w / 2f, h / 2f)
        canvas.scale(s, s)

        val r = RectF(-cardW / 2f, -cardH / 2f, cardW / 2f, cardH / 2f)
        val radius = 24 * d

        paint.style = Paint.Style.FILL
        paint.color = (cfg.bgAlpha() shl 24) or (cfg.bgColor and 0xFFFFFF)
        canvas.drawRoundRect(r, radius, radius, paint)

        if (cfg.bgAlpha() < 40) {
            // faint outline so the transparent widget area is still visible
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * d / s
            paint.color = 0x66FFFFFF
            canvas.drawRoundRect(r, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }

        val clip = Path()
        clip.addRoundRect(r, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)

        val pages = Calc.pages(cfg, LocalDateTime.now())
        for (i in pages.indices) {
            val dy = (i - pos) * cardH
            if (Math.abs(dy) >= cardH) continue
            Draw.drawPage(canvas, pages[i].segs, wDp, hDp, d, cfg, 0f, dy)
        }
        canvas.restore()

        // page dots (preview only, not part of the real widget)
        for (i in 0..1) {
            paint.style = Paint.Style.FILL
            paint.color = if (Math.round(pos) == i) 0xFFFFFFFF.toInt() else 0x66FFFFFF
            canvas.drawCircle(w - 12 * d, h / 2f + (i - 0.5f) * 14 * d, 3 * d, paint)
        }
    }
}
