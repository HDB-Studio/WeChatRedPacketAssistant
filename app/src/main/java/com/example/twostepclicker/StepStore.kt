package com.example.twostepclicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object StepStore {

    private const val PREFS = "twostep_prefs"
    private const val KEY_STEPS = "steps"
    private const val KEY_TARGET = "target_package"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Step> {
        val raw = prefs(context).getString(KEY_STEPS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Step(
                            kind = runCatching {
                                StepKind.valueOf(o.optString("kind", "TEXT"))
                            }.getOrDefault(StepKind.TEXT),
                            value = o.optString("value", ""),
                            xPercent = o.optInt("x", 50),
                            yPercent = o.optInt("y", 50),
                            waitBeforeMs = o.optLong("wait", 500L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, steps: List<Step>) {
        val arr = JSONArray()
        steps.forEach { s ->
            arr.put(
                JSONObject()
                    .put("kind", s.kind.name)
                    .put("value", s.value)
                    .put("x", s.xPercent)
                    .put("y", s.yPercent)
                    .put("wait", s.waitBeforeMs)
            )
        }
        prefs(context).edit().putString(KEY_STEPS, arr.toString()).apply()
    }

    fun targetPackage(context: Context): String =
        prefs(context).getString(KEY_TARGET, "") ?: ""

    fun setTargetPackage(context: Context, pkg: String) {
        prefs(context).edit().putString(KEY_TARGET, pkg.trim()).apply()
    }
}
