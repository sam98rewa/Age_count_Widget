package com.example.agecountdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Widget cell sizes (columns x rows). */
enum class SizeClass(val cols: Int, val rows: Int) {
    S1x1(1, 1), S2x1(2, 1), S3x1(3, 1), S2x2(2, 2), S3x2(3, 2);

    companion object {
        fun of(ctx: Context, id: Int): SizeClass {
            val name = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.provider?.className ?: ""
            return when {
                name.endsWith("Widget1x1Provider") -> S1x1
                name.endsWith("Widget2x1Provider") -> S2x1
                name.endsWith("Widget3x1Provider") -> S3x1
                name.endsWith("Widget3x2Provider") -> S3x2
                else -> S2x2
            }
        }
    }
}

abstract class BaseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) WidgetRenderer.update(context, id)
        Ticker.schedule(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) ConfigStore.delete(context, id)
    }

    override fun onEnabled(context: Context) = Ticker.schedule(context)

    override fun onDisabled(context: Context) = Ticker.schedule(context)
}

class Widget1x1Provider : BaseWidgetProvider()
class Widget2x1Provider : BaseWidgetProvider()
class Widget3x1Provider : BaseWidgetProvider()
class Widget2x2Provider : BaseWidgetProvider()
class Widget3x2Provider : BaseWidgetProvider()

/** Periodic refresh so the age / countdown / clock stay current. */
object Ticker {
    const val ACTION = "com.example.agecountdown.TICK"

    val PROVIDERS: List<Class<*>> = listOf(
        Widget1x1Provider::class.java,
        Widget2x1Provider::class.java,
        Widget3x1Provider::class.java,
        Widget2x2Provider::class.java,
        Widget3x2Provider::class.java
    )

    fun allIds(ctx: Context): List<Int> {
        val mgr = AppWidgetManager.getInstance(ctx)
        val out = ArrayList<Int>()
        for (cls in PROVIDERS) {
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, cls))) out.add(id)
        }
        return out
    }

    fun refreshAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = allIds(ctx).toIntArray()
        if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids, R.id.stack)
    }

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, TickReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Every minute when any widget shows the time, otherwise every 30 minutes. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx)
        val ids = allIds(ctx)
        if (ids.isEmpty()) {
            am.cancel(pi)
            return
        }
        val anyTime = ids.any { ConfigStore.load(ctx, it).showTime }
        val step = if (anyTime) 60_000L else 1_800_000L
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
