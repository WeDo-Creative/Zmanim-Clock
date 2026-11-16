package com.zmanim.alarm

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

object AlarmScheduler {
    fun calculateNextTrigger(alarm: AlarmEntity, modes: List<AlarmMode>, settings: ZmanimSettings): Calendar? {
        val modeActive = modes.find { it.id == alarm.modeId }?.isActive ?: true
        if (!alarm.isEnabled || !modeActive) return null
        val now = Calendar.getInstance()
        for (dayOffset in 0..7) {
            val cand = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val dow = cand.get(Calendar.DAY_OF_WEEK)
            if (alarm.daysOfWeek.isNotEmpty() && !alarm.daysOfWeek.contains(dow)) continue
            val trig = if (alarm.isRelative) {
                ZmanimEngine.calculateZmanTime(cand, alarm.zmanEvent, alarm.offsetMinutes, settings)
            } else {
                (cand.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, alarm.fixedHour)
                    set(Calendar.MINUTE, alarm.fixedMinute)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
            }
            if (trig != null && trig.after(now)) return trig
        }
        return null
    }

    fun rescheduleAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val settings = AppStorage.loadSettings(ctx)
        val modes = AppStorage.loadModes(ctx)
        val alarms = AppStorage.loadAlarms(ctx)

        alarms.forEach { a ->
            val i = Intent(ctx, AlarmReceiver::class.java).apply {
                putExtra("ID", a.id); putExtra("LABEL", a.label)
                putExtra("BG", a.presetBg); putExtra("URI", a.customImageUri)
            }
            val pi = PendingIntent.getBroadcast(ctx, a.id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.cancel(pi)
            val trig = calculateNextTrigger(a, modes, settings) ?: return@forEach
            val showPi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(trig.timeInMillis, showPi), pi)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.rescheduleAll(ctx); return
        }
        val id = intent.getStringExtra("ID") ?: "0"
        val label = intent.getStringExtra("LABEL") ?: "שעון מעורר"
        val bg = intent.getStringExtra("BG") ?: "none"
        val uri = intent.getStringExtra("URI")

        val full = Intent(ctx, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("LABEL", label); putExtra("BG", bg); putExtra("URI", uri)
        }
        val fullPi = PendingIntent.getActivity(ctx, id.hashCode(), full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chId = "zmanim_alarm_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(chId, "שעון מעורר", NotificationManager.IMPORTANCE_HIGH).apply { setBypassDnd(true) })
        }
        val notif = NotificationCompat.Builder(ctx, chId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label).setContentText("לחץ לעצירת השעון")
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullPi, true).setAutoCancel(true).build()
        nm.notify(id.hashCode(), notif)
        ctx.startActivity(full)
        AlarmScheduler.rescheduleAll(ctx)
    }
}

class RingingActivity : ComponentActivity() {
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        }
        val label = intent.getStringExtra("LABEL") ?: "שעון מעורר"
        val bg = intent.getStringExtra("BG") ?: "none"
        val uri = intent.getStringExtra("URI")
        startSound()

        setContent {
            RingingScreen(
                label = label, presetBg = bg, customUri = uri,
                onSnooze = {
                    stopSound()
                    val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val i = Intent(this, AlarmReceiver::class.java).apply {
                        putExtra("LABEL", "$label (נודניק)"); putExtra("BG", bg); putExtra("URI", uri)
                    }
                    val pi = PendingIntent.getBroadcast(this, 8888, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5 * 60_000L, pi)
                    finish()
                },
                onDismiss = { stopSound(); finish() }
            )
        }
    }

    private fun startSound() {
        try {
            val s = AppStorage.loadSettings(this)
            val uri = s.customRingtoneUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setDataSource(this@RingingActivity, uri)
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                isLooping = true; prepare(); start()
            }
        } catch (_: Exception) {}
    }

    private fun stopSound() { player?.stop(); player?.release(); player = null }
    override fun onDestroy() { stopSound(); super.onDestroy() }
}

@Composable
fun RingingScreen(label: String, presetBg: String, customUri: String?, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        if (!customUri.isNullOrBlank()) {
            AsyncImage(model = Uri.parse(customUri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            PresetBackgroundArt(presetBg)
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.35f), Color.Black.copy(0.65f)))))

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 56.dp)) {
                Text(timeStr, fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(label, fontSize = 24.sp, color = Color.White.copy(0.92f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Button(onClick = onSnooze, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.24f)), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("נודניק (5 דקות)", fontSize = 18.sp, color = Color.White)
                }
                Button(onClick = onDismiss, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)), modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text("עצור שעון", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PresetBackgroundArt(key: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        when (key) {
            "prayer" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1423), Color(0xFF2C1E16), Color(0xFF140E0A))))
                drawRect(Color(0xFF1E3A8A), topLeft = Offset(w * 0.08f, h * 0.16f), size = Size(w * 0.18f, h * 0.28f))
                drawRect(Color(0xFF1E3A8A), topLeft = Offset(w * 0.74f, h * 0.16f), size = Size(w * 0.18f, h * 0.28f))
                drawRect(Color(0xFF451A03), topLeft = Offset(w * 0.32f, h * 0.16f), size = Size(w * 0.36f, h * 0.40f))
                drawRect(Color(0xFF7F1D1D), topLeft = Offset(w * 0.35f, h * 0.19f), size = Size(w * 0.30f, h * 0.34f))
                drawCircle(Color(0xFFFEF08A), radius = w * 0.025f, center = Offset(w * 0.50f, h * 0.12f))
                drawRect(Color(0xFF3B1E08), topLeft = Offset(w * 0.38f, h * 0.58f), size = Size(w * 0.24f, h * 0.20f))
                drawOval(Color(0xFF090D16), topLeft = Offset(w * 0.38f, h * 0.43f), size = Size(w * 0.20f, h * 0.03f))
                drawRect(Color(0xFF111827), topLeft = Offset(w * 0.42f, h * 0.40f), size = Size(w * 0.12f, h * 0.04f))
                drawRect(Color(0xFF0F172A), topLeft = Offset(w * 0.40f, h * 0.48f), size = Size(w * 0.16f, h * 0.26f))
                val tallit = Path().apply {
                    moveTo(w * 0.41f, h * 0.48f); lineTo(w * 0.55f, h * 0.48f)
                    lineTo(w * 0.60f, h * 0.68f); lineTo(w * 0.36f, h * 0.68f); close()
                }
                drawPath(tallit, Color(0xFFF8FAFC))
                drawLine(Color(0xFF1E293B), Offset(w * 0.38f, h * 0.64f), Offset(w * 0.58f, h * 0.64f), strokeWidth = 10f)
            }
            "goodmorning" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF1E3A8A), Color(0xFFF97316), Color(0xFFFDE047))))
                drawCircle(Color(0xFFFFFBEB), radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.60f))
            }
            "sport" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF064E3B), Color(0xFF10B981))))
                drawRect(Color(0xFFB91C1C), topLeft = Offset(0f, h * 0.62f), size = Size(w, h * 0.38f))
                drawCircle(Color.White, radius = w * 0.05f, center = Offset(w * 0.54f, h * 0.45f))
                drawLine(Color.White, Offset(w * 0.52f, h * 0.50f), Offset(w * 0.46f, h * 0.64f), strokeWidth = 24f, cap = StrokeCap.Round)
            }
            "work" -> drawRect(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF334155))))
            "food" -> drawRect(Brush.verticalGradient(listOf(Color(0xFF7C2D12), Color(0xFFEA580C))))
            "goodnight" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF312E81))))
                drawCircle(Color(0xFFFEF08A), radius = w * 0.14f, center = Offset(w * 0.72f, h * 0.24f))
            }
            else -> drawRect(Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))))
        }
    }
}