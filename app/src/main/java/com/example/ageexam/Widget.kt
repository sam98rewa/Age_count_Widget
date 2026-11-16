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
        // New size => redraw the pages so the text fills the widget.
        WidgetRenderer.update(context, appWidgetId)
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

    fun updateAll(ctx: Context) {
        for (id in Ticker.allIds(ctx)) update(ctx, id)
    }

    fun update(ctx: Context, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val cfg = ConfigStore.load(ctx)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_root)

        // Background: colour via colour filter, transparency via alpha.
        rv.setInt(R.id.bg, "setColorFilter", 0xFF000000.toInt() or (cfg.bgColor and 0xFFFFFF))
        rv.setInt(R.id.bg, "setImageAlpha", cfg.bgAlpha())

        // Scrollable pages
        val svc = Intent(ctx, PageService::class.java)
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        rv.setRemoteAdapter(R.id.list, svc)

        // Tap the widget => open the AgeExam app
        val open = Intent(ctx, MainActivity::class.java)
        open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pi = PendingIntent.getActivity(
            ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        rv.setPendingIntentTemplate(R.id.list, pi)

        mgr.updateAppWidget(id, rv)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.list)
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
 * Each page is one bitmap exactly as big as the widget, drawn by Draw.drawPage()
 * (the same code as the app preview). Pages are laid out one under the other,
 * so they never overlap; swiping scrolls from the age page to the exam page.
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
        // Keep the bitmap within a safe memory size on very large widgets.
        val longest = maxOf(wDp, hDp) * density
        val scale = if (longest > 1400f) 1400f / longest else 1f
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

/** Keeps the widget numbers fresh. */
object Ticker {
    const val ACTION = "com.example.ageexam.TICK"

    fun allIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, AgeExamWidgetProvider::class.java))

    fun refreshAll(ctx: Context) {
        val ids = allIds(ctx)
        if (ids.isNotEmpty()) {
            AppWidgetManager.getInstance(ctx).notifyAppWidgetViewDataChanged(ids, R.id.list)
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
        Ticker.refreshAll(context)
        Ticker.schedule(context)
    }
}
