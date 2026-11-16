package com.zmanim.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AlarmMode(val id: String, val name: String, val isActive: Boolean)

data class AlarmEntity(
    val id: String,
    val label: String,
    val isRelative: Boolean,
    val fixedHour: Int = 7,
    val fixedMinute: Int = 0,
    val zmanEvent: ZmanEvent = ZmanEvent.SUNRISE,
    val offsetMinutes: Int = 0,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5),
    val modeId: String = "routine",
    val isEnabled: Boolean = true,
    val presetBg: String = "none",
    val customImageUri: String? = null
)

object AppStorage {
    private const val PREFS = "zmanim_clock_v1"

    fun loadModes(ctx: Context): List<AlarmMode> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("modes", null)
            ?: return listOf(
                AlarmMode("routine", "שגרה", true),
                AlarmMode("vacation", "חופשה", false),
                AlarmMode("shabbat", "שבת וחג", true),
                AlarmMode("winter", "חורף", false)
            )
        val arr = JSONArray(raw)
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            AlarmMode(o.getString("id"), o.getString("name"), o.getBoolean("isActive"))
        }
    }

    fun saveModes(ctx: Context, modes: List<AlarmMode>) {
        val arr = JSONArray()
        modes.forEach {
            arr.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("isActive", it.isActive) })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("modes", arr.toString()).apply()
    }

    fun loadAlarms(ctx: Context): List<AlarmEntity> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("alarms", null)
            ?: return listOf(
                AlarmEntity("1", "שחרית ותיקין", true, zmanEvent = ZmanEvent.SUNRISE, offsetMinutes = -20, modeId = "routine", presetBg = "prayer"),
                AlarmEntity("2", "סוף זמן ק״ש", true, zmanEvent = ZmanEvent.SOF_ZMAN_SHMA, offsetMinutes = -15, daysOfWeek = setOf(1,2,3,4,5,6,7), modeId = "routine", presetBg = "goodmorning"),
                AlarmEntity("3", "יציאה לעבודה", false, fixedHour = 8, fixedMinute = 0, modeId = "routine", presetBg = "work"),
                AlarmEntity("4", "צאת שבת", true, zmanEvent = ZmanEvent.TZEIT_HAKOCHAVIM, offsetMinutes = 5, daysOfWeek = setOf(7), modeId = "shabbat", presetBg = "goodnight")
            )
        val arr = JSONArray(raw)
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            val dArr = o.getJSONArray("days")
            AlarmEntity(
                id = o.getString("id"),
                label = o.getString("label"),
                isRelative = o.getBoolean("isRelative"),
                fixedHour = o.getInt("fixedHour"),
                fixedMinute = o.getInt("fixedMinute"),
                zmanEvent = ZmanEvent.valueOf(o.getString("zmanEvent")),
                offsetMinutes = o.getInt("offsetMinutes"),
                daysOfWeek = (0 until dArr.length()).map { idx -> dArr.getInt(idx) }.toSet(),
                modeId = o.getString("modeId"),
                isEnabled = o.getBoolean("isEnabled"),
                presetBg = o.optString("presetBg", "none"),
                customImageUri = o.optString("customImageUri", "").takeIf { s -> s.isNotBlank() }
            )
        }
    }

    fun saveAlarms(ctx: Context, alarms: List<AlarmEntity>) {
        val arr = JSONArray()
        alarms.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id); put("label", a.label); put("isRelative", a.isRelative)
                put("fixedHour", a.fixedHour); put("fixedMinute", a.fixedMinute)
                put("zmanEvent", a.zmanEvent.name); put("offsetMinutes", a.offsetMinutes)
                put("days", JSONArray(a.daysOfWeek.toList()))
                put("modeId", a.modeId); put("isEnabled", a.isEnabled)
                put("presetBg", a.presetBg); put("customImageUri", a.customImageUri ?: "")
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("alarms", arr.toString()).apply()
    }

    fun loadSettings(ctx: Context): ZmanimSettings {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("settings", null) ?: return ZmanimSettings()
        val o = JSONObject(raw)
        val city = ISRAEL_CITIES.find { it.name == o.optString("city") } ?: ISRAEL_CITIES[0]
        return ZmanimSettings(
            city = city,
            minhag = ZmanimMinhag.valueOf(o.optString("minhag", ZmanimMinhag.SEPHARDI.name)),
            useElevation = o.optBoolean("elev", false),
            customAlot = CustomZmanRule(CalculationBasis.valueOf(o.optString("alotB", CalculationBasis.DEGREES.name)), o.optDouble("alotV", 16.1)),
            customTzeit = CustomZmanRule(CalculationBasis.valueOf(o.optString("tzeitB", CalculationBasis.DEGREES.name)), o.optDouble("tzeitV", 8.5)),
            themeMode = o.optString("theme", "system"),
            defaultRingtoneId = o.optString("ringId", "system"),
            customRingtoneUri = o.optString("ringUri", "").takeIf { it.isNotBlank() },
            widgetColorHex = o.optLong("wColor", 0xFFFFFFFFL),
            widgetAlpha = o.optDouble("wAlpha", 0.88).toFloat()
        )
    }

    fun saveSettings(ctx: Context, s: ZmanimSettings) {
        val o = JSONObject().apply {
            put("city", s.city.name); put("minhag", s.minhag.name); put("elev", s.useElevation)
            put("alotB", s.customAlot.basis.name); put("alotV", s.customAlot.value)
            put("tzeitB", s.customTzeit.basis.name); put("tzeitV", s.customTzeit.value)
            put("theme", s.themeMode); put("ringId", s.defaultRingtoneId)
            put("ringUri", s.customRingtoneUri ?: "")
            put("wColor", s.widgetColorHex); put("wAlpha", s.widgetAlpha.toDouble())
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("settings", o.toString()).apply()
    }

    fun loadTimerPresets(ctx: Context): List<Int> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tPresets", null)
            ?: return listOf(60, 180, 300, 600, 900, 1800)
        val arr = JSONArray(raw)
        return List(arr.length()) { arr.getInt(it) }
    }

    fun saveTimerPresets(ctx: Context, list: List<Int>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tPresets", JSONArray(list).toString()).apply()
    }

    fun loadWorldCities(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("worldCities", null)
            ?: return listOf("ny", "lon", "tok", "dxb")
        val arr = JSONArray(raw)
        return List(arr.length()) { arr.getString(it) }
    }

    fun saveWorldCities(ctx: Context, ids: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("worldCities", JSONArray(ids).toString()).apply()
    }

    fun saveWeather(ctx: Context, temp: String, cond: String, iconKey: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("w_temp", temp).putString("w_cond", cond).putString("w_icon", iconKey).apply()
    }

    fun loadWeather(ctx: Context): Triple<String, String, String> {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(sp.getString("w_temp", "--°") ?: "--°", sp.getString("w_cond", "מעדכן...") ?: "מעדכן...", sp.getString("w_icon", "clear") ?: "clear")
    }
}