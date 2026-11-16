package com.example.agecountdown

import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** One swipeable page inside a widget. */
data class Page(val label: String, val main: String, val units: String, val extra: String)

data class Parts(val years: Long, val months: Long, val days: Long, val hours: Long, val minutes: Long)

object Calc {

    /**
     * Splits the span a..b (a <= b) into the selected units only.
     * Unselected larger units roll down into the next selected one
     * (e.g. no "years" => 340 months).
     */
    fun breakdown(
        a: LocalDateTime, b: LocalDateTime,
        years: Boolean, months: Boolean, days: Boolean, time: Boolean
    ): Parts {
        var cur = a
        var yy = 0L
        var mm = 0L
        var dd = 0L
        var hh = 0L
        var mi = 0L
        if (years) {
            yy = ChronoUnit.YEARS.between(cur, b)
            cur = cur.plusYears(yy)
        }
        if (months) {
            mm = ChronoUnit.MONTHS.between(cur, b)
            cur = cur.plusMonths(mm)
        }
        if (days) {
            dd = ChronoUnit.DAYS.between(cur, b)
            cur = cur.plusDays(dd)
        }
        if (time) {
            val secs = ChronoUnit.SECONDS.between(cur, b)
            hh = secs / 3600
            mi = (secs % 3600) / 60
        }
        return Parts(yy, mm, dd, hh, mi)
    }

    private fun two(n: Long) = n.toString().padStart(2, '0')

    fun pages(cfg: WidgetConfig, now: LocalDateTime): List<Page> {
        val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
        val numFmt = NumberFormat.getIntegerInstance()

        val target = cfg.dateTime
        val isAge = cfg.mode == WidgetConfig.MODE_AGE
        val passed = target.isBefore(now)
        val start = if (passed) target else now
        val end = if (passed) now else target
        val label = cfg.title.ifBlank { if (isAge) "Age" else "Exam" }

        // ---- Page 1: the units the user selected ----
        val none = !(cfg.showYears || cfg.showMonths || cfg.showDays || cfg.showTime)
        val useDays = cfg.showDays || none
        val p = breakdown(start, end, cfg.showYears, cfg.showMonths, useDays, cfg.showTime)

        val nums = ArrayList<String>()
        val names = ArrayList<String>()
        val abbr = ArrayList<String>()
        if (cfg.showYears) { nums.add(p.years.toString()); names.add("years"); abbr.add("${p.years}y") }
        if (cfg.showMonths) { nums.add(p.months.toString()); names.add("months"); abbr.add("${p.months}m") }
        if (useDays) { nums.add(p.days.toString()); names.add("days"); abbr.add("${p.days}d") }
        val timeStr = if (cfg.showTime) "${two(p.hours)}:${two(p.minutes)}" else ""

        var main: String
        var units: String
        var extra: String
        if (nums.isEmpty()) {
            main = timeStr
            units = "hours:min"
            extra = ""
        } else if (cfg.compact) {
            main = nums.joinToString(".")          // e.g. 28.6
            units = names.joinToString(".")        // e.g. years.months
            extra = timeStr
        } else {
            main = abbr.joinToString(" ")          // e.g. 28y 6m
            units = ""
            extra = timeStr
        }
        if (!isAge && passed) units = (units + " ago").trim()

        val list = ArrayList<Page>()
        list.add(Page(label, main, units, extra))

        // ---- Page 2: total days ----
        val totalDays = ChronoUnit.DAYS.between(start, end)
        val dayUnits = when {
            isAge && passed -> "days lived"
            isAge -> "days until birth"
            passed -> "days ago"
            else -> "days left"
        }
        list.add(Page(label, numFmt.format(totalDays), dayUnits, target.toLocalDate().format(dateFmt)))

        // ---- Page 3 ----
        if (isAge && passed) {
            val today = now.toLocalDate()
            val birth = target.toLocalDate()
            var next = birth.withYear(today.year)
            if (next.isBefore(today)) next = birth.withYear(today.year + 1)
            val toNext = ChronoUnit.DAYS.between(today, next)
            list.add(Page(label, numFmt.format(toNext), "days to birthday", "turns ${next.year - birth.year}"))
        } else if (!isAge) {
            val w = "${totalDays / 7}.${totalDays % 7}"
            list.add(Page(label, w, if (passed) "weeks.days ago" else "weeks.days left", ""))
        }
        return list
    }
}
