package com.example.agecountdown

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var date: LocalDate = LocalDate.of(2000, 1, 1)
    private var time: LocalTime = LocalTime.MIDNIGHT
    private var bgColor = 0x1E1E2E
    private var textColor = 0xFFFFFF
    private var suppressHex = false

    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var etTitle: EditText
    private lateinit var rbAge: RadioButton
    private lateinit var rbCountdown: RadioButton
    private lateinit var tvDateLabel: TextView
    private lateinit var btnDate: Button
    private lateinit var btnTime: Button
    private lateinit var cbYears: CheckBox
    private lateinit var cbMonths: CheckBox
    private lateinit var cbDays: CheckBox
    private lateinit var cbTime: CheckBox
    private lateinit var rbCompact: RadioButton
    private lateinit var rbDetailed: RadioButton
    private lateinit var cbTransparent: CheckBox
    private lateinit var etHex: EditText
    private lateinit var seekOpacity: SeekBar
    private lateinit var tvOpacity: TextView
    private lateinit var pvCard: FrameLayout
    private lateinit var pvLabel: TextView
    private lateinit var pvMain: TextView
    private lateinit var pvUnits: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // If the user backs out while adding a new widget, Android discards it.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        setContentView(R.layout.activity_config)

        etTitle = findViewById(R.id.etTitle)
        rbAge = findViewById(R.id.rbAge)
        rbCountdown = findViewById(R.id.rbCountdown)
        tvDateLabel = findViewById(R.id.tvDateLabel)
        btnDate = findViewById(R.id.btnDate)
        btnTime = findViewById(R.id.btnTime)
        cbYears = findViewById(R.id.cbYears)
        cbMonths = findViewById(R.id.cbMonths)
        cbDays = findViewById(R.id.cbDays)
        cbTime = findViewById(R.id.cbTime)
        rbCompact = findViewById(R.id.rbCompact)
        rbDetailed = findViewById(R.id.rbDetailed)
        cbTransparent = findViewById(R.id.cbTransparent)
        etHex = findViewById(R.id.etHex)
        seekOpacity = findViewById(R.id.seekOpacity)
        tvOpacity = findViewById(R.id.tvOpacity)
        pvCard = findViewById(R.id.pvCard)
        pvLabel = findViewById(R.id.pvLabel)
        pvMain = findViewById(R.id.pvMain)
        pvUnits = findViewById(R.id.pvUnits)

        // ---- load saved values ----
        val cfg = ConfigStore.load(this, widgetId)
        etTitle.setText(cfg.title)
        if (cfg.mode == WidgetConfig.MODE_COUNTDOWN) rbCountdown.isChecked = true else rbAge.isChecked = true
        date = cfg.dateTime.toLocalDate()
        time = cfg.dateTime.toLocalTime()
        cbYears.isChecked = cfg.showYears
        cbMonths.isChecked = cfg.showMonths
        cbDays.isChecked = cfg.showDays
        cbTime.isChecked = cfg.showTime
        if (cfg.compact) rbCompact.isChecked = true else rbDetailed.isChecked = true
        cbTransparent.isChecked = cfg.bgTransparent
        bgColor = cfg.bgColor and 0xFFFFFF
        textColor = cfg.textColor and 0xFFFFFF
        seekOpacity.progress = cfg.bgOpacity
        setHex(bgColor)

        // ---- colour swatches ----
        fillSwatches(
            findViewById(R.id.bgRow),
            intArrayOf(
                0x1E1E2E, 0x000000, 0xFFFFFF, 0x1565C0, 0x2E7D32,
                0xC62828, 0x6A1B9A, 0xEF6C00, 0x00838F, 0xAD1457
            )
        ) { c ->
            bgColor = c
            setHex(c)
            refresh()
        }
        fillSwatches(
            findViewById(R.id.fgRow),
            intArrayOf(
                0xFFFFFF, 0x000000, 0xFFEB3B, 0x80DEEA, 0xA5D6A7,
                0xFFAB91, 0xCE93D8, 0xFFCC80
            )
        ) { c ->
            textColor = c
            refresh()
        }

        // ---- listeners ----
        btnDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    date = LocalDate.of(y, m + 1, d)
                    refresh()
                },
                date.year, date.monthValue - 1, date.dayOfMonth
            ).show()
        }
        btnTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    time = LocalTime.of(h, m)
                    refresh()
                },
                time.hour, time.minute, true
            ).show()
        }

        val modeListener = RadioGroup.OnCheckedChangeListener { _, _ -> refresh() }
        findViewById<RadioGroup>(R.id.rgMode).setOnCheckedChangeListener(modeListener)
        findViewById<RadioGroup>(R.id.rgStyle).setOnCheckedChangeListener(modeListener)

        val unitListener = CompoundButton.OnCheckedChangeListener { btn, _ ->
            // keep at least one unit selected
            if (!cbYears.isChecked && !cbMonths.isChecked && !cbDays.isChecked && !cbTime.isChecked) {
                btn.isChecked = true
            }
            refresh()
        }
        cbYears.setOnCheckedChangeListener(unitListener)
        cbMonths.setOnCheckedChangeListener(unitListener)
        cbDays.setOnCheckedChangeListener(unitListener)
        cbTime.setOnCheckedChangeListener(unitListener)

        cbTransparent.setOnCheckedChangeListener { _, _ -> refresh() }

        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = refresh()
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        etTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) = refresh()
        })

        etHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                if (suppressHex) return
                val s = e.toString().trim().removePrefix("#")
                if (s.length == 6) {
                    try {
                        bgColor = s.toInt(16)
                        refresh()
                    } catch (ex: NumberFormatException) {
                        // ignore until valid
                    }
                }
            }
        })

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        refresh()
    }

    private fun setHex(c: Int) {
        suppressHex = true
        etHex.setText(String.format("%06X", c and 0xFFFFFF))
        suppressHex = false
    }

    private fun fillSwatches(row: LinearLayout, colors: IntArray, onPick: (Int) -> Unit) {
        val dp = resources.displayMetrics.density
        val size = (40 * dp).toInt()
        val m = (4 * dp).toInt()
        for (c in colors) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(m, m, m, m)
            v.layoutParams = lp
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(0xFF000000.toInt() or c)
            g.setStroke((2 * dp).toInt(), 0xFF888888.toInt())
            v.background = g
            v.setOnClickListener { onPick(c) }
            row.addView(v)
        }
    }

    private fun readConfig(): WidgetConfig = WidgetConfig(
        mode = if (rbCountdown.isChecked) WidgetConfig.MODE_COUNTDOWN else WidgetConfig.MODE_AGE,
        title = etTitle.text.toString().trim(),
        dateTime = LocalDateTime.of(date, time),
        showYears = cbYears.isChecked,
        showMonths = cbMonths.isChecked,
        showDays = cbDays.isChecked,
        showTime = cbTime.isChecked,
        compact = rbCompact.isChecked,
        bgTransparent = cbTransparent.isChecked,
        bgColor = bgColor,
        bgOpacity = seekOpacity.progress,
        textColor = textColor
    )

    private fun refresh() {
        if (!::pvCard.isInitialized) return
        val cfg = readConfig()
        val countdown = cfg.mode == WidgetConfig.MODE_COUNTDOWN

        tvDateLabel.text = getString(if (countdown) R.string.label_exam_date else R.string.label_birth_date)
        etTitle.hint = getString(if (countdown) R.string.hint_exam_name else R.string.hint_person_name)
        btnDate.text = date.format(dateFmt)
        btnTime.text = time.format(timeFmt)
        tvOpacity.text = getString(R.string.label_opacity, cfg.bgOpacity)
        seekOpacity.isEnabled = !cfg.bgTransparent
        etHex.isEnabled = !cfg.bgTransparent

        val page = Calc.pages(cfg, LocalDateTime.now()).first()
        pvLabel.text = page.label
        pvMain.text = page.main
        pvUnits.text = listOf(page.units, page.extra).filter { it.isNotBlank() }.joinToString(" · ")

        val fg = 0xFF000000.toInt() or cfg.textColor
        pvLabel.setTextColor(fg)
        pvMain.setTextColor(fg)
        pvUnits.setTextColor(fg)

        val bg = GradientDrawable()
        bg.cornerRadius = 18 * resources.displayMetrics.density
        bg.setColor((cfg.bgAlpha() shl 24) or (cfg.bgColor and 0xFFFFFF))
        pvCard.background = bg
    }

    private fun save() {
        val cfg = readConfig()
        ConfigStore.save(this, widgetId, cfg)
        WidgetRenderer.update(this, widgetId)
        Ticker.schedule(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
