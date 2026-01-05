package com.sui.customrotationbutton

import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge

object RotCfg {
    private const val MOD_PKG = "com.sui.customrotationbutton"
    private const val PREF_NAME = "rotbtn_prefs"
    private fun log(s: String) = XposedBridge.log("CustomRotBtn: $s")

    data class Cfg(
        val enabled: Boolean = true,
        val hideButton: Boolean = false,
        val useDrawHook: Boolean = false,
        val iconArgb: Int = 0xFFFFFFFF.toInt(),
        val bgArgb: Int = 0xCC000000.toInt()
    )

    private val xsp: XSharedPreferences by lazy { XSharedPreferences(MOD_PKG, PREF_NAME) }

    @Volatile private var cached = Cfg()
    @Volatile private var inited = false

    fun initOnce() {
        if (inited) return
        synchronized(this) {
            if (inited) return
            try {
                log("reload(initOnce)")
                xsp.reload()
            } catch (t: Throwable) {
                log("reload(initOnce) FAIL: ${t.javaClass.simpleName}:${t.message}")
            }
            cached = readNoReload()
            inited = true
        }
    }

    //setColors
    fun get(): Cfg = cached

    //Hide
    fun hideButtonCached(): Boolean = cached.hideButton

    private fun readNoReload(): Cfg {
        return Cfg(
            enabled = xsp.getBoolean("enabled", true),
            hideButton = xsp.getBoolean("hideButton", false),
            useDrawHook = xsp.getBoolean("useDrawHook", false),
            iconArgb = xsp.getInt("iconArgb", 0xFFFFFFFF.toInt()),
            bgArgb = xsp.getInt("bgArgb", 0xCC000000.toInt())
        )
    }
}
