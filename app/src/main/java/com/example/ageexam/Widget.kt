package com.example.ageexam

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.RemoteViews
import java.time.LocalDateTime

/**
 * The one widget. It is a single picture drawn by Draw (the same code as the app preview),
 * so there is no scrolling list or background service that a launcher could fail to load.
 * Resizable from 1x1 up; the picture is redrawn for the new size.
 */
class AgeExamWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            try {
                WidgetRenderer.update(context, id)
            } catch (e: Exception) {
                // keep going for the other widgets
            }
        }
        Ticker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // New size => redraw so the text fills the box again.
        try {
            WidgetRenderer.update(context, appWidgetId)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) Tap.clear(context, id)
    }

    override fun onEnabled(context: Context) {
        Ticker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        Ticker.schedule(context)
    }
}

object WidgetRenderer {

    /** Current widget size in dp (width, height), as reported by the launcher. */
    fun sizeDp(ctx: Context, id: Int): Pair<Float, Float> {
        val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        var w = o.getInt(
            if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0
        )
        var h = o.getInt(
            if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0
        )
        if (w <= 0) w = 70
        if (h <= 0) h = 70
        return Pair(w.toFloat(), h.toFloat())
    }

    /** Keeps the picture within a safe memory size on very large widgets. */
    fun bitmapScale(wDp: Float, hDp: Float, density: Float): Float {
        val longest = maxOf(wDp, hDp) * density
        return if (longest > 1400f) 1400f / longest else 1f
    }

    fun updateAll(ctx: Context) {
        for (id in Ticker.allIds(ctx)) {
            try {
                update(ctx, id)
            } catch (e: Exception) {
                // ignore one broken widget
            }
        }
    }

    /** Draws the current page of one widget (box + text) as one picture and shows it. */
    fun update(ctx: Context, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val cfg = ConfigStore.load(ctx)
        val pages = Calc.pages(cfg, LocalDateTime.now())
        val page = Tap.page(ctx, id).coerceIn(0, pages.size - 1)

        val size = sizeDp(ctx, id)
        val density = ctx.resources.displayMetrics.density
        val scale = bitmapScale(size.first, size.second, density)
        val bw = maxOf(1, Math.round(size.first * density * scale))
        val bh = maxOf(1, Math.round(size.second * density * scale))

        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        Draw.drawBox(canvas, bw.toFloat(), bh.toFloat(), density * scale, cfg, 0f, 0f)
        Draw.drawPage(canvas, pages[page].segs, size.first, size.second, density * scale, cfg, bw / 2f, bh / 2f)

        val rv = RemoteViews(ctx.packageName, R.layout.widget_root)
        rv.setImageViewBitmap(R.id.img, bmp)

        // Every tap goes through TapActivity: one tap switches page, a double tap opens settings.
        val tap = Intent(ctx, TapActivity::class.java)
        tap.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        tap.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        val pi = PendingIntent.getActivity(
            ctx, id, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.root, pi)

        mgr.updateAppWidget(id, rv)
    }
}

/** Remembers which page each widget shows and when it was last tapped. */
object Tap {
    private const val DOUBLE_MS = 500L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("timer_state", Context.MODE_PRIVATE)

    fun page(ctx: Context, id: Int): Int = prefs(ctx).getInt("page_$id", 0)

    fun clear(ctx: Context, id: Int) {
        prefs(ctx).edit().remove("page_$id").remove("tap_$id").apply()
    }

    /**
     * Handles one tap: switches to the other page and redraws the widget.
     * Returns true when this tap is the second of a double tap (the page is switched back
     * so a double tap leaves the widget as it was).
     */
    fun register(ctx: Context, id: Int): Boolean {
        val sp = prefs(ctx)
        val now = System.currentTimeMillis()
        val last = sp.getLong("tap_$id", 0L)
        val double = last != 0L && now - last <= DOUBLE_MS
        val next = (sp.getInt("page_$id", 0) + 1) % 2
        sp.edit().putInt("page_$id", next).putLong("tap_$id", if (double) 0L else now).apply()
        try {
            WidgetRenderer.update(ctx, id)
        } catch (e: Exception) {
            // ignore
        }
        return double
    }
}

/**
 * Invisible screen that receives widget taps. It stays for a moment so a quick second tap
 * on it counts as a double tap; a second tap on the widget itself is also detected.
 */
class TapActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        handleTap()
        handler.postDelayed({ finish() }, 350)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) handleTap()
        return true
    }

    private fun handleTap() {
        if (Tap.register(this, widgetId)) {
            handler.removeCallbacksAndMessages(null)
            val open = Intent(this, MainActivity::class.java)
            open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(open)
            finish()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

/** Keeps the widget numbers fresh. */
object Ticker {
    const val ACTION = "com.example.ageexam.TICK"

    fun allIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, AgeExamWidgetProvider::class.java))

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, TickReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Every minute if minutes are shown, every 5 min for hours, otherwise every 30 min. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx)
        if (allIds(ctx).isEmpty()) {
            am.cancel(pi)
            return
        }
        val c = ConfigStore.load(ctx)
        val units = c.ageUnits or c.examUnits
        val step = if (U.has(units, U.MIN)) 60_000L else if (U.has(units, U.H)) 300_000L else 1_800_000L
        val next = (System.currentTimeMillis() / step + 1) * step
        am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetRenderer.updateAll(context)
        Ticker.schedule(context)
    }
}
