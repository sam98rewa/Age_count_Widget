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
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDateTime

/** The one widget. Resizable from 1x1 up; text re-fits automatically. */
class AgeExamWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) WidgetRenderer.update(context, id)
        Ticker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
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

    /** Current widget size in dp (width, height), from the launcher. */
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

class PageFactory(private val ctx: Context, private val id: Int) :
    RemoteViewsService.RemoteViewsFactory {

    private var cfg: AppConfig = ConfigStore.defaults()
    private var pages: List<Page> = emptyList()
    private var wDp = 110f
    private var hDp = 110f
    private val segIds = intArrayOf(R.id.s1, R.id.s2, R.id.s3, R.id.s4, R.id.s5)

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

    override fun getViewTypeCount(): Int = 3

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    override fun getViewAt(position: Int): RemoteViews {
        val layoutId = when (cfg.weight) {
            0 -> R.layout.widget_item_thin
            2 -> R.layout.widget_item_bold
            else -> R.layout.widget_item_light
        }
        val rv = RemoteViews(ctx.packageName, layoutId)
        if (position !in pages.indices) return rv

        val segs = pages[position].segs
        val fit = Fit.layout(segs, wDp, hDp, ctx.resources.displayMetrics.density, cfg.weight)
        val color = 0xFF000000.toInt() or (cfg.textColor and 0xFFFFFF)

        if (fit.stacked) {
            rv.setViewVisibility(R.id.main, View.GONE)
            rv.setViewVisibility(R.id.stack, View.VISIBLE)
            for (i in segIds.indices) {
                if (i < segs.size) {
                    rv.setViewVisibility(segIds[i], View.VISIBLE)
                    rv.setTextViewText(segIds[i], segs[i])
                    rv.setTextViewTextSize(segIds[i], TypedValue.COMPLEX_UNIT_PX, fit.sizePx)
                    rv.setTextColor(segIds[i], color)
                } else {
                    rv.setViewVisibility(segIds[i], View.GONE)
                }
            }
        } else {
            rv.setViewVisibility(R.id.stack, View.GONE)
            rv.setViewVisibility(R.id.main, View.VISIBLE)
            rv.setTextViewText(R.id.main, segs.joinToString("."))
            rv.setTextViewTextSize(R.id.main, TypedValue.COMPLEX_UNIT_PX, fit.sizePx)
            rv.setTextColor(R.id.main, color)
        }

        rv.setOnClickFillInIntent(R.id.item_root, Intent())
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
