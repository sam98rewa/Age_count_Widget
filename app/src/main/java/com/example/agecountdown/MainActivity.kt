package com.example.agecountdown

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = getString(R.string.app_name)
        title.textSize = 24f
        root.addView(title)

        val help = TextView(this)
        help.text = getString(R.string.main_help)
        help.textSize = 15f
        help.setPadding(0, (12 * dp).toInt(), 0, (16 * dp).toInt())
        root.addView(help)

        val items = listOf(
            "1×1" to Widget1x1Provider::class.java,
            "2×1" to Widget2x1Provider::class.java,
            "3×1" to Widget3x1Provider::class.java,
            "2×2" to Widget2x2Provider::class.java,
            "3×2" to Widget3x2Provider::class.java
        )
        for ((name, cls) in items) {
            val b = Button(this)
            b.text = "Add $name widget"
            b.setOnClickListener { pin(cls) }
            root.addView(
                b, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)
    }

    private fun pin(cls: Class<*>) {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, cls), null, null)
        } else {
            Toast.makeText(this, R.string.pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }
}
