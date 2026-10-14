package com.ezanvakti

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EzanTheme { App() } }
    }
}

@Composable
fun EzanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4FD1C5), onPrimary = Color(0xFF00201D),
            secondary = Color(0xFFFFC857),
            background = Color(0xFF0B1F2E), onBackground = Color.White,
            surface = Color(0xFF12303F), onSurface = Color.White,
            surfaceVariant = Color(0xFF1B4152), onSurfaceVariant = Color(0xFFB7CBD6)
        ),
        content = content
    )
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var dataVer by remember { mutableIntStateOf(0) }
    var city by remember { mutableStateOf(Prefs.city(ctx)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Scheduler.refreshAsync(ctx)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(city) {
        loading = true
        val ok = withContext(Dispatchers.IO) {
            Repo.ensure(ctx)
            Scheduler.refresh(ctx)
            Repo.times(ctx, LocalDate.now(Repo.zone)) != null
        }
        error = !ok
        loading = false
        dataVer++
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("🕌", fontSize = 20.sp) }, label = { Text("Vakitler") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("⚙️", fontSize = 20.sp) }, label = { Text("Ayarlar") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) HomeScreen(city, dataVer, loading, error)
            else SettingsScreen(city) { city = it; Prefs.setCity(ctx, it) }
        }
    }
}

private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(city: String, dataVer: Int, loading: Boolean, error: Boolean) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(ZonedDateTime.now(Repo.zone)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now(Repo.zone)
            delay(1000)
        }
    }
    var ver by remember { mutableIntStateOf(0) }
    val next = remember(now, dataVer, city) { Repo.nextPrayer(ctx, now) }
    val times = remember(now.toLocalDate(), dataVer, city) { Repo.times(ctx, now.toLocalDate()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1E5B6E), Color(0xFF12303F))),
                    RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Text("📍 $city", color = Color(0xFFB7CBD6), fontSize = 14.sp)
                Text(
                    now.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale("tr"))),
                    color = Color(0xFFB7CBD6), fontSize = 13.sp
                )
                Spacer(Modifier.height(18.dp))
                if (next != null) {
                    Text("Sıradaki vakit", color = Color(0xFFB7CBD6), fontSize = 13.sp)
                    Text(
                        "${Prefs.NAMES[next.index]}  ${next.at.format(hm)}",
                        color = MaterialTheme.colorScheme.secondary, fontSize = 30.sp, fontWeight = FontWeight.Bold
                    )
                    val s = Duration.between(now, next.at).seconds.coerceAtLeast(0)
                    Text(
                        "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60),
                        color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light
                    )
                } else {
                    Text(
                        when {
                            loading -> "Vakitler yükleniyor…"
                            error -> "Vakitler alınamadı. İnternet bağlantınızı kontrol edin."
                            else -> "Veri yok"
                        },
                        color = Color.White, fontSize = 18.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        for (i in 0..5) {
            val isNext = next?.index == i && next.at.toLocalDate() == now.toLocalDate()
            val on = remember(ver) { Prefs.notify(ctx, i) }
            val snd = remember(ver) { Prefs.sound(ctx, i) }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isNext) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Prefs.NAMES[i], modifier = Modifier.weight(1f), fontSize = 18.sp,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        color = if (isNext) MaterialTheme.colorScheme.secondary else Color.White
                    )
                    Text(times?.get(i)?.format(hm) ?: "--:--", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (on) "🔔" else "🔕", fontSize = 22.sp,
                        modifier = Modifier.clickable { Prefs.setNotify(ctx, i, !on); ver++ }.padding(8.dp)
                    )
                    Text(
                        if (snd) "🔊" else "🔇", fontSize = 22.sp,
                        modifier = Modifier.alpha(if (on) 1f else 0.3f)
                            .clickable(enabled = on) { Prefs.setSound(ctx, i, !snd); ver++ }.padding(8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "🔔 vakitte bildirim   🔊 vakitte ezan sesi",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
    }
}

@Composable
fun SettingsScreen(city: String, onCity: (String) -> Unit) {
    val ctx = LocalContext.current
    var persist by remember { mutableStateOf(Prefs.persistent(ctx)) }
    var hasFile by remember { mutableStateOf(File(ctx.filesDir, "ezan.mp3").exists()) }
    var menu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    File(ctx.filesDir, "ezan.mp3").outputStream().use { input.copyTo(it) }
                }
                hasFile = true
            } catch (_: Exception) {
            }
        }
    }

    val am = ctx.getSystemService(AlarmManager::class.java)
    val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ayarlar", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SectionCard("Şehir") {
            Box {
                OutlinedButton(onClick = { menu = true }) { Text("$city  ▾") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Cities.ALL.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { menu = false; onCity(it) })
                    }
                }
            }
            Text(
                "Vakitler Diyanet İşleri Başkanlığı hesaplama yöntemiyle şehir merkezi için alınır.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
            )
        }

        SectionCard("Kilit ekranı") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kalıcı vakit bildirimi", fontSize = 16.sp)
                    Text(
                        "Sıradaki vakti geri sayımla kilit ekranında ve bildirim panelinde gösterir.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                    )
                }
                Switch(checked = persist, onCheckedChange = {
                    persist = it; Prefs.setPersistent(ctx, it); Scheduler.refreshAsync(ctx)
                })
            }
        }

        SectionCard("Ezan sesi") {
            Text(
                if (hasFile) "Seçili ses: kendi dosyanız" else "Seçili ses: varsayılan sistem sesi (ezan dosyası seçmediniz)",
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Ses seç") }
                if (hasFile) OutlinedButton(onClick = {
                    File(ctx.filesDir, "ezan.mp3").delete(); hasFile = false
                }) { Text("Sıfırla") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    ctx.startForegroundService(Intent(ctx, AdhanService::class.java).putExtra("idx", 2))
                }) { Text("Sesi dene") }
                OutlinedButton(onClick = { Notifier.alert(ctx, 2) }) { Text("Bildirimi dene") }
            }
        }

        if (!exactOk) {
            SectionCard("İzin gerekli") {
                Text("Vakitte tam zamanında bildirim için \"Alarmlar ve hatırlatıcılar\" izni verin.", fontSize = 14.sp)
                Button(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("İzni ver") }
            }
        }

        SectionCard("İpucu") {
            Text(
                "Bazı telefonlarda (Xiaomi, Huawei, Samsung vb.) bildirimlerin kaçmaması için uygulamanın pil " +
                    "optimizasyonunu \"Kısıtlama yok\" yapın.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
