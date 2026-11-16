package com.example.ageexam

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
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDateTime

/** The one widget. Resizable from 1x1 up; the text re-fits itself every time. */
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
        // New size => redraw the box and pages so everything fills the widget.
        try {
            WidgetRenderer.update(context, appWidgetId)
        } catch (e: Exception) {
            // ignore
        }
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
        if (w <= 0) w = 110
        if (h <= 0) h = 110
        return Pair(w.toFloat(), h.toFloat())
    }

    /** Keeps bitmaps within a safe memory size on very large widgets. */
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

    fun update(ctx: Context, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val cfg = ConfigStore.load(ctx)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_root)
        val swipe = cfg.switchBy == 1

        // The coloured box is a bitmap, so its size, corners and transparency match the preview exactly.
        val size = sizeDp(ctx, id)
        val density = ctx.resources.displayMetrics.density
        val scale = bitmapScale(size.first, size.second, density)
        val bw = maxOf(1, Math.round(size.first * density * scale))
        val bh = maxOf(1, Math.round(size.second * density * scale))
        val bg = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        Draw.drawBox(Canvas(bg), bw.toFloat(), bh.toFloat(), density * scale, cfg, 0f, 0f)
        rv.setImageViewBitmap(R.id.bg, bg)

        // Tap mode: a page flipper (tap toggles age <-> exam). Swipe mode: a scrolling list.
        val active = if (swipe) R.id.list else R.id.flip
        val inactive = if (swipe) R.id.flip else R.id.list
        rv.setViewVisibility(active, View.VISIBLE)
        rv.setViewVisibility(inactive, View.GONE)

        val svc = Intent(ctx, PageService::class.java)
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        rv.setRemoteAdapter(active, svc)

        if (swipe) {
            // Tap opens the AgeExam app
            val open = Intent(ctx, MainActivity::class.java)
            open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pi = PendingIntent.getActivity(
                ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            rv.setPendingIntentTemplate(active, pi)
        } else {
            // Tap flips to the other page
            val tap = Intent(ctx, TickReceiver::class.java).setAction(Ticker.TOGGLE)
            tap.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            val pi = PendingIntent.getBroadcast(
                ctx, id, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            rv.setPendingIntentTemplate(active, pi)
        }

        mgr.updateAppWidget(id, rv)
        mgr.notifyAppWidgetViewDataChanged(id, active)
    }
}

class PageService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return PageFactory(applicationContext, id)
    }
}

/**
 * Each page is one transparent bitmap exactly as big as the widget, with only the text drawn
 * by Draw.drawPage() (the same code as the app preview). The box is a separate bitmap behind it.
 */
class PageFactory(private val ctx: Context, private val id: Int) :
    RemoteViewsService.RemoteViewsFactory {

    private var cfg: AppConfig = ConfigStore.defaults()
    private var pages: List<Page> = emptyList()
    private var wDp = 110f
    private var hDp = 110f

    private fun load() {
        cfg = ConfigStore.load(ctx)
        pages = Calc.pages(cfg, LocalDateTime.now())
        val size = WidgetRenderer.sizeDp(ctx, id)
        wDp = size.first
        hDp = size.second
    }

    override fun onCreate() {
        load()
    }

    override fun onDataSetChanged() {
        load()
    }

    override fun onDestroy() {}

    override fun getCount(): Int = pages.size

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
        if (position !in pages.indices) return rv

        val density = ctx.resources.displayMetrics.density
        val scale = WidgetRenderer.bitmapScale(wDp, hDp, density)
        val bw = maxOf(1, Math.round(wDp * density * scale))
        val bh = maxOf(1, Math.round(hDp * density * scale))

        try {
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            Draw.drawPage(canvas, pages[position].segs, wDp, hDp, density * scale, cfg, bw / 2f, bh / 2f)
            rv.setImageViewBitmap(R.id.page, bmp)
        } catch (e: Exception) {
            // Never let one bad draw break the whole widget.
        }
        rv.setOnClickFillInIntent(R.id.page, Intent())
        return rv
    }
}

/** Keeps the widget numbers fresh and handles the tap-to-toggle. */
object Ticker {
    const val ACTION = "com.example.ageexam.TICK"
    const val TOGGLE = "com.example.ageexam.TOGGLE"

    fun allIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, AgeExamWidgetProvider::class.java))

    fun refreshAll(ctx: Context) {
        val ids = allIds(ctx)
        if (ids.isNotEmpty()) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.flip)
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.list)
        }
    }

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
        if (intent.action == Ticker.TOGGLE) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Flip to the other page: instant, never half-way, works both directions.
                val rv = RemoteViews(context.packageName, R.layout.widget_root)
                rv.showNext(R.id.flip)
                AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(id, rv)
            }
            return
        }
        Ticker.refreshAll(context)
        Ticker.schedule(context)
    }
}
