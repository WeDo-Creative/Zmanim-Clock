package com.zmanim.alarm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class WorldCity(val id: String, val name: String, val tz: String, val lat: Double, val lon: Double)

val WORLD_CITIES = listOf(
    WorldCity("la", "לוס אנג׳לס", "America/Los_Angeles", 34.0, -118.2),
    WorldCity("ny", "ניו יורק", "America/New_York", 40.7, -74.0),
    WorldCity("ba", "בואנוס איירס", "America/Argentina/Buenos_Aires", -34.6, -58.4),
    WorldCity("lon", "לונדון", "Europe/London", 51.5, -0.1),
    WorldCity("par", "פריז", "Europe/Paris", 48.8, 2.3),
    WorldCity("jlm", "ירושלים", "Asia/Jerusalem", 31.8, 35.2),
    WorldCity("mos", "מוסקבה", "Europe/Moscow", 55.7, 37.6),
    WorldCity("dxb", "דובאי", "Asia/Dubai", 25.2, 55.3),
    WorldCity("bkk", "בנגקוק", "Asia/Bangkok", 13.7, 100.5),
    WorldCity("tok", "טוקיו", "Asia/Tokyo", 35.7, 139.7),
    WorldCity("syd", "סידני", "Australia/Sydney", -33.9, 151.2)
)

@Composable
fun WorldClockScreen(selectedIds: List<String>, onUpdateIds: (List<String>) -> Unit, onOpenSettings: () -> Unit) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCityPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { while (true) { nowMillis = System.currentTimeMillis(); delay(1000) } }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("שעון עולמי", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCityPicker = true }, shape = RoundedCornerShape(50)) { Text("רשימה", fontSize = 12.sp) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = null) }
                }
            }

            Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().height(175.dp)) {
                WorldDayNightMap(nowMillis = nowMillis, selectedIds = selectedIds)
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 84.dp)) {
                items(selectedIds) { cid ->
                    val c = WORLD_CITIES.find { it.id == cid } ?: return@items
                    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(c.tz) }
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(c.tz.substringAfter("/").replace("_", " "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(fmt.format(Date(nowMillis)), fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                IconButton(onClick = { onUpdateIds(selectedIds - cid) }) {
                                    Icon(Icons.Default.Close, contentDescription = "הסר", tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCityPicker = true }, shape = CircleShape,
            containerColor = Color(0xFF0D84FF), contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "הוסף עיר") }
    }

    if (showCityPicker) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCityPicker = false },
            title = { Text("בחירת עיר בעולם") },
            text = {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("חפש עיר...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(WORLD_CITIES.filter { query.isBlank() || it.name.contains(query) }) { c ->
                            val added = selectedIds.contains(c.id)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onUpdateIds(if (added) selectedIds - c.id else selectedIds + c.id)
                                }.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(c.name)
                                Text(if (added) "נוסף ✓" else "+ הוסף", color = Color(0xFF0D84FF))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCityPicker = false }) { Text("סגור") } }
        )
    }
}

@Composable
fun WorldDayNightMap(nowMillis: Long, selectedIds: List<String>) {
    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFFDCEAFB))) {
        val w = size.width; val h = size.height
        fun px(lon: Double) = ((lon + 180.0) / 360.0 * w).toFloat()
        fun py(lat: Double) = ((90.0 - lat) / 180.0 * h).toFloat()

        val landColor = Color(0xFFA8C6E8)
        val continents = listOf(
            listOf(-168.0 to 65.0, -135.0 to 69.0, -80.0 to 52.0, -56.0 to 52.0, -80.0 to 26.0, -96.0 to 19.0, -80.0 to 9.0, -110.0 to 23.0, -124.0 to 48.0),
            listOf(-78.0 to 9.0, -50.0 to 0.0, -35.0 to -6.0, -56.0 to -35.0, -68.0 to -55.0, -76.0 to -12.0),
            listOf(-9.0 to 38.0, 2.0 to 51.0, 10.0 to 60.0, 26.0 to 71.0, 40.0 to 65.0, 30.0 to 41.0, 12.0 to 42.0),
            listOf(-6.0 to 36.0, 32.0 to 31.0, 51.0 to 12.0, 31.0 to -30.0, 18.0 to -34.0, 9.0 to 0.0, -17.0 to 15.0),
            listOf(34.0 to 32.0, 45.0 to 13.0, 58.0 to 24.0, 77.0 to 8.0, 104.0 to 2.0, 121.0 to 31.0, 156.0 to 52.0, 170.0 to 66.0, 70.0 to 73.0, 40.0 to 65.0),
            listOf(114.0 to -22.0, 131.0 to -12.0, 143.0 to -11.0, 153.0 to -27.0, 145.0 to -38.0, 116.0 to -32.0)
        )
        continents.forEach { pts ->
            val p = Path().apply {
                moveTo(px(pts[0].first), py(pts[0].second))
                pts.drop(1).forEach { lineTo(px(it.first), py(it.second)) }
                close()
            }
            drawPath(p, landColor)
        }

        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMillis }
        val doy = utcCal.get(Calendar.DAY_OF_YEAR)
        val decl = (23.44 * sin((2 * PI / 365.0) * (doy - 81))).let { if (abs(it) < 0.2) 0.2 else it }
        val utcH = utcCal.get(Calendar.HOUR_OF_DAY) + utcCal.get(Calendar.MINUTE) / 60.0
        val subLon = (12.0 - utcH) * 15.0
        val tanDecl = tan(Math.toRadians(decl))
        val nightPath = Path().apply {
            val edgeY = if (decl >= 0) h else 0f
            moveTo(0f, edgeY)
            for (lon in -180..180 step 5) {
                val lat = Math.toDegrees(atan(-cos(Math.toRadians(lon - subLon)) / tanDecl))
                lineTo(px(lon.toDouble()), py(lat))
            }
            lineTo(w, edgeY); close()
        }
        drawPath(nightPath, Color(0x700A1630))

        WORLD_CITIES.forEach { c ->
            val sel = selectedIds.contains(c.id)
            drawCircle(Color.White, radius = if (sel) 12f else 9f, center = Offset(px(c.lon), py(c.lat)))
            drawCircle(if (sel) Color(0xFF0D84FF) else Color(0xFF9CA3AF), radius = if (sel) 9f else 6.5f, center = Offset(px(c.lon), py(c.lat)))
        }
    }
}

@Composable
fun StopwatchScreen() {
    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var laps by remember { mutableStateOf(listOf<Long>()) }

    LaunchedEffect(running) {
        var last = System.currentTimeMillis()
        while (running) {
            delay(30)
            val now = System.currentTimeMillis()
            elapsedMs += now - last; last = now
        }
    }

    fun fmt(ms: Long): String {
        val m = ms / 60000; val s = (ms % 60000) / 1000; val cs = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(m, s, cs)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("סטופר", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(28.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(fmt(elapsedMs), fontSize = 52.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(laps.indices.toList()) { idx ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFF0D84FF), modifier = Modifier.size(16.dp))
                        Text("הקפה ${laps.size - idx}")
                    }
                    Text(fmt(laps[idx]))
                }
                HorizontalDivider()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(vertical = 14.dp)) {
            FilledTonalIconButton(onClick = { if (running) laps = listOf(elapsedMs) + laps else { elapsedMs = 0L; laps = emptyList() } }, modifier = Modifier.size(62.dp)) {
                Icon(if (running) Icons.Default.Flag else Icons.Default.Refresh, contentDescription = null)
            }
            FilledIconButton(
                onClick = { running = !running },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (running) Color(0xFFEF4444) else Color(0xFF0D84FF), contentColor = Color.White),
                modifier = Modifier.size(62.dp)
            ) { Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    var presets by remember { mutableStateOf(AppStorage.loadTimerPresets(ctx)) }
    var h by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(5) }
    var s by remember { mutableIntStateOf(0) }

    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var totalSec by remember { mutableIntStateOf(300) }
    var remSec by remember { mutableIntStateOf(300) }

    LaunchedEffect(running, paused) {
        while (running && !paused && remSec > 0) {
            delay(1000); remSec--
            if (remSec <= 0) running = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Text("טיימר", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))

        if (!running) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberWheelPicker(0..23, h, { "%02d".format(it) }) { h = it }
                        Text(":", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        NumberWheelPicker(0..59, m, { "%02d".format(it) }) { m = it }
                        Text(":", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        NumberWheelPicker(0..59, s, { "%02d".format(it) }) { s = it }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("קיצורי זמן (לחיצה ארוכה שומרת זמן נוכחי):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                presets.chunked(3).forEachIndexed { rowIdx, row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { colIdx, sec ->
                            val idx = rowIdx * 3 + colIdx
                            val lbl = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60) else if (sec % 60 == 0) "${sec / 60} דק׳" else "%d:%02d".format(sec / 60, sec % 60)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp).combinedClickable(
                                    onClick = { h = sec / 3600; m = (sec % 3600) / 60; s = sec % 60 },
                                    onLongClick = {
                                        val cur = h * 3600 + m * 60 + s
                                        if (cur > 0) {
                                            presets = presets.toMutableList().apply { set(idx, cur) }
                                            AppStorage.saveTimerPresets(ctx, presets)
                                        }
                                    }
                                )
                            ) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) { Text(lbl, fontWeight = FontWeight.SemiBold) } }
                        }
                    }
                }
            }

            FilledIconButton(
                onClick = { val tot = h * 3600 + m * 60 + s; if (tot > 0) { totalSec = tot; remSec = tot; paused = false; running = true } },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0D84FF), contentColor = Color.White),
                modifier = Modifier.size(64.dp)
            ) { Icon(Icons.Default.PlayArrow, contentDescription = "התחל") }
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(Color.Gray.copy(0.2f), 0f, 360f, false, style = Stroke(18f))
                    drawArc(Color(0xFF0D84FF), -90f, 360f * (remSec.toFloat() / totalSec.coerceAtLeast(1)), false, style = Stroke(18f, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:%02d".format(remSec / 60, remSec % 60), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { remSec += 60; totalSec += 60 }) { Text("+1 דקה") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                FilledTonalIconButton(onClick = { running = false }, modifier = Modifier.size(62.dp)) { Icon(Icons.Default.Stop, contentDescription = null) }
                FilledIconButton(onClick = { paused = !paused }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0D84FF), contentColor = Color.White), modifier = Modifier.size(62.dp)) {
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                }
            }
        }
    }
}