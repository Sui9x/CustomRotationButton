package com.sui.customrotationbutton

import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private val prefName = "rotbtn_prefs"
    private val cfg = UiCfg()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadPrefsIntoCfg()

        val root = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(box)

        // bar
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, sysBars.top, v.paddingRight, v.paddingBottom)
            insets
        }
        
        val status = TextView(this).apply { textSize = 14f }

        fun addTitle(text: String) {
            box.addView(TextView(this).apply { this.text = text; textSize = 18f })
        }

        fun addSwitch(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val tv = TextView(this).apply { text = label; textSize = 16f }
            val sw = Switch(this).apply {
                isChecked = initial
                setOnCheckedChangeListener { _, v ->
                    onChange(v)
                    saveCfgToPrefs()
                    status.text = "Saved"
                }
            }
            row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(sw)
            box.addView(row)
        }

        fun addColorInput(label: String, initialArgb: Int, onChange: (Int) -> Unit) {
            val tv = TextView(this).apply { text = label; textSize = 16f }
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
                setText(toHexArgb(initialArgb))
                hint = "#AARRGGBB or 0xAARRGGBB"
            }
            val err = TextView(this).apply { textSize = 12f }

            fun parseAndSet(s: String) {
                val v = parseHexArgbOrNull(s)
                if (v == null) {
                    err.text = "Invalid. Use #AARRGGBB"
                } else {
                    err.text = ""
                    onChange(v)
                    saveCfgToPrefs()
                    status.text = "Saved"
                }
            }

            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    parseAndSet(s?.toString() ?: "")
                }
            })

            box.addView(tv)
            box.addView(et)
            box.addView(err)
        }

        addTitle("Custom Rotation Button")

        addSwitch("Enable override colors", cfg.enabled) { cfg.enabled = it }
        
        addSwitch("Enable draw hook (workaround)", cfg.useDrawHook) { cfg.useDrawHook = it }
        
        addSwitch("Hide button (override diameter)", cfg.hideButton) { cfg.hideButton = it }

        addColorInput("Icon Color (ARGB)", cfg.iconArgb) { cfg.iconArgb = it }
        
        addColorInput("Background Color (ARGB)", cfg.bgArgb) { cfg.bgArgb = it }

        val btnRestart = Button(this).apply {
            text = "Restart SystemUI"
            setOnClickListener {
                status.text = "Restarting SystemUI..."
                Thread {
                    val ok = restartSystemUI()
                    runOnUiThread {
                        status.text = if (ok) "SystemUI restarted" else "Failed (su?)"
                    }
                }.start()
            }
        }
        box.addView(btnRestart)
        //box.addView(status)
        setContentView(root)
    }

    // prefs
    private fun sp(): SharedPreferences =
        getSharedPreferences(prefName, MODE_WORLD_READABLE)

    private fun loadPrefsIntoCfg() {
        val sp = sp()
        cfg.enabled = sp.getBoolean("enabled", true)
        cfg.useDrawHook = sp.getBoolean("useDrawHook", false)
        cfg.hideButton = sp.getBoolean("hideButton", false)
        cfg.iconArgb = sp.getInt("iconArgb", 0xFFFFFFFF.toInt())
        cfg.bgArgb = sp.getInt("bgArgb", 0xCC000000.toInt())
    }

    private fun saveCfgToPrefs() {
        sp().edit()
            .putBoolean("enabled", cfg.enabled)
            .putBoolean("hideButton", cfg.hideButton)
            .putBoolean("useDrawHook", cfg.useDrawHook)
            .putInt("iconArgb", cfg.iconArgb)
            .putInt("bgArgb", cfg.bgArgb)
            .commit()
    }

    // utils
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun parseHexArgbOrNull(s: String): Int? {
        val t = s.trim()
        val hex = when {
            t.startsWith("#") -> t.substring(1)
            t.startsWith("0x", ignoreCase = true) -> t.substring(2)
            else -> t
        }
        if (hex.length != 8) return null
        return try { (hex.toLong(16) and 0xFFFFFFFFL).toInt() } catch (_: Throwable) { null }
    }

    private fun toHexArgb(v: Int): String =
        String.format("#%08X", (v.toLong() and 0xFFFFFFFFL))
    
    private fun su(cmd: String): Pair<Int, String> {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            val code = p.waitFor()
            code to (out + if (err.isNotBlank()) "\n$err" else "")
        } catch (t: Throwable) {
            -1 to (t.javaClass.simpleName + ": " + (t.message ?: ""))
        }
    }

    private fun restartSystemUI(): Boolean {
        val cmds = listOf(
            "pkill -f com.android.systemui",
            "killall com.android.systemui",
            "am crash com.android.systemui"
        )
        for (c in cmds) {
            val (code, _) = su(c)
            if (code == 0) return true
        }
        return false
    }

    private class UiCfg {
        var enabled: Boolean = true
        var hideButton: Boolean = false
        var useDrawHook: Boolean = false
        var iconArgb: Int = 0xFFFFFFFF.toInt()
        var bgArgb: Int = 0xCC000000.toInt()
    }
}
