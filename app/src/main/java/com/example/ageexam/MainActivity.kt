package com.example.ageexam

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The whole app: live preview on top, every setting below.
 * Every change is saved immediately and pushed to all widgets.
 */
class MainActivity : Activity() {

    private lateinit var cfg: AppConfig
    private lateinit var preview: PreviewView
    private val refreshers = ArrayList<() -> Unit>()

    private val dp: Float by lazy { resources.displayMetrics.density }
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private val bgPage = 0xFF0F1013.toInt()
    private val bgCard = 0xFF1A1C21.toInt()
    private val chipOff = 0xFF2A2D34.toInt()
    private val accent = 0xFF8AB4F8.toInt()
    private val textMain = 0xFFE8EAED.toInt()
    private val textMuted = 0xFF9AA0A6.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = ConfigStore.load(this)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bgPage)
        scroll.isFillViewport = true

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(px(16), px(20), px(16), px(28))
        scroll.addView(root)

        val title = TextView(this)
        title.text = "AgeExam"
        title.textSize = 30f
        title.setTextColor(textMain)
        title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        title.setPadding(px(4), px(8), 0, px(12))
        root.addView(title)

        buildPreviewCard(root)
        buildAgeCard(root)
        buildExamCard(root)
        buildStyleCard(root)
        buildBackgroundCard(root)

        val add = pill("Add to home screen", true)
        add.setOnClickListener { pinWidget() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = px(16)
        root.addView(add, lp)

        setContentView(scroll)
        applyAll()
    }

    // ---------------------------------------------------------------- state

    private fun commit(c: AppConfig) {
        cfg = c
        ConfigStore.save(this, c)
        WidgetRenderer.updateAll(this)
        Ticker.schedule(this)
        applyAll()
    }

    private fun applyAll() {
        preview.setConfig(cfg)
        for (r in refreshers) r()
    }

    private fun pinWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        var asked = false
        try {
            if (mgr.isRequestPinAppWidgetSupported) {
                asked = mgr.requestPinAppWidget(
                    ComponentName(this, AgeExamWidgetProvider::class.java), null, null
                )
            }
        } catch (e: Exception) {
            asked = false
        }
        if (asked) {
            Toast.makeText(this, "Confirm in the popup to place the widget", Toast.LENGTH_LONG).show()
            return
        }
        // Some launchers block this button. Adding from the widget list always works.
        AlertDialog.Builder(this)
            .setTitle("Add the widget manually")
            .setMessage(
                "1. Go to your home screen and long-press an empty spot.\n" +
                    "2. Tap Widgets and find AgeExam.\n" +
                    "3. Drag it onto the screen, then resize it by its handles.\n\n" +
                    "If the popup never appears, allow \"Home screen shortcuts\" for AgeExam in App settings."
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("App settings") { _, _ ->
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                startActivity(i)
            }
            .show()
    }

    // ---------------------------------------------------------------- cards

    private fun buildPreviewCard(root: LinearLayout) {
        val card = card(root, "Preview")

        preview = PreviewView(this)
        card.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(300)))

        val sizes = listOf(
            Triple("1×1", 72f, 72f),
            Triple("2×1", 160f, 72f),
            Triple("2×2", 160f, 160f),
            Triple("4×2", 300f, 160f),
            Triple("4×5", 300f, 372f)
        )
        var selected = 2
        preview.setSizeDp(sizes[selected].second, sizes[selected].third)
        val row = row()
        for ((i, s) in sizes.withIndex()) {
            val sizeChip = chip(s.first, { selected == i }) {
                selected = i
                preview.setSizeDp(s.second, s.third)
                applyAll()
            }
            row.addView(sizeChip, weighted())
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = px(12)
        card.addView(row, lp)
    }

    private fun buildAgeCard(root: LinearLayout) {
        val card = card(root, "Age")
        card.addView(
            dateRow("Date of birth", { cfg.birth }, { commit(cfg.copy(birth = it)) })
        )
        card.addView(
            unitRow({ cfg.ageUnits }, { commit(cfg.copy(ageUnits = it)) })
        )
    }

    private fun buildExamCard(root: LinearLayout) {
        val card = card(root, "Exam countdown")
        card.addView(
            dateRow("Exam date", { cfg.exam }, { commit(cfg.copy(exam = it)) })
        )
        card.addView(
            unitRow({ cfg.examUnits }, { commit(cfg.copy(examUnits = it)) })
        )
    }

    private fun buildStyleCard(root: LinearLayout) {
        val card = card(root, "Style")

        val lay = row()
        val layNames = listOf("Auto", "Horizontal", "Stacked")
        for (i in layNames.indices) {
            lay.addView(chip(layNames[i], { cfg.layout == i }) { commit(cfg.copy(layout = i)) }, weighted())
        }
        card.addView(lay)

        val padRow = switchRow("Two-digit numbers", { cfg.pad }) { commit(cfg.copy(pad = it)) }
        val padLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        padLp.topMargin = px(14)
        padRow.layoutParams = padLp
        card.addView(padRow)

        val w = row()
        val names = listOf("Thin", "Light", "Bold")
        for (i in names.indices) {
            w.addView(chip(names[i], { cfg.weight == i }) { commit(cfg.copy(weight = i)) }, weighted())
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = px(10)
        card.addView(w, lp)

        card.addView(subLabel("Text colour"))
        card.addView(
            swatches(
                intArrayOf(0xFFFFFF, 0x000000, 0xFDD663, 0x81C995, 0x8AB4F8, 0xF28B82, 0xD7AEFB, 0xFCAD70),
                { cfg.textColor }, { commit(cfg.copy(textColor = it)) }
            )
        )
        card.addView(hexField({ cfg.textColor }, { commit(cfg.copy(textColor = it)) }))
    }

    private fun buildBackgroundCard(root: LinearLayout) {
        val card = card(root, "Background")

        card.addView(switchRow("Transparent", { cfg.transparent }) { commit(cfg.copy(transparent = it)) })

        card.addView(subLabel("Colour"))
        card.addView(
            swatches(
                intArrayOf(
                    0x1B1B1F, 0x000000, 0xFFFFFF, 0x1A73E8, 0x188038,
                    0xD93025, 0x9334E6, 0xF29900, 0x12B5CB, 0xE52592
                ),
                { cfg.bgColor }, { commit(cfg.copy(bgColor = it)) }
            )
        )
        card.addView(hexField({ cfg.bgColor }, { commit(cfg.copy(bgColor = it)) }))

        val opLabel = subLabel("Opacity")
        card.addView(opLabel)
        val seek = SeekBar(this)
        seek.max = 100
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) commit(cfg.copy(bgOpacity = p))
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        card.addView(seek)
        refreshers.add {
            opLabel.text = "Opacity  ${cfg.bgOpacity}%"
            if (seek.progress != cfg.bgOpacity) seek.progress = cfg.bgOpacity
            seek.isEnabled = !cfg.transparent
            seek.alpha = if (cfg.transparent) 0.4f else 1f
        }
    }

    // ---------------------------------------------------------------- building blocks

    private fun px(v: Int): Int = (v * dp).toInt()

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radiusDp * dp
        return g
    }

    private fun card(root: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(bgCard, 24)
        c.setPadding(px(16), px(14), px(16), px(16))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = px(12)
        root.addView(c, lp)

        val t = TextView(this)
        t.text = title
        t.textSize = 13f
        t.setTextColor(accent)
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        t.setPadding(0, 0, 0, px(10))
        c.addView(t)
        return c
    }

    private fun subLabel(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 12f
        t.setTextColor(textMuted)
        t.setPadding(0, px(14), 0, px(6))
        return t
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun weighted(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(px(3), 0, px(3), 0)
        return lp
    }

    private fun pill(text: String, filled: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.gravity = Gravity.CENTER
        t.textSize = 14f
        t.setPadding(px(14), px(13), px(14), px(13))
        t.background = rounded(if (filled) accent else chipOff, 28)
        t.setTextColor(if (filled) 0xFF0F1013.toInt() else textMain)
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        return t
    }

    private fun chip(text: String, selected: () -> Boolean, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.gravity = Gravity.CENTER
        t.textSize = 13f
        t.setPadding(px(4), px(10), px(4), px(10))
        t.setOnClickListener { onClick() }
        val style: () -> Unit = {
            val on = selected()
            t.background = rounded(if (on) accent else chipOff, 20)
            t.setTextColor(if (on) 0xFF0F1013.toInt() else textMain)
        }
        style()
        refreshers.add(style)
        return t
    }

    private fun dateRow(
        label: String,
        get: () -> LocalDateTime,
        set: (LocalDateTime) -> Unit
    ): LinearLayout {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL

        val l = TextView(this)
        l.text = label
        l.textSize = 12f
        l.setTextColor(textMuted)
        l.setPadding(0, 0, 0, px(6))
        wrap.addView(l)

        val r = row()
        val dateBtn = pill("", false)
        val timeBtn = pill("", false)
        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
        lp1.rightMargin = px(6)
        r.addView(dateBtn, lp1)
        r.addView(timeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wrap.addView(r)

        dateBtn.setOnClickListener {
            val cur = get()
            DatePickerDialog(
                this,
                { _, y, m, d -> set(LocalDateTime.of(LocalDate.of(y, m + 1, d), cur.toLocalTime())) },
                cur.year, cur.monthValue - 1, cur.dayOfMonth
            ).show()
        }
        timeBtn.setOnClickListener {
            val cur = get()
            TimePickerDialog(
                this,
                { _, h, m -> set(LocalDateTime.of(cur.toLocalDate(), LocalTime.of(h, m))) },
                cur.hour, cur.minute, true
            ).show()
        }
        refreshers.add {
            dateBtn.text = get().format(dateFmt)
            timeBtn.text = get().format(timeFmt)
        }
        return wrap
    }

    private fun unitRow(get: () -> Int, set: (Int) -> Unit): LinearLayout {
        val r = row()
        val names = listOf("Years", "Months", "Days", "Hours", "Min")
        val flags = intArrayOf(U.Y, U.M, U.D, U.H, U.MIN)
        for (i in names.indices) {
            val c = chip(names[i], { U.has(get(), flags[i]) }) {
                val next = get() xor flags[i]
                if ((next and U.ALL) != 0) set(next)
            }
            r.addView(c, weighted())
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = px(12)
        r.layoutParams = lp
        return r
    }

    private fun switchRow(label: String, get: () -> Boolean, set: (Boolean) -> Unit): LinearLayout {
        val r = row()
        r.gravity = Gravity.CENTER_VERTICAL
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTextColor(textMain)
        r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        r.addView(sw)
        sw.setOnCheckedChangeListener { _, checked ->
            if (checked != get()) set(checked)
        }
        refreshers.add {
            if (sw.isChecked != get()) sw.isChecked = get()
        }
        return r
    }

    private fun swatches(colors: IntArray, get: () -> Int, set: (Int) -> Unit): HorizontalScrollView {
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = row()
        hs.addView(row)
        for (c in colors) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(px(38), px(38))
            lp.setMargins(px(3), px(3), px(7), px(3))
            v.layoutParams = lp
            v.setOnClickListener { set(c) }
            row.addView(v)
            refreshers.add {
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(0xFF000000.toInt() or c)
                val sel = (get() and 0xFFFFFF) == c
                g.setStroke(if (sel) px(3) else px(1), if (sel) accent else 0x44FFFFFF)
                v.background = g
            }
        }
        return hs
    }

    private fun hexField(get: () -> Int, set: (Int) -> Unit): EditText {
        val et = EditText(this)
        et.hint = "Custom hex, e.g. 1A73E8"
        et.setHintTextColor(textMuted)
        et.setTextColor(textMain)
        et.textSize = 14f
        et.isSingleLine = true
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        et.filters = arrayOf(android.text.InputFilter.LengthFilter(7))
        et.background = rounded(chipOff, 14)
        et.setPadding(px(14), px(11), px(14), px(11))
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                if (!et.hasFocus()) return
                val s = e.toString().trim().removePrefix("#")
                if (s.length == 6) {
                    val v = s.toIntOrNull(16)
                    if (v != null && v != (get() and 0xFFFFFF)) set(v)
                }
            }
        })
        refreshers.add {
            if (!et.hasFocus()) et.setText(String.format("%06X", get() and 0xFFFFFF))
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = px(8)
        et.layoutParams = lp
        return et
    }
}
