package com.sui.customrotationbutton

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.widget.ImageView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class Hook : IXposedHookLoadPackage {

    companion object {
        private const val PKG_SYSTEMUI = "com.android.systemui"
        private const val CLS_FRB = "com.android.systemui.shared.rotation.FloatingRotationButton"
        private const val CLS_FRBV = "com.android.systemui.shared.rotation.FloatingRotationButtonView"
        private const val CLS_KBD = "com.android.systemui.statusbar.policy.KeyButtonDrawable"
        private const val CLS_KBV = "com.android.systemui.statusbar.policy.KeyButtonView"

        // tag key
        private val TAG_KEY_COLORS = com.sui.customrotationbutton.R.id.tag_rotbtn_colors
    }

    private fun log(msg: String) = XposedBridge.log("CustomRotBtn: $msg")

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PKG_SYSTEMUI) return
        val cl = lpparam.classLoader

        RotCfg.initOnce()

        hookGetDimens()
        hookSetColors(cl)
        hookUpdateIcon(cl)
        hookCreate(cl)
        
        val cfg = RotCfg.get()
        if (cfg.useDrawHook) {
            hookDraw(cl)
            log("draw hook enabled")
        } else {
            log("draw hook disabled")
        }

        log("hooks installed for SystemUI (cl=$cl)")
    }

    // hookAll overloads
    private fun hookAll(className: String, methodName: String, hook: XC_MethodHook) {
        try {
            val c = XposedHelpers.findClass(className, null)
            XposedBridge.hookAllMethods(c, methodName, hook)
            log("HOOKED(all): $className->$methodName (boot)")
        } catch (t: Throwable) {
            log("FAILED: $className->$methodName (boot) : ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun hookAll(cl: ClassLoader, className: String, methodName: String, hook: XC_MethodHook) {
        try {
            val c = XposedHelpers.findClass(className, cl)
            XposedBridge.hookAllMethods(c, methodName, hook)
            log("HOOKED(all): $className->$methodName (cl)")
        } catch (t: Throwable) {
            log("FAILED: $className->$methodName (cl) : ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // Resources#getDimension
    private fun hookGetDimens() {
        fun isTarget(res: Resources, id: Int): Boolean {
            if (!RotCfg.hideButtonCached()) return false
            return try {
                when (res.getResourceEntryName(id)) {
                    "floating_rotation_button_diameter",
                    "rotation_button_diameter" -> true
                    else -> false
                }
            } catch (_: Throwable) {
                false
            }
        }

        hookAll(
            "android.content.res.Resources",
            "getDimensionPixelSize",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val res = param.thisObject as Resources
                    val id = param.args[0] as Int
                    if (isTarget(res, id)) param.result = 0
                }
            }
        )

        hookAll(
            "android.content.res.Resources",
            "getDimensionPixelOffset",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val res = param.thisObject as Resources
                    val id = param.args[0] as Int
                    if (isTarget(res, id)) param.result = 0
                }
            }
        )

        hookAll(
            "android.content.res.Resources",
            "getDimension",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val res = param.thisObject as Resources
                    val id = param.args[0] as Int
                    if (isTarget(res, id)) param.result = 0f
                }
            }
        )

        log("dimen hooks installed")
    }

    // setColors
    private fun hookSetColors(cl: ClassLoader) {
        hookAll(cl, CLS_FRBV, "setColors", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val cfg = RotCfg.get()
                if (!cfg.enabled) return
                val v = param.thisObject as? ImageView ?: return
                forceColors(v, cfg)
                log("hookSetColors()")
            }
        })
        log("setColors hook installed")
    }
    
    private fun hookUpdateIcon(cl: ClassLoader) {
        hookAll(cl, CLS_FRB, "updateIcon", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val cfg = RotCfg.get()
                if (!cfg.enabled) return

                // thisObject = FloatingRotationButton
                val btn = param.thisObject ?: return

                // mKeyButtonView
                val v = runCatching {
                    XposedHelpers.getObjectField(btn, "mKeyButtonView") as? ImageView
                }.getOrNull() ?: return

                forceColors(v, cfg)
                log("hookUpdateIcon()")
            }
        })
        log("updateIcon hook installed")
    }

    private fun hookCreate(cl: ClassLoader) {
        hookAll(cl, CLS_KBD, "create", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val cfg = RotCfg.get()
                if (!cfg.enabled) return
                
                // create(Context, int light, int dark, int iconRes, boolean shadow, Color ovalBg)
                if (param.args.size < 6) return

                val ctx = param.args[0] as? android.content.Context ?: return
                val iconRes = (param.args[3] as? Int) ?: return

                // rotate
                val rotateId = ctx.resources.getIdentifier(
                    "ic_sysbar_rotate_button", "drawable", ctx.packageName
                )
                if (rotateId == 0 || iconRes != rotateId) return

                val newLight = cfg.iconArgb
                val newDark = cfg.bgArgb

                // light/dark cfg
                param.args[1] = newLight
                param.args[2] = newDark

                val oval = android.graphics.Color.valueOf(newDark)

                when (param.args[5]) {
                    is android.graphics.Color -> param.args[5] = oval
                    is Int -> param.args[5] = newDark
                }

                log("hookCreate()")
            }
        })
        log("create hook installed")
    }

    private fun hookDraw(cl: ClassLoader) {
        hookAll(cl, CLS_FRBV, "draw", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val cfg = RotCfg.get()
                if (!cfg.enabled) return
                val v = param.thisObject as? ImageView ?: return
                forceColorsIfNeededReal(v, cfg)
                log("hookDraw()")
            }
        })
        log("draw hook installed")
    }
    
    private fun forceColors(v: ImageView, cfg: RotCfg.Cfg) {
        try {
            val d: Drawable? = v.drawable
            d?.setColorFilter(cfg.iconArgb, PorterDuff.Mode.SRC_IN)

            val paint = XposedHelpers.getObjectField(v, "mOvalBgPaint") as? Paint
            paint?.color = cfg.bgArgb

            v.invalidate()
        } catch (_: Throwable) {
        }
    }

    private fun forceColorsIfNeeded(v: ImageView, cfg: RotCfg.Cfg) {
        try {
            val now = packColors(cfg.iconArgb, cfg.bgArgb)
            val prev = (v.getTag(TAG_KEY_COLORS) as? Long) ?: Long.MIN_VALUE
            if (prev == now) return
            v.setTag(TAG_KEY_COLORS, now)

            val d: Drawable? = v.drawable
            d?.setColorFilter(cfg.iconArgb, PorterDuff.Mode.SRC_IN)

            val paint = XposedHelpers.getObjectField(v, "mOvalBgPaint") as? Paint
            paint?.color = cfg.bgArgb

            // v.invalidate()
        } catch (_: Throwable) {
        }
    }
    
    private fun forceColorsIfNeededReal(v: ImageView, cfg: RotCfg.Cfg) {
        try {
            val paint = XposedHelpers.getObjectField(v, "mOvalBgPaint") as? Paint
            val curBg = paint?.color
            val needBg = (curBg == null || curBg != cfg.bgArgb)

            if (!needBg) {
                //setColorFilter
            }

            val d = v.drawable
            d?.setColorFilter(cfg.iconArgb, PorterDuff.Mode.SRC_IN)

            if (needBg) {
                paint?.color = cfg.bgArgb
            }
        } catch (_: Throwable) {}
    }

    private fun packColors(icon: Int, bg: Int): Long {
        val hi = icon.toLong() and 0xFFFFFFFFL
        val lo = bg.toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }
}
