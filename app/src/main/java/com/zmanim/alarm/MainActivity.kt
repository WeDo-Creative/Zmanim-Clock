package com.zmanim.alarm

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WeatherSyncWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        setContent { MainAppRoot() }
    }
}

@Composable
fun MainAppRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppStorage.loadSettings(ctx)) }
    var modes by remember { mutableStateOf(AppStorage.loadModes(ctx)) }
    var alarms by remember { mutableStateOf(AppStorage.loadAlarms(ctx)) }
    var worldIds by remember { mutableStateOf(AppStorage.loadWorldCities(ctx)) }

    val sysDark = isSystemInDarkTheme()
    val isDark = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> sysDark
    }
    val colors = if (isDark) darkColorScheme(background = Color.Black, surface = Color(0xFF181B20), primary = Color(0xFF2593FF))
                 else lightColorScheme(background = Color(0xFFF6F7F9), surface = Color.White, primary = Color(0xFF0D84FF))

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    fun saveAll(newAlarms: List<AlarmEntity> = alarms, newModes: List<AlarmMode> = modes) {
        alarms = newAlarms; modes = newModes
        AppStorage.saveAlarms(ctx, newAlarms)
        AppStorage.saveModes(ctx, newModes)
        AlarmScheduler.rescheduleAll(ctx)
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val items = listOf(
                        Triple(0, "שעון מעורר", Icons.Default.Alarm),
                        Triple(1, "עולמי", Icons.Default.Public),
                        Triple(2, "סטופר", Icons.Default.Timer),
                        Triple(3, "טיימר", Icons.Default.HourglassBottom)
                    )
                    items.forEach { (idx, title, ico) ->
                        NavigationBarItem(selected = selectedTab == idx, onClick = { selectedTab = idx }, icon = { Icon(ico, null) }, label = { Text(title, fontSize = 11.sp) })
                    }
                }
            }
        ) { pad ->
            Box(modifier = Modifier.fillMaxSize().padding(pad).background(MaterialTheme.colorScheme.background)) {
                when (selectedTab) {
                    0 -> AlarmsTab(
                        modes = modes, alarms = alarms, settings = settings,
                        onUpdateModes = { saveAll(modes = it) },
                        onUpdateAlarms = { saveAll(newAlarms = it) },
                        onEditAlarm = { editingAlarm = it },
                        onOpenSettings = { showSettings = true }
                    )
                    1 -> WorldClockScreen(worldIds, { worldIds = it; AppStorage.saveWorldCities(ctx, it) }, { showSettings = true })
                    2 -> StopwatchScreen()
                    3 -> TimerScreen()
                }
            }

            editingAlarm?.let { alarm ->
                FullScreenAlarmEditor(
                    initial = alarm, modes = modes, settings = settings,
                    isNew = alarms.none { it.id == alarm.id },
                    onDismiss = { editingAlarm = null },
                    onSave = { saved ->
                        val updated = if (alarms.any { it.id == saved.id }) alarms.map { if (it.id == saved.id) saved else it } else alarms + saved
                        saveAll(newAlarms = updated); editingAlarm = null
                    },
                    onDelete = { saveAll(newAlarms = alarms.filterNot { it.id == alarm.id }); editingAlarm = null }
                )
            }

            if (showSettings) {
                FullScreenSettings(
                    initial = settings,
                    onDismiss = { showSettings = false },
                    onSave = { newS ->
                        settings = newS; AppStorage.saveSettings(ctx, newS)
                        AlarmScheduler.rescheduleAll(ctx)
                        scope.launch { ClockWidget().updateAll(ctx) }
                        showSettings = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmsTab(
    modes: List<AlarmMode>, alarms: List<AlarmEntity>, settings: ZmanimSettings,
    onUpdateModes: (List<AlarmMode>) -> Unit, onUpdateAlarms: (List<AlarmEntity>) -> Unit,
    onEditAlarm: (AlarmEntity) -> Unit, onOpenSettings: () -> Unit
) {
    val ctx = LocalContext.current
    var filterModeId by remember { mutableStateOf<String?>(null) }
    var showModesDialog by remember { mutableStateOf(false) }
    var deleteModeTarget by remember { mutableStateOf<AlarmMode?>(null) }

    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val today = remember { Calendar.getInstance() }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("שעון מעורר", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "הגדרות") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                Surface(
                    shape = CircleShape, border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(34.dp).clickable { showModesDialog = true }
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) } }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                    items(modes, key = { it.id }) { m ->
                        val selected = filterModeId == m.id
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier.combinedClickable(
                                onClick = { filterModeId = if (selected) null else m.id },
                                onLongClick = { if (modes.size > 1) deleteModeTarget = m }
                            )
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(m.name, fontSize = 13.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                Icon(
                                    if (m.isActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (m.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp).clickable {
                                        onUpdateModes(modes.map { if (it.id == m.id) it.copy(isActive = !it.isActive) else it })
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val visible = if (filterModeId == null) alarms else alarms.filter { it.modeId == filterModeId }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 84.dp)) {
                items(visible, key = { it.id }) { a ->
                    val mode = modes.find { it.id == a.modeId }
                    val effective = a.isEnabled && (mode?.isActive ?: true)
                    val timeStr = if (a.isRelative) ZmanimEngine.calculateZmanTime(today, a.zmanEvent, a.offsetMinutes, settings)?.let { fmt.format(it.time) } ?: "--:--"
                                  else "%02d:%02d".format(a.fixedHour, a.fixedMinute)

                    Surface(
                        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().alpha(if (effective) 1f else 0.45f).clickable { onEditAlarm(a) }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(timeStr, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                                    if (a.isRelative) {
                                        val sign = if (a.offsetMinutes > 0) "+${a.offsetMinutes}׳" else if (a.offsetMinutes < 0) "${a.offsetMinutes}׳" else ""
                                        Surface(color = Color(0xFFFFF4E5), shape = RoundedCornerShape(6.dp)) {
                                            Text("${a.zmanEvent.title} $sign".trim(), fontSize = 11.sp, color = Color(0xFFB36B00), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    if (a.presetBg != "none" || !a.customImageUri.isNullOrBlank()) {
                                        Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    }
                                }
                                val daysStr = if (a.daysOfWeek.size == 7) "כל יום" else a.daysOfWeek.sorted().map { listOf("","א׳","ב׳","ג׳","ד׳","ה׳","ו׳","ש׳")[it] }.joinToString(" ")
                                Text("${a.label}  |  $daysStr  |  ${mode?.name ?: ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(onClick = {
                                    ctx.startActivity(Intent(ctx, RingingActivity::class.java).apply {
                                        putExtra("LABEL", a.label); putExtra("BG", a.presetBg); putExtra("URI", a.customImageUri)
                                    })
                                }) { Icon(Icons.Default.Notifications, null, modifier = Modifier.size(18.dp)) }
                                Switch(checked = a.isEnabled, onCheckedChange = { en -> onUpdateAlarms(alarms.map { if (it.id == a.id) it.copy(isEnabled = en) else it }) })
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onEditAlarm(AlarmEntity(id = UUID.randomUUID().toString(), label = "", isRelative = true, modeId = modes.firstOrNull()?.id ?: "routine")) },
            shape = CircleShape, containerColor = Color(0xFF0D84FF), contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "הוסף שעון") }
    }

    if (showModesDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showModesDialog = false },
            title = { Text("ניהול פילטרים") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("פילטר חדש...") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            if (newName.isNotBlank()) { onUpdateModes(modes + AlarmMode("m_${System.currentTimeMillis()}", newName.trim(), true)); newName = "" }
                        }) { Text("הוסף") }
                    }
                    modes.forEach { m ->
                        var edited by remember(m.id, m.name) { mutableStateOf(m.name) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = edited,
                                onValueChange = { edited = it; if (it.isNotBlank()) onUpdateModes(modes.map { x -> if (x.id == m.id) x.copy(name = it) else x }) },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { if (modes.size > 1) onUpdateModes(modes.filterNot { it.id == m.id }) }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModesDialog = false }) { Text("סגור") } }
        )
    }

    deleteModeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteModeTarget = null },
            title = { Text("מחיקת פילטר") },
            text = { Text("למחוק את הפילטר \"${target.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    val remaining = modes.filterNot { it.id == target.id }
                    val fallback = remaining.first().id
                    onUpdateAlarms(alarms.map { if (it.modeId == target.id) it.copy(modeId = fallback) else it })
                    onUpdateModes(remaining)
                    if (filterModeId == target.id) filterModeId = null
                    deleteModeTarget = null
                }) { Text("מחק", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { deleteModeTarget = null }) { Text("ביטול") } }
        )
    }
}

@Composable
fun NumberWheelPicker(range: IntRange, selected: Int, format: (Int) -> String, onSelect: (Int) -> Unit) {
    val list = remember(range) { range.toList() }
    val initIdx = (list.indexOf(selected)).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initIdx)
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    var showTypeDialog by remember { mutableStateOf(false) }

    val centerVal by remember { derivedStateOf { list.getOrElse(state.firstVisibleItemIndex) { selected } } }
    LaunchedEffect(centerVal) { onSelect(centerVal) }

    LazyColumn(
        state = state, flingBehavior = fling,
        contentPadding = PaddingValues(vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(86.dp).height(132.dp)
    ) {
        items(list.size) { i ->
            val v = list[i]
            val active = v == centerVal
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(44.dp).fillMaxWidth().clickable { if (active) showTypeDialog = true }
            ) {
                Text(format(v), fontSize = if (active) 22.sp else 17.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) Color(0xFF0D84FF) else Color.Gray, maxLines = 1)
            }
        }
    }

    if (showTypeDialog) {
        var typed by remember { mutableStateOf(selected.toString()) }
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text("הקלד מספר") },
            text = { OutlinedTextField(value = typed, onValueChange = { typed = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val num = typed.toIntOrNull()?.coerceIn(range.first, range.last)
                    if (num != null) {
                        onSelect(num)
                        val idx = list.indexOf(num)
                        if (idx >= 0) scope.launch { state.scrollToItem(idx) }
                    }
                    showTypeDialog = false
                }) { Text("אישור") }
            }
        )
    }
}

@Composable
fun FullScreenAlarmEditor(
    initial: AlarmEntity, modes: List<AlarmMode>, settings: ZmanimSettings,
    isNew: Boolean, onDismiss: () -> Unit, onSave: (AlarmEntity) -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    var draft by remember { mutableStateOf(initial) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            draft = draft.copy(presetBg = "custom", customImageUri = uri.toString())
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                Text(if (isNew) "שעון חדש" else "עריכת שעון", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onSave(draft.copy(label = draft.label.ifBlank { "שעון מעורר" })) }) { Icon(Icons.Default.Check, null, tint = Color(0xFF0D84FF)) }
            }

            OutlinedTextField(value = draft.label, onValueChange = { draft = draft.copy(label = it) }, label = { Text("שם השעון") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(modes) { m -> FilterChip(selected = draft.modeId == m.id, onClick = { draft = draft.copy(modeId = m.id) }, label = { Text(m.name) }) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !draft.isRelative, onClick = { draft = draft.copy(isRelative = false) }, label = { Text("שעה קבועה") }, modifier = Modifier.weight(1f))
                FilterChip(selected = draft.isRelative, onClick = { draft = draft.copy(isRelative = true) }, label = { Text("יחסי לזמני היום") }, modifier = Modifier.weight(1f))
            }

            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                if (!draft.isRelative) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            NumberWheelPicker(0..23, draft.fixedHour, { "%02d".format(it) }) { draft = draft.copy(fixedHour = it) }
                            Text(":", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            NumberWheelPicker(0..59, draft.fixedMinute, { "%02d".format(it) }) { draft = draft.copy(fixedMinute = it) }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        val evs = ZmanEvent.entries
                        NumberWheelPicker(0 until evs.size, evs.indexOf(draft.zmanEvent), { evs[it].title }) { draft = draft.copy(zmanEvent = evs[it]) }
                        NumberWheelPicker(-90..90, draft.offsetMinutes, { if (it == 0) "בדיוק" else if (it < 0) "${-it} לפני" else "$it אחרי" }) { draft = draft.copy(offsetMinutes = it) }
                    }
                }
            }

            val dayBounds = remember { mutableMapOf<Int, Rect>() }
            var dragTarget by remember { mutableStateOf(true) }
            val visited = remember { mutableSetOf<Int>() }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            visited.clear()
                            val hit = dayBounds.entries.firstOrNull { it.value.contains(pos) }?.key ?: return@detectDragGestures
                            dragTarget = !draft.daysOfWeek.contains(hit)
                            visited.add(hit)
                            draft = draft.copy(daysOfWeek = if (dragTarget) draft.daysOfWeek + hit else draft.daysOfWeek - hit)
                        },
                        onDrag = { change, _ ->
                            val hit = dayBounds.entries.firstOrNull { it.value.contains(change.position) }?.key ?: return@detectDragGestures
                            if (visited.add(hit)) {
                                draft = draft.copy(daysOfWeek = if (dragTarget) draft.daysOfWeek + hit else draft.daysOfWeek - hit)
                            }
                        }
                    )
                }
            ) {
                listOf("א","ב","ג","ד","ה","ו","ש").forEachIndexed { idx, lbl ->
                    val d = idx + 1; val act = draft.daysOfWeek.contains(d)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(if (act) Color(0xFF0D84FF) else MaterialTheme.colorScheme.surface)
                            .onGloballyPositioned { dayBounds[d] = it.boundsInParent() }
                            .clickable { draft = draft.copy(daysOfWeek = if (act) draft.daysOfWeek - d else draft.daysOfWeek + d) }
                    ) { Text(lbl, color = if (act) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
                }
            }

            val bgs = listOf("goodmorning" to "בוקר טוב", "prayer" to "תפילה", "sport" to "ספורט", "work" to "עבודה", "food" to "אוכל", "goodnight" to "לילה טוב")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(bgs) { (k, title) ->
                    FilterChip(selected = draft.presetBg == k, onClick = { draft = draft.copy(presetBg = k, customImageUri = null) }, label = { Text(title, fontSize = 12.sp) })
                }
                item { FilterChip(selected = draft.presetBg == "custom", onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, label = { Text("מהמכשיר", fontSize = 12.sp) }) }
                item { FilterChip(selected = draft.presetBg == "none", onClick = { draft = draft.copy(presetBg = "none", customImageUri = null) }, label = { Text("ללא", fontSize = 12.sp) }) }
            }

            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(ctx, RingingActivity::class.java).apply {
                        putExtra("LABEL", draft.label.ifBlank { "ניסוי צלצול" }); putExtra("BG", draft.presetBg); putExtra("URI", draft.customImageUri)
                    })
                }) { Text("ניסוי צלצול") }
                if (!isNew) TextButton(onClick = onDelete) { Text("מחק שעון", color = Color.Red) }
            }
        }
    }
}

@Composable
fun FullScreenSettings(initial: ZmanimSettings, onDismiss: () -> Unit, onSave: (ZmanimSettings) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) draft = draft.copy(customRingtoneUri = uri.toString())
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                Text("הגדרות וזמנים", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onSave(draft) }) { Icon(Icons.Default.Check, null, tint = Color(0xFF0D84FF)) }
            }

            Text("מראה", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("light" to "בהיר", "dark" to "כהה", "system" to "מערכת").forEach { (k, lbl) ->
                    FilterChip(selected = draft.themeMode == k, onClick = { draft = draft.copy(themeMode = k) }, label = { Text(lbl) }, modifier = Modifier.weight(1f))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("צלצול ברירת מחדל", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                TextButton(onClick = { audioPicker.launch("audio/*") }) { Text(if (draft.customRingtoneUri == null) "בחר קובץ צלצול" else "צלצול מותאם ✓") }
            }

            Text("מיקום בארץ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            ISRAEL_CITIES.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { c ->
                        FilterChip(selected = draft.city.name == c.name, onClick = { draft = draft.copy(city = c) }, label = { Text(c.name, fontSize = 11.sp, maxLines = 1) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            Text("שיטת חישוב זמני היום", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            ZmanimMinhag.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { m ->
                        FilterChip(selected = draft.minhag == m, onClick = { draft = draft.copy(minhag = m) }, label = { Text(m.displayName, fontSize = 11.sp, maxLines = 1) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("התחשבות בגובה טופוגרפי")
                Switch(checked = draft.useElevation, onCheckedChange = { draft = draft.copy(useElevation = it) })
            }

            Text("עיצוב הווידג'ט (צבע ושקיפות)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFFFFFFFFL, 0xFF111827L, 0xFF0D84FFL, 0xFFF5EBDCL, 0xFF14532DL, 0xFF581C87L).forEach { hex ->
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(hex)).border(2.dp, if (draft.widgetColorHex == hex) Color(0xFF0D84FF) else Color.Gray, CircleShape).clickable { draft = draft.copy(widgetColorHex = hex) })
                }
            }
            Slider(value = draft.widgetAlpha, onValueChange = { draft = draft.copy(widgetAlpha = it) }, valueRange = 0f..1f)
        }
    }
}