package com.example.agecountdown

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDateTime

object WidgetRenderer {

    /** Builds the widget frame: background colour/transparency, scroll adapter, tap action. */
    fun update(ctx: Context, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val cfg = ConfigStore.load(ctx, id)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_root)

        // Background: opaque RGB via colour filter, transparency via image alpha.
        rv.setInt(R.id.bg, "setColorFilter", 0xFF000000.toInt() or (cfg.bgColor and 0xFFFFFF))
        rv.setInt(R.id.bg, "setImageAlpha", cfg.bgAlpha())

        // Scrollable pages
        val svc = Intent(ctx, CountdownWidgetService::class.java)
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        rv.setRemoteAdapter(R.id.stack, svc)

        // Tap => open this widget's settings
        val cfgIntent = Intent(ctx, ConfigActivity::class.java)
        cfgIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        cfgIntent.data = Uri.parse("agecountdown://widget/$id")
        cfgIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val pi = PendingIntent.getActivity(
            ctx, id, cfgIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        rv.setPendingIntentTemplate(R.id.stack, pi)

        mgr.updateAppWidget(id, rv)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.stack)
    }
}

class CountdownWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return PageFactory(applicationContext, id)
    }
}

class PageFactory(private val ctx: Context, private val id: Int) :
    RemoteViewsService.RemoteViewsFactory {

    private var cfg = WidgetConfig()
    private var size = SizeClass.S2x2
    private var pages: List<Page> = emptyList()

    private fun load() {
        cfg = ConfigStore.load(ctx, id)
        size = SizeClass.of(ctx, id)
        pages = Calc.pages(cfg, LocalDateTime.now())
    }

    override fun onCreate() = load()
    override fun onDataSetChanged() = load()
    override fun onDestroy() {}
    override fun getCount(): Int = pages.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
        if (position !in pages.indices) return rv
        val page = pages[position]

        val c = 0xFF000000.toInt() or (cfg.textColor and 0xFFFFFF)
        val dim = (0xB3 shl 24) or (cfg.textColor and 0xFFFFFF)
        rv.setTextColor(R.id.main, c)
        rv.setTextColor(R.id.label, dim)
        rv.setTextColor(R.id.units, dim)
        rv.setTextColor(R.id.extra, dim)

        rv.setTextViewText(R.id.main, page.main)

        if (size.rows == 1) {
            // Short widgets: everything small goes on one line under the number.
            rv.setViewVisibility(R.id.label, View.GONE)
            rv.setViewVisibility(R.id.extra, View.GONE)
            val parts = ArrayList<String>()
            if (size.cols >= 3 && page.label.isNotBlank()) parts.add(page.label)
            if (page.units.isNotBlank()) parts.add(page.units)
            if (page.extra.isNotBlank()) parts.add(page.extra)
            val text = parts.joinToString(if (size.cols == 1) "\n" else " · ")
            rv.setTextViewText(R.id.units, text)
            rv.setViewVisibility(R.id.units, if (text.isBlank()) View.GONE else View.VISIBLE)
            rv.setTextViewTextSize(R.id.units, TypedValue.COMPLEX_UNIT_SP, if (size.cols == 1) 9f else 10f)
        } else {
            val big = size.cols >= 3
            rv.setTextViewText(R.id.label, page.label)
            rv.setViewVisibility(R.id.label, if (page.label.isBlank()) View.GONE else View.VISIBLE)
            rv.setTextViewTextSize(R.id.label, TypedValue.COMPLEX_UNIT_SP, if (big) 14f else 12f)

            rv.setTextViewText(R.id.units, page.units)
            rv.setViewVisibility(R.id.units, if (page.units.isBlank()) View.GONE else View.VISIBLE)
            rv.setTextViewTextSize(R.id.units, TypedValue.COMPLEX_UNIT_SP, if (big) 13f else 11f)

            rv.setTextViewText(R.id.extra, page.extra)
            rv.setViewVisibility(R.id.extra, if (page.extra.isBlank()) View.GONE else View.VISIBLE)
            rv.setTextViewTextSize(R.id.extra, TypedValue.COMPLEX_UNIT_SP, if (big) 14f else 12f)
        }

        rv.setOnClickFillInIntent(R.id.item_root, Intent())
        return rv
    }
}
