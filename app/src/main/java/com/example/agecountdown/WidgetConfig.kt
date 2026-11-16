package com.example.agecountdown

import android.content.Context
import java.time.LocalDateTime

/** All per-widget settings. */
data class WidgetConfig(
    val mode: Int = MODE_AGE,
    val title: String = "",
    val dateTime: LocalDateTime = LocalDateTime.of(2000, 1, 1, 0, 0),
    val showYears: Boolean = true,
    val showMonths: Boolean = true,
    val showDays: Boolean = false,
    val showTime: Boolean = false,
    val compact: Boolean = true,          // true => 28.6   false => 28y 6m
    val bgTransparent: Boolean = false,
    val bgColor: Int = 0x1E1E2E,          // RGB
    val bgOpacity: Int = 85,              // 0..100 (%)
    val textColor: Int = 0xFFFFFF         // RGB
) {
    fun bgAlpha(): Int = if (bgTransparent) 0 else bgOpacity.coerceIn(0, 100) * 255 / 100

    companion object {
        const val MODE_AGE = 0
        const val MODE_COUNTDOWN = 1
    }
}

/** Stores one WidgetConfig per appWidgetId in SharedPreferences. */
object ConfigStore {
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("widget_configs", Context.MODE_PRIVATE)

    fun load(ctx: Context, id: Int): WidgetConfig {
        val sp = prefs(ctx)
        val d = WidgetConfig()
        val raw = sp.getString("$id.dt", null)
        val dt = try {
            if (raw != null) LocalDateTime.parse(raw) else d.dateTime
        } catch (e: Exception) {
            d.dateTime
        }
        return WidgetConfig(
            mode = sp.getInt("$id.mode", d.mode),
            title = sp.getString("$id.title", d.title) ?: "",
            dateTime = dt,
            showYears = sp.getBoolean("$id.y", d.showYears),
            showMonths = sp.getBoolean("$id.m", d.showMonths),
            showDays = sp.getBoolean("$id.d", d.showDays),
            showTime = sp.getBoolean("$id.t", d.showTime),
            compact = sp.getBoolean("$id.compact", d.compact),
            bgTransparent = sp.getBoolean("$id.transparent", d.bgTransparent),
            bgColor = sp.getInt("$id.bg", d.bgColor),
            bgOpacity = sp.getInt("$id.opacity", d.bgOpacity),
            textColor = sp.getInt("$id.fg", d.textColor)
        )
    }

    fun save(ctx: Context, id: Int, c: WidgetConfig) {
        prefs(ctx).edit()
            .putInt("$id.mode", c.mode)
            .putString("$id.title", c.title)
            .putString("$id.dt", c.dateTime.toString())
            .putBoolean("$id.y", c.showYears)
            .putBoolean("$id.m", c.showMonths)
            .putBoolean("$id.d", c.showDays)
            .putBoolean("$id.t", c.showTime)
            .putBoolean("$id.compact", c.compact)
            .putBoolean("$id.transparent", c.bgTransparent)
            .putInt("$id.bg", c.bgColor)
            .putInt("$id.opacity", c.bgOpacity)
            .putInt("$id.fg", c.textColor)
            .apply()
    }

    fun delete(ctx: Context, id: Int) {
        val sp = prefs(ctx)
        val e = sp.edit()
        for (k in sp.all.keys) if (k.startsWith("$id.")) e.remove(k)
        e.apply()
    }
}
