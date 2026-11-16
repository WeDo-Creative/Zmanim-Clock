package com.zmanim.alarm

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()
}

class ClockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = AppStorage.loadSettings(context)
        val (temp, cond, _) = AppStorage.loadWeather(context)
        val hebDate = ZmanimEngine.getHebrewDateLetters()
        val gregDate = SimpleDateFormat("EEEE, d בMMMM", Locale("he")).format(Date())
        val bgColor = Color(settings.widgetColorHex).copy(alpha = settings.widgetAlpha)
        val isDarkBg = settings.widgetAlpha < 0.25f ||
            settings.widgetColorHex == 0xFF111827L ||
            settings.widgetColorHex == 0xFF0D84FFL ||
            settings.widgetColorHex == 0xFF14532DL ||
            settings.widgetColorHex == 0xFF581C87L
        val textColor = if (isDarkBg) Color.White else Color(0xFF111827)

        provideContent {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(bgColor))
                    .cornerRadius(22.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    AndroidRemoteViews(
                        RemoteViews(context.packageName, R.layout.widget_text_clock).apply {
                            setTextColor(R.id.widgetTextClock, if (isDarkBg) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#111827"))
                        }
                    )
                    Text(hebDate, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ColorProvider(textColor)))
                    Text(gregDate, style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor.copy(alpha = 0.8f))))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(temp, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Color(0xFF0D84FF))))
                    Text(cond, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ColorProvider(textColor)))
                    Text(settings.city.name, style = TextStyle(fontSize = 11.sp, color = ColorProvider(textColor.copy(alpha = 0.75f))))
                }
            }
        }
    }
}

class WeatherSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val s = AppStorage.loadSettings(applicationContext)
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${s.city.lat}&longitude=${s.city.lon}&current_weather=true")
            val body = (url.openConnection() as HttpURLConnection).inputStream.bufferedReader().readText()
            val cw = JSONObject(body).getJSONObject("current_weather")
            val temp = "${cw.getDouble("temperature").toInt()}°"
            val code = cw.getInt("weathercode")
            val cond = when {
                code == 0 -> "בהיר"
                code in 1..2 -> "מעונן חלקית"
                code == 3 -> "מעונן"
                code == 45 || code == 48 -> "אביך / ערפל"
                code in 51..67 || code in 80..82 || code >= 95 -> "גשום"
                else -> "נאה"
            }
            AppStorage.saveWeather(applicationContext, temp, cond, "clear")
            ClockWidget().updateAll(applicationContext)
            AlarmScheduler.rescheduleAll(applicationContext)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WeatherSyncWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("weather_zmanim_sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}