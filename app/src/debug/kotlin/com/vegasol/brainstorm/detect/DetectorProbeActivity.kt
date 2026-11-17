package com.vegasol.brainstorm.detect

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Debug-only surface that mimics what the accessibility tree exposes for a Shorts player. It lets
 * the opt-in pipeline (service -> detector -> Room -> dashboard) be exercised end to end without
 * depending on a particular YouTube build.
 *
 * It is declared in `src/debug/AndroidManifest.xml` and lives only in debug builds.
 */
class DetectorProbeActivity : Activity() {
    private var index = 1
    private var likes = 1234
    private lateinit var title: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(56, 160, 56, 56)
        }

        title = TextView(this).apply {
            text = "probe short $index"
            textSize = 20f
            contentDescription = "Remix this video"
        }
        val channel = TextView(this).apply {
            text = "probe channel"
            textSize = 14f
        }
        val like = Button(this).apply {
            text = "$likes likes"
            contentDescription = "Like this video along with $likes other people"
            setOnClickListener {
                likes++
                text = "$likes likes"
                contentDescription = "Like this video along with $likes other people"
            }
        }
        val next = Button(this).apply {
            text = "Next short"
            setOnClickListener {
                index++
                title.text = "probe short $index"
            }
        }
        val hint = TextView(this).apply {
            text = "Enable content detection in Brain Storm, then press Next. The count in the " +
                "dashboard must go up once per short; pressing Like must not change it."
            textSize = 12f
        }

        root.addView(title)
        root.addView(channel)
        root.addView(like)
        root.addView(next)
        root.addView(hint)
        setContentView(ScrollView(this).apply { addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
    }
}
